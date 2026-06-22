package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.preferences.UserPreferences
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake for [UserPreferencesRepository]. Backed by a [MutableStateFlow] so reads
 * observe writes. Not wired into any Hilt graph — use directly in unit tests.
 */
class FakeUserPreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : UserPreferencesRepository {

    private val state = MutableStateFlow(initial)

    /** When non-null, [updatePreferences] returns [OperationResult.Failure] with this error. */
    var errorToReturn: OperationError? = null

    var updateCount: Int = 0
        private set

    override fun getPreferences(): Flow<UserPreferences> = state.asStateFlow()

    override suspend fun updatePreferences(preferences: UserPreferences): OperationResult<Unit> {
        updateCount++
        val error = errorToReturn
        return if (error != null) {
            OperationResult.Failure(error)
        } else {
            state.value = preferences
            OperationResult.Success(Unit)
        }
    }
}
