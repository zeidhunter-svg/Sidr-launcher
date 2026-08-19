package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface ResolutionPreferenceStore {
    suspend fun find(key: CapabilityKey, context: ResolutionContext): OperationResult<ResolutionPreference?>
    suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit>
    suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit>
    fun observeAll(): Flow<List<ResolutionPreference>>
}
