package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.data.ailocal.ModelStore
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import kotlinx.coroutines.CancellationException

/**
 * The download → verify → atomic-rename → mark-available flow, extracted out of the WorkManager
 * shell so it is plain-JVM testable (no `CoroutineWorker`, no real network, no `Context`). The
 * `:app` `ModelDownloadWorker` is a thin shell that delegates to [provision] and maps the result to
 * a `Result.success/retry/failure`.
 *
 * Idempotent, and never exposes an unverified file (the [ModelStore] enforces verify-before-promote).
 * [CancellationException] is **not** caught — a stopped worker's cancellation propagates so the
 * download aborts cooperatively without flipping availability or promoting a partial file.
 */
class ModelProvisioner(
    private val store: ModelStore,
    private val downloader: ModelDownloader,
    private val availability: ModelAvailabilityRepository,
    private val config: ModelDownloadConfig,
) {
    suspend fun provision(): ProvisionResult {
        val modelId = config.modelId

        // Idempotent no-op: a verified model already on disk just (re)asserts availability.
        if (store.modelFile(modelId) != null) {
            availability.markAvailable(modelId)
            return ProvisionResult.AlreadyAvailable
        }

        // OQ#2-pending: nothing to download until a real artifact + hash are pinned.
        if (!config.isPinned) {
            return ProvisionResult.NotConfigured
        }

        val quarantine = store.quarantineFile(modelId)
        val download = try {
            downloader.download(config.url, quarantine)
        } catch (c: CancellationException) {
            throw c
        }
        if (download is com.sidr.launcher.domain.result.OperationResult.Failure) {
            // Transient (network/IO) — drop the partial file and let WorkManager retry with backoff.
            store.deleteQuarantine(modelId)
            return ProvisionResult.TransientFailure
        }

        // Verify + atomically promote. A hash mismatch is a permanent failure (re-downloading the
        // same pinned artifact will not fix a bad pin); availability stays not-Available.
        return when (store.promote(modelId, config.expectedSha256)) {
            is com.sidr.launcher.domain.result.OperationResult.Success -> {
                availability.markAvailable(modelId)
                ProvisionResult.Provisioned
            }
            is com.sidr.launcher.domain.result.OperationResult.Failure -> {
                availability.markMissing(modelId)
                ProvisionResult.VerificationFailed
            }
        }
    }
}

/**
 * Outcome of [ModelProvisioner.provision], mapped by the worker:
 *  - [Provisioned] / [AlreadyAvailable] → `Result.success`
 *  - [TransientFailure]                 → `Result.retry`
 *  - [VerificationFailed] / [NotConfigured] → `Result.failure` (no point retrying the same pin)
 */
enum class ProvisionResult {
    Provisioned,
    AlreadyAvailable,
    TransientFailure,
    VerificationFailed,
    NotConfigured,
}
