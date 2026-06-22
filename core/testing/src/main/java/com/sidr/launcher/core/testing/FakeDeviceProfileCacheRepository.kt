package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.preferences.DeviceProfileCacheEntry
import com.sidr.launcher.domain.preferences.DeviceProfileCacheRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake for [DeviceProfileCacheRepository]. Starts empty (null) like the real impl
 * before the first write. Not wired into any Hilt graph — use directly in unit tests.
 */
class FakeDeviceProfileCacheRepository(
    initial: DeviceProfileCacheEntry? = null,
) : DeviceProfileCacheRepository {

    private val state = MutableStateFlow(initial)

    /** When non-null, [updateCache] / [clearCache] return [OperationResult.Failure]. */
    var errorToReturn: OperationError? = null

    override fun getCachedProfile(): Flow<DeviceProfileCacheEntry?> = state.asStateFlow()

    override suspend fun updateCache(entry: DeviceProfileCacheEntry): OperationResult<Unit> {
        val error = errorToReturn
        return if (error != null) {
            OperationResult.Failure(error)
        } else {
            state.value = entry
            OperationResult.Success(Unit)
        }
    }

    override suspend fun clearCache(): OperationResult<Unit> {
        val error = errorToReturn
        return if (error != null) {
            OperationResult.Failure(error)
        } else {
            state.value = null
            OperationResult.Success(Unit)
        }
    }
}
