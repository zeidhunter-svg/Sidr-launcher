package com.sidr.launcher.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sidr.launcher.data.ailocal.provision.ModelProvisioner
import com.sidr.launcher.data.ailocal.provision.ProvisionResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * One-shot model-download worker (Block Q, Q3). A **thin shell**: all correctness-critical logic
 * (download → SHA-256 verify → atomic promote → mark-available, idempotency, cancellation) lives in
 * the JVM-tested [ModelProvisioner] in `:data:ai-local`. The worker only maps the outcome to a
 * WorkManager [Result] and inherits cooperative cancellation from [CoroutineWorker] (a stopped
 * worker cancels the `doWork` coroutine, which propagates into [ModelProvisioner.provision]).
 *
 * No foreground service: this is a deferrable background download gated by constraints.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val provisioner: ModelProvisioner,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (provisioner.provision()) {
        ProvisionResult.Provisioned, ProvisionResult.AlreadyAvailable -> Result.success()
        // Transient (network/IO) — WorkManager retries with the request's backoff policy.
        ProvisionResult.TransientFailure -> Result.retry()
        // Permanent — a bad/unpinned hash will not fix itself by retrying the same artifact.
        ProvisionResult.VerificationFailed, ProvisionResult.NotConfigured -> Result.failure()
    }

    companion object {
        const val UNIQUE_NAME = "sidr_model_download"
    }
}
