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
}
