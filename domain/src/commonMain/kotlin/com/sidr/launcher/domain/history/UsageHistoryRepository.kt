package com.sidr.launcher.domain.history

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface UsageHistoryRepository {
    /** Emits records ordered by recency/frequency (most-used first). */
    fun getUsageRecords(): Flow<List<AppUsageRecord>>

    /**
     * Upserts a launch event: increments [AppUsageRecord.launchCount] and updates
     * [AppUsageRecord.lastUsedEpochMs] for the given package. Retention-pruning is applied
     * by the implementation on every write.
     */
    suspend fun recordLaunch(packageName: String, timestampEpochMs: Long): OperationResult<Unit>

    /**
     * Best-effort time-based retention complement for background maintenance.
     *
     * Removes usage rows whose [AppUsageRecord.lastUsedEpochMs] is strictly older than
     * [cutoffEpochMs]. Row-count caps still apply on every write; this is the periodic WorkManager
     * sweep used by Phase 7 Block W's maintenance worker.
     */
    suspend fun cleanupOlderThan(cutoffEpochMs: Long): OperationResult<Unit>
}
