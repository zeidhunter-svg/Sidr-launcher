package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.history.SuggestionRankingRecord
import com.sidr.launcher.domain.history.SuggestionRankingRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Configurable fake for unit-testing components that depend on [SuggestionRankingRepository].
 * Backed by a [MutableStateFlow] so [getRankingRecords] emissions are observable in tests.
 */
class FakeSuggestionRankingRepository : SuggestionRankingRepository {

    private val _records = MutableStateFlow<List<SuggestionRankingRecord>>(emptyList())

    /** All records passed to [upsertRanking], in order. */
    val recordedUpserts = mutableListOf<SuggestionRankingRecord>()

    /** When non-null, [upsertRanking] returns [OperationResult.Failure] with this error. */
    var errorToReturn: OperationError? = null

    override fun getRankingRecords(): Flow<List<SuggestionRankingRecord>> = _records

    override suspend fun upsertRanking(record: SuggestionRankingRecord): OperationResult<Unit> {
        val error = errorToReturn
        if (error != null) return OperationResult.Failure(error)
        recordedUpserts += record
        val existing = _records.value.find { it.actionId == record.actionId }
        _records.value = if (existing != null) {
            _records.value.map { if (it.actionId == record.actionId) record else it }
        } else {
            _records.value + record
        }
        return OperationResult.Success(Unit)
    }

    /** Pre-load records for tests that verify state without going through [upsertRanking]. */
    fun setRecords(records: List<SuggestionRankingRecord>) {
        _records.value = records
    }

    fun reset() {
        _records.value = emptyList()
        recordedUpserts.clear()
        errorToReturn = null
    }
}
