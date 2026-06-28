package com.sidr.launcher.data.ailocal

import com.sidr.launcher.core.testing.FakeDeviceProfileProvider
import com.sidr.launcher.core.testing.FakeModelAvailabilityRepository
import com.sidr.launcher.data.ailocal.session.OnnxSessionFactory
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.device.DeviceCapability
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

/**
 * P4 / Fork P6-4 (moment 2): the per-inference gate re-check and graceful degrade are exercised on
 * the JVM **without a real ONNX session**. When the gate denies (LOW_END / thermal / battery /
 * availability) the classifier returns a lowest-confidence `UnknownIntent(source = NLU)` and never
 * touches the native runtime; when the gate allows but the model file is missing, it degrades the
 * same way (resolved before any `OrtEnvironment` call). ONNX cannot run on the JVM, so the
 * gate-ON happy path is covered only by the device-pending androidTest.
 */
class OnnxIntentClassifierGateTest {

    private val modelId = ModelId("nlu-intent-v1")

    private class CountingModelFiles(
        private val file: File? = null,
        private val vocab: (() -> InputStream)? = null,
    ) : LocalModelFiles {
        var modelFileCalls = 0
        override fun modelFile(modelId: ModelId): File? {
            modelFileCalls++
            return file
        }
        override fun vocabStream(modelId: ModelId): InputStream? = vocab?.invoke()
    }

    private fun classifier(
        provider: FakeDeviceProfileProvider,
        availability: ModelAvailability,
        files: LocalModelFiles = CountingModelFiles(),
    ): Pair<OnnxIntentClassifier, FakeModelAvailabilityRepository> {
        val repo = FakeModelAvailabilityRepository().apply { setAvailability(modelId, availability) }
        val c = OnnxIntentClassifier(
            deviceProfileProvider = provider,
            modelAvailabilityRepository = repo,
            modelFiles = files,
            sessionFactory = OnnxSessionFactory(nnapiEnabled = false, sdkInt = 0),
            modelId = modelId,
        )
        return c to repo
    }

    private fun assertDegraded(result: com.sidr.launcher.domain.intent.IntentMatchResult, reason: String) {
        assertTrue("intent should be UnknownIntent", result.best.intent is LauncherIntent.UnknownIntent)
        assertEquals(0f, result.best.confidence, 0f)
        assertEquals(MatcherSource.NLU, result.source)
        assertEquals(reason, result.debugReason)
    }

    @Test
    fun low_end_profile_degrades_without_touching_the_model() = runTest {
        val files = CountingModelFiles()
        val (c, _) = classifier(
            FakeDeviceProfileProvider(initialProfile = DeviceProfile.LOW_END),
            ModelAvailability.Available,
            files,
        )
        assertDegraded(c.match("fire up the camera"), "gate_off")
        assertEquals("session must never be created on LOW_END", 0, files.modelFileCalls)
    }

    @Test
    fun thermal_throttle_degrades_and_keeps_session_untouched() = runTest {
        val provider = FakeDeviceProfileProvider(
            initialProfile = DeviceProfile.MID_RANGE,
            initialCapability = DeviceCapability(4_000_000_000L, 4, false, thermalOk = false, batteryOk = true),
        )
        val files = CountingModelFiles()
        val (c, _) = classifier(provider, ModelAvailability.Available, files)
        assertDegraded(c.match("fire up the camera"), "gate_off")
        assertEquals(0, files.modelFileCalls)
    }

    @Test
    fun battery_saver_degrades() = runTest {
        val provider = FakeDeviceProfileProvider(
            initialProfile = DeviceProfile.MID_RANGE,
            initialCapability = DeviceCapability(4_000_000_000L, 4, false, thermalOk = true, batteryOk = false),
        )
        val (c, _) = classifier(provider, ModelAvailability.Available)
        assertDegraded(c.match("fire up the camera"), "gate_off")
    }

    @Test
    fun model_not_available_degrades() = runTest {
        val (c, _) = classifier(FakeDeviceProfileProvider(), ModelAvailability.Missing)
        assertDegraded(c.match("fire up the camera"), "gate_off")
        val (c2, _) = classifier(FakeDeviceProfileProvider(), ModelAvailability.Unverified)
        assertDegraded(c2.match("fire up the camera"), "gate_off")
    }

    @Test
    fun gate_on_but_missing_model_file_degrades_without_crashing() = runTest {
        // Gate allows (MID_RANGE, Available, cool, battery ok) but the file resolver returns null →
        // ensureSession throws before any OrtEnvironment call → graceful degrade.
        val files = CountingModelFiles(file = null)
        val (c, _) = classifier(FakeDeviceProfileProvider(), ModelAvailability.Available, files)
        assertDegraded(c.match("fire up the camera"), "inference_error")
        assertTrue("file resolver should have been consulted", files.modelFileCalls >= 1)
    }

    @Test
    fun release_resources_is_safe_when_no_session_is_open() {
        val (c, _) = classifier(FakeDeviceProfileProvider(), ModelAvailability.Available)
        c.releaseResources() // must not throw
        c.close() // must not throw
        assertFalse(false)
    }
}
