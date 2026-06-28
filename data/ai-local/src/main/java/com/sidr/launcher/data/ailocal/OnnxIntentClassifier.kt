package com.sidr.launcher.data.ailocal

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.sidr.launcher.data.ailocal.nlu.IntentLabelMapper
import com.sidr.launcher.data.ailocal.nlu.OnnxModelSpec
import com.sidr.launcher.data.ailocal.nlu.WordPieceTokenizer
import com.sidr.launcher.data.ailocal.session.OnnxSessionFactory
import com.sidr.launcher.data.ailocal.session.SessionLifecycle
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.device.LocalInferenceGate
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.IntentMatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Local ONNX NLU classifier — the second [IntentMatcher] source (`MatcherSource.NLU`). The thin
 * shell around the runtime: it owns the lazy, single, shared [OrtSession]; the pure tokenization /
 * label-map / slot / confidence-escape logic lives in [WordPieceTokenizer] / [IntentLabelMapper]
 * (P2a, JVM-tested). There is **no** `IntentClassifier` port — Block O kept NLU on `IntentMatcher`.
 *
 * Lifecycle (Fork P6-9):
 *  - **Lazy** — the session is created on the first *gated* [match], never on launcher cold start.
 *  - **Single + shared** — one session; concurrent [match] calls are serialized by [runMutex].
 *  - **Inference off-Main** — native CPU-bound work runs on [inferenceDispatcher] (Default).
 *    `session.run` is NOT cooperatively cancellable; a cancelled collector aborts the *coroutine*
 *    but the in-flight native run still completes (input debounce upstream limits this).
 *  - **Teardown** — only on [releaseResources] (`onTrimMemory`, wired in `:app`) and on a
 *    **sustained** gate-off (debounced by [sustainedGateOffNanos]); a *transient* thermal/battery
 *    flicker only skips one inference and keeps the session. Re-inits lazily on the next gated run.
 *
 * Per-inference gate (Fork P6-4 moment 2): before any session work, re-reads fresh
 * `DeviceCapability` + model availability and calls the same [LocalInferenceGate]; if it denies
 * (thermal throttle / battery-saver / model not Available) it returns a lowest-confidence
 * `UnknownIntent(source = NLU)` so Block R's `LayeredIntentMatcher` falls back to the rule path.
 *
 * Graceful degrade (P4): ANY load/tokenize/inference failure → the same lowest-confidence result,
 * never an exception to the caller. No user text is logged — reasons only.
 *
 * Hilt-free (like `:data:ai-cloud`); constructed as a `@Singleton` by `:app` DI in Block R.
 */
