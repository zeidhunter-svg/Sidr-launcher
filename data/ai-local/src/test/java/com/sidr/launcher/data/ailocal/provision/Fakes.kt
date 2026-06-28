package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.domain.ai.local.ModelDownloader
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.awaitCancellation
import java.io.File

/**
 * Test fakes for the provisioning collaborators, kept in this module's **test source set** (not a
 * published module — P2-8). [FakeModelDownloadScheduler] fakes a `:data:ai-local`-internal port, so it
 * must live here. [FakeModelDownloader] fakes the [ModelDownloader] **domain** port (relocated in the
 * P2-4 rework), so it is eligible for promotion to `:core:testing` — **defer that to Block R**, only
 * if R's tests need it; today the sole consumer is this module's `ModelProvisionerTest`. The reused
 * domain fakes `FakeDeviceProfileProvider` / `FakeModelAvailabilityRepository` stay in `:core:testing`.
 */
class FakeModelDownloader(
    /** Bytes written to the destination on a successful download; null + no [failWith] → success-empty. */
    private val bytes: ByteArray? = null,
) : ModelDownloader {

    var failWith: OperationError? = null

    /** When true, [download] suspends until cancelled (to exercise cooperative cancellation). */
    var suspendUntilCancelled: Boolean = false

    var downloadCalls = 0
        private set

    override suspend fun download(url: String, destination: File): OperationResult<Unit> {
        downloadCalls++
        if (suspendUntilCancelled) awaitCancellation()
        failWith?.let { return OperationResult.Failure(it) }
        bytes?.let { destination.outputStream().use { out -> out.write(it) } }
        return OperationResult.Success(Unit)
    }
}

class FakeModelDownloadScheduler : ModelDownloadScheduler {
    val scheduled = mutableListOf<ModelId>()
    val cancelled = mutableListOf<ModelId>()

    override fun ensureScheduled(modelId: ModelId) {
        scheduled += modelId
    }

    override fun cancel(modelId: ModelId) {
        cancelled += modelId
    }
}
