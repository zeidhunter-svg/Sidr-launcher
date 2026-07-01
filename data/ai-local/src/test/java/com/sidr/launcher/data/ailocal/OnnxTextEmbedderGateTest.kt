package com.sidr.launcher.data.ailocal

import com.sidr.launcher.core.testing.FakeDeviceProfileProvider
import com.sidr.launcher.core.testing.FakeModelAvailabilityRepository
import com.sidr.launcher.data.ailocal.session.OnnxSessionFactory
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStream

class OnnxTextEmbedderGateTest {

    private val modelId = ModelId("suggestion-embedding-v1")

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

    private fun embedder(
        provider: FakeDeviceProfileProvider,
        availability: ModelAvailability,
        files: LocalModelFiles = CountingModelFiles(),
    ): OnnxTextEmbedder {
        val repo = FakeModelAvailabilityRepository().apply { setAvailability(modelId, availability) }
        return OnnxTextEmbedder(
            deviceProfileProvider = provider,
            modelAvailabilityRepository = repo,
            modelFiles = files,
            sessionFactory = OnnxSessionFactory(nnapiEnabled = false, sdkInt = 0),
            modelId = modelId,
        )
    }

    @Test
    fun low_end_profile_fails_without_touching_model_files() = runTest {
        val files = CountingModelFiles()
        val embedder = embedder(
            FakeDeviceProfileProvider(initialProfile = DeviceProfile.LOW_END),
            ModelAvailability.Available,
            files,
        )

        assertTrue(embedder.embed("camera") is OperationResult.Failure)
        assertEquals(0, files.modelFileCalls)
    }

    @Test
    fun missing_availability_fails_without_touching_model_files() = runTest {
        val files = CountingModelFiles()
        val embedder = embedder(FakeDeviceProfileProvider(), ModelAvailability.Missing, files)

        assertTrue(embedder.embed("camera") is OperationResult.Failure)
        assertEquals(0, files.modelFileCalls)
    }

    @Test
    fun gate_on_but_missing_model_file_fails_gracefully() = runTest {
        val files = CountingModelFiles(file = null)
        val embedder = embedder(FakeDeviceProfileProvider(), ModelAvailability.Available, files)

        assertTrue(embedder.embed("camera") is OperationResult.Failure)
        assertTrue(files.modelFileCalls >= 1)
    }

    @Test
    fun release_resources_is_safe_when_no_session_is_open() {
        val embedder = embedder(FakeDeviceProfileProvider(), ModelAvailability.Available)

        embedder.releaseResources()
        embedder.close()
    }
}
