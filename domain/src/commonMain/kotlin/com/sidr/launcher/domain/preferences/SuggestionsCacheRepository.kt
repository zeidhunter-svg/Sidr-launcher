package com.sidr.launcher.domain.preferences

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface SuggestionsCacheRepository {
    fun getCachedSuggestions(): Flow<List<CachedSuggestion>>
    suspend fun updateCachedSuggestions(suggestions: List<CachedSuggestion>): OperationResult<Unit>
}
