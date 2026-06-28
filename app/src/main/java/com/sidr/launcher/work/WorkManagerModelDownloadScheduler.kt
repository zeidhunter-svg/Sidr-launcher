package com.sidr.launcher.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sidr.launcher.data.ailocal.provision.ModelDownloadScheduler
import com.sidr.launcher.domain.ai.local.ModelId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * WorkManager-backed [ModelDownloadScheduler] (Block Q, §5.F). Enqueues a single
 * [ModelDownloadWorker] as **unique work** with [ExistingWorkPolicy.KEEP] so a queued/running
 * download wins and re-calls are idempotent (no duplicate downloads).
 *
 * Constraints (context7-verified): requires a connected network, battery-not-low, and storage-not-low
 * — so a battery-saver / low-storage device defers (never aborts) the download. **Not** requires-
 * charging (a one-shot ~10–25 MB download should not wait for a charger). No foreground service.
 */
class WorkManagerModelDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ModelDownloadScheduler {

    override fun ensureScheduled(modelId: ModelId) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(ModelDownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(modelId: ModelId) {
        WorkManager.getInstance(context).cancelUniqueWork(ModelDownloadWorker.UNIQUE_NAME)
    }

    private companion object {
        const val BACKOFF_SECONDS = 30L
    }
}
