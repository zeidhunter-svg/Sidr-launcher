package com.sidr.launcher.domain.preferences

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface DeviceProfileCacheRepository {
    fun getCachedProfile(): Flow<DeviceProfileCacheEntry?>
    suspend fun updateCache(entry: DeviceProfileCacheEntry): OperationResult<Unit>
    suspend fun clearCache(): OperationResult<Unit>
}
