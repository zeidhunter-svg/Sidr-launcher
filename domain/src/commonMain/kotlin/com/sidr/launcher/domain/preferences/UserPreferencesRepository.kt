package com.sidr.launcher.domain.preferences

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    fun getPreferences(): Flow<UserPreferences>
    suspend fun updatePreferences(preferences: UserPreferences): OperationResult<Unit>
}
