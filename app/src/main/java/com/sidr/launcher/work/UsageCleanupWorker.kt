package com.sidr.launcher.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.result.OperationResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic maintenance sweep for stale usage-history rows.
 *
 * Row-count caps are already enforced on every write; this worker is the time-based complement so
 * cold devices do not accumulate very old usage rows forever. Best-effort only — the next periodic
 * run will try again.
 */
@HiltWorker
class UsageCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val usageHistoryRepository: UsageHistoryRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cutoffEpochMs = System.currentTimeMillis() - USAGE_RETENTION_MS
        return when (usageHistoryRepository.cleanupOlderThan(cutoffEpochMs)) {
            is OperationResult.Success -> Result.success()
            is OperationResult.Failure -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "sidr_usage_cleanup"
        private val USAGE_RETENTION_MS = TimeUnit.DAYS.toMillis(90)
    }
}
