package com.sidr.launcher.domain.history

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface SuggestionRankingRepository {
    /** Emits records ordered by score descending. */
    fun getRankingRecords(): Flow<List<SuggestionRankingRecord>>

    suspend fun upsertRanking(record: SuggestionRankingRecord): OperationResult<Unit>
}
