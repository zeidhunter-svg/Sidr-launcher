package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.data.ailocal.ModelStore
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelDownloader
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
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
 *
 * **Retry taxonomy (P2-7):** a download failure is split by [OperationError.NetworkError.retryable]
 * — transient (network/5xx/timeout) → [ProvisionResult.TransientFailure] (worker retries); permanent
 * (4xx/non-HTTPS) → [ProvisionResult.PermanentFailure] (worker fails). A SHA-256 mismatch is always
 * permanent ([ProvisionResult.VerificationFailed]).
 */
class ModelProvisioner(
    private val store: ModelStore,
    private val downloader: ModelDownloader,
    private val availability: ModelAvailabilityRepository,
    private val config: ModelDownloadConfig,
) {
    suspend fun provision(): ProvisionResult {
        val modelId = config.modelId

        // Idempotent no-op: a verified model already on disk just (re)asserts availability. This also
        // self-heals a crash between atomic-rename and markAvailable (file present, marker missing).
        if (store.modelFile(modelId) != null) {
            availability.markAvailable(modelId)
            return ProvisionResult.AlreadyAvailable
        }

        // OQ#2-pending: nothing to download until a real artifact + hash are pinned.
        if (!config.isPinned) {
            return ProvisionResult.NotConfigured
        }

        val quarantine = store.quarantineFile(modelId)
        val download = downloader.download(config.url, quarantine) // CancellationException propagates
        if (download is OperationResult.Failure) {
            store.deleteQuarantine(modelId)
            val error = download.error
            val transient = error is OperationError.NetworkError && error.retryable
            return if (transient) ProvisionResult.TransientFailure else ProvisionResult.PermanentFailure
        }

        // Verify + atomically promote. A hash mismatch is permanent — re-downloading the same pinned
        // artifact will not fix a bad pin; availability stays not-Available.
        return when (store.promote(modelId, config.expectedSha256)) {
            is OperationResult.Success -> {
                availability.markAvailable(modelId)
                ProvisionResult.Provisioned
            }
            is OperationResult.Failure -> {
                availability.markMissing(modelId)
                ProvisionResult.VerificationFailed
            }
        }
    }
}

/**
 * Outcome of [ModelProvisioner.provision], mapped by the worker:
 *  - [Provisioned] / [AlreadyAvailable] → `Result.success`
 *  - [TransientFailure]                 → `Result.retry` (network/5xx/timeout — backoff)
 *  - [PermanentFailure] / [VerificationFailed] / [NotConfigured] → `Result.failure` (no point retrying)
 */
enum class ProvisionResult {
    Provisioned,
    AlreadyAvailable,
    TransientFailure,
    PermanentFailure,
    VerificationFailed,
    NotConfigured,
}
