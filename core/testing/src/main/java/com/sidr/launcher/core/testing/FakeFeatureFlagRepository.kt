package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake for [FeatureFlagRepository]. Backed by a [MutableStateFlow] so reads
 * observe writes. Not wired into any Hilt graph — use directly in unit tests.
 */
class FakeFeatureFlagRepository(
    initial: FeatureFlags = FeatureFlags(),
) : FeatureFlagRepository {

    private val state = MutableStateFlow(initial)

    /** When non-null, [updateFlags] returns [OperationResult.Failure] with this error. */
    var errorToReturn: OperationError? = null

    var updateCount: Int = 0
        private set

    override fun getFlags(): Flow<FeatureFlags> = state.asStateFlow()

    override suspend fun updateFlags(flags: FeatureFlags): OperationResult<Unit> {
        updateCount++
        val error = errorToReturn
        return if (error != null) {
            OperationResult.Failure(error)
        } else {
            state.value = flags
            OperationResult.Success(Unit)
        }
    }
}
