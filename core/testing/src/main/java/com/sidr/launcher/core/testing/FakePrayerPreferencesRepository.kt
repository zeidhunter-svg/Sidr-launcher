package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.prayer.PrayerPreferencesRepository
import com.sidr.launcher.domain.prayer.PrayerSetup
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake [PrayerPreferencesRepository] (DS-6B Task 3). Backed by a [MutableStateFlow] so
 * reads observe writes; `null` initial state = never configured. Not wired into any Hilt graph —
 * use directly in unit tests.
 */
class FakePrayerPreferencesRepository(
    initial: PrayerSetup? = null,
) : PrayerPreferencesRepository {

    private val state = MutableStateFlow(initial)

    /** When non-null, write operations return [OperationResult.Failure] with this error. */
    var errorToReturn: OperationError? = null

    var writeCount: Int = 0
        private set

    override fun setup(): Flow<PrayerSetup?> = state.asStateFlow()

    override suspend fun saveSetup(setup: PrayerSetup): OperationResult<Unit> = write { setup }

    override suspend fun clearSetup(): OperationResult<Unit> = write { null }

    private fun write(newValue: () -> PrayerSetup?): OperationResult<Unit> {
        writeCount++
        val error = errorToReturn
        return if (error != null) {
            OperationResult.Failure(error)
        } else {
            state.value = newValue()
            OperationResult.Success(Unit)
        }
    }
}