class OnnxIntentClassifier(
    private val deviceProfileProvider: DeviceProfileProvider,
    private val modelAvailabilityRepository: ModelAvailabilityRepository,
    private val modelFiles: LocalModelFiles,
    private val sessionFactory: OnnxSessionFactory,
    private val modelId: ModelId,
    private val spec: OnnxModelSpec = OnnxModelSpec.DEFAULT,
    private val labelMapper: IntentLabelMapper = IntentLabelMapper(spec),
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val sustainedGateOffNanos: Long = DEFAULT_SUSTAINED_GATE_OFF_NANOS,
    private val now: () -> Long = { System.nanoTime() },
) : IntentMatcher, SessionLifecycle, AutoCloseable {

    private class Loaded(
        val env: OrtEnvironment,
        val session: OrtSession,
        val tokenizer: WordPieceTokenizer,
        val declaredInputs: Set<String>,
        val nnapiActive: Boolean,
    )

    private val runMutex = Mutex()

    @Volatile
    private var loaded: Loaded? = null

    @Volatile
    private var pendingTeardown = false

    @Volatile
    private var gateOffSinceNanos: Long = 0L

    override suspend fun match(normalizedInput: String): IntentMatchResult {
        // 1. Per-inference dynamic gate re-check (fresh capability — thermal/battery may have moved).
        if (!gateAllows()) {
            onGateOff()
            return labelMapper.escape(normalizedInput, "gate_off")
        }
        gateOffSinceNanos = 0L // gate on → reset the sustained-off debounce

        // 2. Inference on a background dispatcher, serialized to the single shared session.
        return try {
            withContext(inferenceDispatcher) {
                runMutex.withLock {
                    try {
                        val session = ensureSession()
                        infer(session, normalizedInput)
                    } finally {
                        if (pendingTeardown) tearDown("trim_deferred")
                    }
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            // P4: any load/tokenize/run failure degrades to the rule path. No user text logged.
            Log.w(TAG, "NLU inference failed (${t.javaClass.simpleName}); degrading to rule path.")
            labelMapper.escape(normalizedInput, "inference_error")
        }
    }

    private suspend fun gateAllows(): Boolean = try {
        val profile = deviceProfileProvider.profile()
        val capability = deviceProfileProvider.capability()
        val availability = currentAvailability()
        LocalInferenceGate.allowsLocalNlu(profile, capability, availability)
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Log.w(TAG, "Gate evaluation failed (${t.javaClass.simpleName}); treating as gate-off.")
        false
    }

    private suspend fun currentAvailability(): ModelAvailability = try {
        modelAvailabilityRepository.availability(modelId).first()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        ModelAvailability.Missing
    }

    /** Lazy single-session init. Resolves files BEFORE touching native ORT so a missing model/vocab
     *  degrades without loading the runtime. Must be called under [runMutex]. */
    private fun ensureSession(): OrtSession {
        loaded?.let { return it.session }

        val file = modelFiles.modelFile(modelId)
            ?: throw IllegalStateException("model_file_missing")
        val vocab = modelFiles.vocabStream(modelId)?.use { WordPieceTokenizer.loadVocab(it) }
            ?: throw IllegalStateException("vocab_missing")

        val bytes = file.readBytes()
        val env = OrtEnvironment.getEnvironment()
        val created = sessionFactory.create(env, bytes)
        val declaredInputs = created.session.inputNames
        loaded = Loaded(env, created.session, WordPieceTokenizer(vocab), declaredInputs, created.nnapiActive)
        Log.i(TAG, "ONNX session loaded (nnapi=${created.nnapiActive}, inputs=${declaredInputs.size}).")
        return created.session
    }

    private fun infer(session: OrtSession, normalizedInput: String): IntentMatchResult {
        val state = loaded!!
        val enc = state.tokenizer.encode(normalizedInput, spec.maxLen)
        val env = state.env

        val idsTensor = OnnxTensor.createTensor(env, arrayOf(enc.inputIds))
        val maskTensor = OnnxTensor.createTensor(env, arrayOf(enc.attentionMask))
        val typeTensor =
            if (state.declaredInputs.contains(spec.tokenTypeIdsName)) {
                OnnxTensor.createTensor(env, arrayOf(enc.tokenTypeIds))
            } else {
                null
            }
        try {
            val inputs = LinkedHashMap<String, OnnxTensor>()
            inputs[spec.inputIdsName] = idsTensor
            inputs[spec.attentionMaskName] = maskTensor
            if (typeTensor != null) inputs[spec.tokenTypeIdsName] = typeTensor

            session.run(inputs).use { result ->
                val logits = readLogits(result)
                return labelMapper.map(normalizedInput, logits)
            }
        } finally {
            idsTensor.close()
            maskTensor.close()
            typeTensor?.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readLogits(result: OrtSession.Result): FloatArray {
        // Output is logits [1, numLabels] float → float[][]; read row 0 by index (name-independent).
        val value = result.get(0).value
        val rows = value as Array<FloatArray>
        return rows[0]
    }

    // ---- lifecycle / teardown (P3) ----

    private fun onGateOff() {
        val t = now()
        if (gateOffSinceNanos == 0L) {
            gateOffSinceNanos = t
            return
        }
        // Sustained gate-off (debounced): tear down an idle session to free native memory.
        if (loaded != null && t - gateOffSinceNanos >= sustainedGateOffNanos) {
            if (runMutex.tryLock()) {
                try {
                    tearDown("sustained_gate_off")
                } finally {
                    runMutex.unlock()
                }
            }
        }
    }

    /** Fork P6-9 / P3: release native memory under pressure. Wired from `:app` `onTrimMemory`. */
    override fun releaseResources() {
        if (runMutex.tryLock()) {
            try {
                tearDown("trim")
            } finally {
                runMutex.unlock()
            }
        } else {
            // A run holds the mutex; it will tear down in its finally block.
            pendingTeardown = true
        }
    }

    override fun close() = releaseResources()

    /** Closes the session + frees it. Caller must hold [runMutex] (or hold no run). */
    private fun tearDown(reason: String) {
        loaded?.let {
            try {
                it.session.close()
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing ONNX session (${t.javaClass.simpleName}).")
            }
        }
        loaded = null
        pendingTeardown = false
        gateOffSinceNanos = 0L
        Log.i(TAG, "ONNX session released ($reason).")
    }

    companion object {
        private const val TAG = "OnnxIntentClassifier"
        private const val DEFAULT_SUSTAINED_GATE_OFF_NANOS = 30_000_000_000L // 30s
    }
}
