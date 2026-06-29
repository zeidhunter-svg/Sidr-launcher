package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * JVM-only fake [SuggestionEngine] (Phase 7, Block S), backed by a [MutableStateFlow] so [suggestions]
 * and [refresh] stay consistent. [emit] pushes a new value to the stream (e.g. simulating a cache
 * repaint or a fresh load); [refresh] returns [refreshResult] and, on success, publishes it to the
 * stream. [refreshCount] records calls. Not wired into any Hilt graph; use directly in unit tests.
 */
class FakeSuggestionEngine : SuggestionEngine {

    private val state = MutableStateFlow<List<Suggestion>>(emptyList())

    var refreshResult: OperationResult<List<Suggestion>> = OperationResult.Success(emptyList())

    var refreshCount: Int = 0
        private set

    override fun suggestions(): Flow<List<Suggestion>> = state.asStateFlow()

    override suspend fun refresh(): OperationResult<List<Suggestion>> {
        refreshCount++
        (refreshResult as? OperationResult.Success)?.let { state.value = it.value }
        return refreshResult
    }

    /** Push a new value to the [suggestions] stream without going through [refresh]. */
    fun emit(values: List<Suggestion>) {
        state.value = values
    }
}
