package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Configurable fake for unit-testing components that depend on [UsageHistoryRepository].
 * Backed by a [MutableStateFlow] so [getUsageRecords] emissions are observable in tests.
 */
class FakeUsageHistoryRepository : UsageHistoryRepository {

    private val _records = MutableStateFlow<List<AppUsageRecord>>(emptyList())

    /** All packageNames passed to [recordLaunch], in order. */
    val recordedLaunches = mutableListOf<String>()

    /** When non-null, [recordLaunch] returns [OperationResult.Failure] with this error. */
    var errorToReturn: OperationError? = null

    /** All cutoffs passed to [cleanupOlderThan], in order. */
    val cleanupCutoffs = mutableListOf<Long>()

    override fun getUsageRecords(): Flow<List<AppUsageRecord>> = _records

    override suspend fun recordLaunch(
        packageName: String,
        timestampEpochMs: Long,
    ): OperationResult<Unit> {
        val error = errorToReturn
        if (error != null) return OperationResult.Failure(error)
        recordedLaunches += packageName
        val existing = _records.value.find { it.packageName == packageName }
        _records.value = if (existing != null) {
            _records.value.map { record ->
                if (record.packageName == packageName)
                    record.copy(launchCount = record.launchCount + 1, lastUsedEpochMs = timestampEpochMs)
                else record
            }
        } else {
            _records.value + AppUsageRecord(packageName, timestampEpochMs, launchCount = 1)
        }
        return OperationResult.Success(Unit)
    }

    override suspend fun cleanupOlderThan(cutoffEpochMs: Long): OperationResult<Unit> {
        val error = errorToReturn
        if (error != null) return OperationResult.Failure(error)
        cleanupCutoffs += cutoffEpochMs
        _records.value = _records.value.filter { it.lastUsedEpochMs >= cutoffEpochMs }
        return OperationResult.Success(Unit)
    }

    /** Pre-load records for tests that verify sorting without going through [recordLaunch]. */
    fun setRecords(records: List<AppUsageRecord>) {
        _records.value = records
    }

    fun reset() {
        _records.value = emptyList()
        recordedLaunches.clear()
        cleanupCutoffs.clear()
        errorToReturn = null
    }
}
