package com.sidr.launcher.data.ailocal

import ai.onnxruntime.OrtEnvironment
import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sidr.launcher.data.ailocal.session.OnnxSessionFactory
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.device.DeviceCapability
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.intent.MatcherSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream

/**
 * Block P / P5 — device-pending acceptance test (SM-A325F), like Block J's
 * `SecretStoreInstrumentedTest`. It loads a *locally-present, verified* model (placeholder or real)
 * from androidTest assets, runs a real ONNX inference, exercises the NNAPI→CPU comparison, and
 * **records** the MID_RANGE CPU-path latency (`< 150ms` budget) as a log line, NOT a JVM assert.
 *
 * Drops the model + `vocab.txt` into `data/ai-local/src/androidTest/assets/nlu/`
 * (`intent.onnx`, `vocab.txt`) — generate the placeholder via `tools/nlu/make_placeholder_model.py`
 * or ship the real artifact. When the assets are absent the test **skips** (Assume) so the harness
 * compiles + passes in CI without the binary; the real device run is the pending acceptance item.
 */
@RunWith(AndroidJUnit4::class)
class OnnxIntentClassifierInstrumentedTest {

    private val modelId = ModelId("nlu-intent-v1")
    private lateinit var context: Context
    private lateinit var modelFile: File
    private lateinit var vocabFile: File

    private class FixedProfileProvider(private val cap: DeviceCapability) : DeviceProfileProvider {
        override fun profile(): DeviceProfile = DeviceProfile.MID_RANGE
        override fun capability(): DeviceCapability = cap
    }

    private class FixedAvailability(private val a: ModelAvailability) : ModelAvailabilityRepository {
        override fun availability(modelId: ModelId): Flow<ModelAvailability> = flowOf(a)
        override suspend fun markAvailable(modelId: ModelId) = com.sidr.launcher.domain.result.OperationResult.Success(Unit)
        override suspend fun markMissing(modelId: ModelId) = com.sidr.launcher.domain.result.OperationResult.Success(Unit)
    }

    private inner class AssetModelFiles : LocalModelFiles {
        override fun modelFile(modelId: ModelId): File? = modelFile.takeIf { it.exists() }
        override fun vocabStream(modelId: ModelId): InputStream? = vocabFile.takeIf { it.exists() }?.inputStream()
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        modelFile = copyAssetOrSkip("nlu/intent.onnx", "intent.onnx")
        vocabFile = copyAssetOrSkip("nlu/vocab.txt", "vocab.txt")
    }

    private fun copyAssetOrSkip(assetPath: String, outName: String): File {
        val out = File(context.cacheDir, outName)
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val present = try {
            assets.open(assetPath).use { input -> out.outputStream().use { input.copyTo(it) } }; true
        } catch (e: Exception) {
            false
        }
        assumeTrue("device-pending: $assetPath not bundled (see tools/nlu/README.md)", present)
        return out
    }

    private fun capability() = DeviceCapability(
        ramBytes = 4_000_000_000L, cpuCores = 4, nnapiAvailable = false, thermalOk = true, batteryOk = true,
    )

    private fun classifier(nnapi: Boolean) = OnnxIntentClassifier(
        deviceProfileProvider = FixedProfileProvider(capability()),
        modelAvailabilityRepository = FixedAvailability(ModelAvailability.Available),
        modelFiles = AssetModelFiles(),
        sessionFactory = OnnxSessionFactory(nnapiEnabled = nnapi, sdkInt = android.os.Build.VERSION.SDK_INT),
        modelId = modelId,
    )

    @Test
    fun cpu_path_classifies_and_returns_nlu_result() = runBlocking {
        val c = classifier(nnapi = false)
        val started = System.nanoTime()
        val result = c.match("fire up the camera")
        val elapsedMs = (System.nanoTime() - started) / 1_000_000.0
        assertNotNull(result)
        assertEquals(MatcherSource.NLU, result.source)
        // Device-pending acceptance: record, do not assert (< 150ms MID_RANGE CPU budget).
        Log.i("NluLatency", "CPU-path inference: ${"%.1f".format(elapsedMs)} ms (budget 150ms)")
        c.close()
    }

    @Test
    fun nnapi_path_falls_back_to_cpu_and_still_returns() = runBlocking {
        // Forces NNAPI on via the P1 seam to compare paths on one model; even if NNAPI init fails or
        // is degraded, the session falls back to CPU and inference still returns a result.
        val c = classifier(nnapi = true)
        val result = c.match("fire up the camera")
        assertEquals(MatcherSource.NLU, result.source)
        c.close()
    }

    @Test
    fun session_factory_builds_a_runnable_session_on_cpu() {
        val env = OrtEnvironment.getEnvironment()
        val created = OnnxSessionFactory(nnapiEnabled = false, sdkInt = 0).create(env, modelFile.readBytes())
        assertNotNull(created.session)
        assertEquals(false, created.nnapiActive)
        created.session.close()
    }
}
