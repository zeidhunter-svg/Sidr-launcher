package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.history.IntentMatchHistoryRepository
import com.sidr.launcher.domain.history.IntentMatchRecord
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Configurable fake for unit-testing components that depend on [IntentMatchHistoryRepository].
 * Backed by a [MutableStateFlow] so [getMatchRecords] emissions are observable in tests.
 *
 * Note: this fake does NOT apply the redaction policy (that is a data-layer concern enforced by
 * [com.sidr.launcher.data.repository.db.mapper.IntentMatchMapper]). Tests that verify redaction
 * should use the real repository impl with an in-memory Room database.
 */
class FakeIntentMatchHistoryRepository : IntentMatchHistoryRepository {

    private val _records = MutableStateFlow<List<IntentMatchRecord>>(emptyList())

    /** All records passed to [recordMatch], in order (no redaction applied). */
    val recordedMatches = mutableListOf<IntentMatchRecord>()

    /** When non-null, [recordMatch] returns [OperationResult.Failure] with this error. */
    var errorToReturn: OperationError? = null

    override fun getMatchRecords(): Flow<List<IntentMatchRecord>> = _records

    override suspend fun recordMatch(record: IntentMatchRecord): OperationResult<Unit> {
        val error = errorToReturn
        if (error != null) return OperationResult.Failure(error)
        recordedMatches += record
        _records.value = _records.value + record
        return OperationResult.Success(Unit)
    }

    /** Pre-load records for tests that verify state without going through [recordMatch]. */
    fun setRecords(records: List<IntentMatchRecord>) {
        _records.value = records
    }

    fun reset() {
        _records.value = emptyList()
        recordedMatches.clear()
        errorToReturn = null
    }
}
