package com.sidr.launcher.domain.preferences

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface FeatureFlagRepository {
    fun getFlags(): Flow<FeatureFlags>
    suspend fun updateFlags(flags: FeatureFlags): OperationResult<Unit>
}
