package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.domain.ai.local.ModelId

/**
 * Enqueue seam (Block Q, §5.F) so [ModelManager.ensureModel]'s gate-before-enqueue logic is
 * JVM-testable without instantiating WorkManager. The WorkManager-backed impl
 * (`enqueueUniqueWork(name, KEEP, request)` with battery/storage-not-low constraints) lives in
 * `:app`; tests use a `FakeModelDownloadScheduler` that records calls.
 */
interface ModelDownloadScheduler {
    /** Idempotently schedules the one-shot download for [modelId] (KEEP — a queued/running job wins). */
    fun ensureScheduled(modelId: ModelId)

    /** Cancels any scheduled/running download for [modelId]. */
    fun cancel(modelId: ModelId)
}
