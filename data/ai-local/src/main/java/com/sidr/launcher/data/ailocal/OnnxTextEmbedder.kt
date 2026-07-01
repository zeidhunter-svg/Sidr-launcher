package com.sidr.launcher.data.ailocal

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import com.sidr.launcher.data.ailocal.embedding.TextEmbeddingModelSpec
import com.sidr.launcher.data.ailocal.nlu.WordPieceTokenizer
import com.sidr.launcher.data.ailocal.session.OnnxSessionFactory
import com.sidr.launcher.data.ailocal.session.SessionLifecycle
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.ai.local.TextEmbedder
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.device.LocalInferenceGate
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Optional ONNX-backed [TextEmbedder] for Phase 7 Block V semantic suggestion re-rank.
 *
 * This mirrors [OnnxIntentClassifier]'s runtime shape: lazy single session, per-call
 * [LocalInferenceGate] check, file/vocab resolution before native ORT work, serialized runs, and
 * [SessionLifecycle] teardown under memory pressure. It is intentionally inert in production until
 * OQ#3 pins an embedding model URL/hash and ONNX contract; missing model/vocab/gate failures return
 * [OperationResult.Failure] without logging user text.
 */
class OnnxTextEmbedder(
    private val deviceProfileProvider: DeviceProfileProvider,
    private val modelAvailabilityRepository: ModelAvailabilityRepository,
    private val modelFiles: LocalModelFiles,
    private val sessionFactory: OnnxSessionFactory,
    private val modelId: ModelId,
    private val spec: TextEmbeddingModelSpec = TextEmbeddingModelSpec.DEFAULT,
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TextEmbedder, SessionLifecycle, AutoCloseable {

    private class Loaded(
        val env: OrtEnvironment,
        val session: OrtSession,
        val tokenizer: WordPieceTokenizer,
        val declaredInputs: Set<String>,
    )

    private val runMutex = Mutex()

    @Volatile
    private var loaded: Loaded? = null

    @Volatile
    private var pendingTeardown: Boolean = false

    override suspend fun embed(text: String): OperationResult<FloatArray> {
        if (!gateAllows()) {
            return OperationResult.Failure(OperationError.DeviceNotCapable("local_embedding_gate_off"))
        }

        return try {
            withContext(inferenceDispatcher) {
                runMutex.withLock {
                    try {
                        val session = ensureSession()
                        OperationResult.Success(infer(session, text))
                    } finally {
                        if (pendingTeardown) tearDown("trim_deferred")
                    }
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "Embedding inference failed (${t.javaClass.simpleName}); using heuristic rank.")
            OperationResult.Failure(OperationError.UnknownError(reason = "embedding_inference_failed"))
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
        Log.w(TAG, "Embedding gate evaluation failed (${t.javaClass.simpleName}); treating as gate-off.")
        false
    }

    private suspend fun currentAvailability(): ModelAvailability = try {
        modelAvailabilityRepository.availability(modelId).first()
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        ModelAvailability.Missing
    }

    /** Must be called under [runMutex]. Resolves model/vocab before touching native ORT. */
    private fun ensureSession(): OrtSession {
        loaded?.let { return it.session }

        val file = modelFiles.modelFile(modelId)
            ?: throw IllegalStateException("embedding_model_file_missing")
        val vocab = modelFiles.vocabStream(modelId)?.use { WordPieceTokenizer.loadVocab(it) }
            ?: throw IllegalStateException("embedding_vocab_missing")

        val bytes = file.readBytes()
        val env = OrtEnvironment.getEnvironment()
        val created = sessionFactory.create(env, bytes)
        val state = Loaded(
            env = env,
            session = created.session,
            tokenizer = WordPieceTokenizer(vocab),
            declaredInputs = created.session.inputNames,
        )
        loaded = state
        Log.i(TAG, "Embedding ONNX session loaded (nnapi=${created.nnapiActive}, inputs=${state.declaredInputs.size}).")
        return created.session
    }

    private fun infer(session: OrtSession, text: String): FloatArray {
        val state = loaded!!
        val enc = state.tokenizer.encode(text, spec.maxLen)
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

            session.run(inputs).use { result -> return readEmbedding(result) }
        } finally {
            idsTensor.close()
            maskTensor.close()
            typeTensor?.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readEmbedding(result: OrtSession.Result): FloatArray {
        val value = result.get(0).value
        return when (value) {
            is FloatArray -> value.copyOf()
            is Array<*> -> {
                val first = value.firstOrNull()
                    ?: throw IllegalStateException("embedding_output_empty")
                (first as FloatArray).copyOf()
            }
            else -> throw IllegalStateException("embedding_output_unsupported")
        }
    }

    override fun releaseResources() {
        if (runMutex.tryLock()) {
            try {
                tearDown("trim")
            } finally {
                runMutex.unlock()
            }
        } else {
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
                Log.w(TAG, "Error closing embedding ONNX session (${t.javaClass.simpleName}).")
            }
        }
        loaded = null
        pendingTeardown = false
        Log.i(TAG, "Embedding ONNX session released ($reason).")
    }

    private companion object {
        const val TAG = "OnnxTextEmbedder"
    }
}
