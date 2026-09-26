package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.suggestions.ResolvedSuggestionAction
import com.sidr.launcher.domain.suggestions.SuggestionActionAnchor
import com.sidr.launcher.domain.suggestions.SuggestionActionTargetResolver

class FakeSuggestionActionTargetResolver(
    private val defaultSupported: Boolean = true,
) : SuggestionActionTargetResolver {

    private val resolvedAnchors = mutableMapOf<SuggestionActionAnchor, ResolvedSuggestionAction>()
    private val supportedActions = mutableSetOf<String>()
    private val unsupportedActions = mutableSetOf<String>()

    override suspend fun resolve(anchor: SuggestionActionAnchor): ResolvedSuggestionAction? =
        resolvedAnchors[anchor]

    override suspend fun isSupportedAction(actionId: String): Boolean =
        when (actionId) {
            in unsupportedActions -> false
            in supportedActions -> true
            else -> defaultSupported
        }

    fun resolveAnchor(
        anchor: SuggestionActionAnchor,
        label: String,
        actionId: String,
    ) {
        resolvedAnchors[anchor] = ResolvedSuggestionAction(label = label, actionId = actionId)
        supportedActions += actionId
    }

    fun support(actionId: String) {
        supportedActions += actionId
        unsupportedActions -= actionId
    }

    fun reject(actionId: String) {
        unsupportedActions += actionId
        supportedActions -= actionId
    }
}
