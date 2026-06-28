package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.awaitCancellation
import java.io.File

/**
 * Test fakes for the `:data:ai-local`-internal provisioning ports. These ports are NOT domain ports,
 * and `:core:testing` is a pure `kotlin.jvm` module that cannot depend on this Android library — so
 * the fakes live in this module's test source set (the reused domain fakes
 * `FakeDeviceProfileProvider` / `FakeModelAvailabilityRepository` stay in `:core:testing`).
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
