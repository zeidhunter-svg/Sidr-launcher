package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.core.testing.FakeModelAvailabilityRepository
import com.sidr.launcher.data.ailocal.ModelStore
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.result.OperationError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ModelProvisionerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val modelId = ModelId("intent-nlu-v1")
    private val bytes = "model-bytes".toByteArray()
    private val hash = Sha256Verifier().sha256Hex(
        File.createTempFile("hash", ".bin").apply { writeBytes(bytes) }
    )

    private fun store() = ModelStore(tmp.root, { ByteArrayInputStream("vocab".toByteArray()) })

    private fun config(url: String = "https://example.test/model.onnx", sha: String = hash) =
        ModelDownloadConfig(modelId, url, sha)

    @Test
    fun `happy path downloads, verifies, promotes, and flips availability to Available`() = runTest {
        val store = store()
        val avail = FakeModelAvailabilityRepository()
        val provisioner = ModelProvisioner(store, FakeModelDownloader(bytes), avail, config())

        assertEquals(ProvisionResult.Provisioned, provisioner.provision())
        assertEquals(ModelAvailability.Available, avail.availability(modelId).first())
        assertTrue(store.modelFile(modelId) != null)
    }

    @Test
    fun `idempotent no-op when a verified model is already present`() = runTest {
        val store = store()
        store.readyFile(modelId).apply { parentFile?.mkdirs(); writeBytes(bytes) }
        val avail = FakeModelAvailabilityRepository()
        val downloader = FakeModelDownloader(bytes)

        assertEquals(ProvisionResult.AlreadyAvailable, ModelProvisioner(store, downloader, avail, config()).provision())
        assertEquals(0, downloader.downloadCalls)
        assertEquals(ModelAvailability.Available, avail.availability(modelId).first())
    }

    @Test
    fun `unpinned config (OQ#2 open) is inert - no download`() = runTest {
        val downloader = FakeModelDownloader(bytes)
        val provisioner = ModelProvisioner(store(), downloader, FakeModelAvailabilityRepository(), ModelDownloadConfig.INTENT_NLU_PENDING)

        assertEquals(ProvisionResult.NotConfigured, provisioner.provision())
        assertEquals(0, downloader.downloadCalls)
    }

    @Test
    fun `transient download failure leaves no ready file and availability not Available`() = runTest {
        val store = store()
        val avail = FakeModelAvailabilityRepository()
        val downloader = FakeModelDownloader(bytes).apply { failWith = OperationError.NetworkError() }

        assertEquals(ProvisionResult.TransientFailure, ModelProvisioner(store, downloader, avail, config()).provision())
        assertNull(store.modelFile(modelId))
        // Transient failure leaves availability untouched (a WorkManager retry will try again); the
        // fake's default is Unverified and the gate treats Unverified == not-Available.
        assertEquals(ModelAvailability.Unverified, avail.availability(modelId).first())
        assertTrue(avail.markAvailableCalls.isEmpty())
    }

    @Test
    fun `hash mismatch is permanent - marks missing and never exposes the file`() = runTest {
        val store = store()
        val avail = FakeModelAvailabilityRepository()
        val provisioner = ModelProvisioner(store, FakeModelDownloader(bytes), avail, config(sha = "0".repeat(64)))

        assertEquals(ProvisionResult.VerificationFailed, provisioner.provision())
        assertNull(store.modelFile(modelId))
        assertEquals(ModelAvailability.Missing, avail.availability(modelId).first())
        assertTrue(modelId in avail.markMissingCalls)
    }

    @Test
    fun `cooperative cancellation aborts mid-download with no ready file`() = runTest {
        val store = store()
        val avail = FakeModelAvailabilityRepository()
        val downloader = FakeModelDownloader(bytes).apply { suspendUntilCancelled = true }
        val provisioner = ModelProvisioner(store, downloader, avail, config())

        val job = launch { provisioner.provision() }
        runCurrent() // let the launched coroutine reach the suspended download
        assertEquals(1, downloader.downloadCalls)

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertNull(store.modelFile(modelId))
        assertTrue("availability must not flip on a cancelled download", avail.markAvailableCalls.isEmpty())
    }
}
