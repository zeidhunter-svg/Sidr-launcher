package com.sidr.launcher.domain.history

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface IntentMatchHistoryRepository {
    /** Emits records ordered by timestamp descending (most-recent first). */
    fun getMatchRecords(): Flow<List<IntentMatchRecord>>

    /**
     * Persists [record]. The caller (data layer) is responsible for redacting
     * [IntentMatchRecord.normalizedText] for [IntentMatchType.SEARCH] matches before calling
     * this method. Retention-pruning is applied by the implementation on every write.
     */
    suspend fun recordMatch(record: IntentMatchRecord): OperationResult<Unit>
}
