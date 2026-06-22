package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.preferences.CachedSuggestion
import com.sidr.launcher.domain.preferences.SuggestionsCacheRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake for [SuggestionsCacheRepository]. Backed by a [MutableStateFlow] so reads
 * observe writes. Not wired into any Hilt graph — use directly in unit tests.
 */
class FakeSuggestionsCacheRepository(
    initial: List<CachedSuggestion> = emptyList(),
) : SuggestionsCacheRepository {

    private val state = MutableStateFlow(initial)

    /** When non-null, [updateCachedSuggestions] returns [OperationResult.Failure]. */
    var errorToReturn: OperationError? = null

    override fun getCachedSuggestions(): Flow<List<CachedSuggestion>> = state.asStateFlow()

    override suspend fun updateCachedSuggestions(
        suggestions: List<CachedSuggestion>,
    ): OperationResult<Unit> {
        val error = errorToReturn
        return if (error != null) {
            OperationResult.Failure(error)
        } else {
            state.value = suggestions
            OperationResult.Success(Unit)
        }
    }
}
