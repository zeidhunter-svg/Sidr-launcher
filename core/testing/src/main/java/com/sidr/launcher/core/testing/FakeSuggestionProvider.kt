package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionProvider

/**
 * JVM-only fake [SuggestionProvider] (Phase 7, Block S). Returns a scripted [suggestions] list and
 * records every [SuggestionContext] it was given. Default empty list models an opt-in provider whose
 * permission/signal is absent. Not wired into any Hilt graph; use directly in unit tests.
 */
class FakeSuggestionProvider(
    var suggestions: List<Suggestion> = emptyList(),
) : SuggestionProvider {

    val receivedContexts = mutableListOf<SuggestionContext>()

    override suspend fun provide(context: SuggestionContext): List<Suggestion> {
        receivedContexts += context
        return suggestions
    }
}
