package com.sidr.launcher.work

import com.sidr.launcher.domain.suggestions.SuggestionScheduling
import javax.inject.Inject

/**
 * `:app`-side implementation of the [SuggestionScheduling] domain port (Block X5, Fork X5-A).
 *
 * Thin delegate to the existing [SuggestionsWorkScheduler] — keeps the WorkManager contract
 * (gate-before-enqueue) in one place while letting `:feature:settings` re-sync via the domain port.
 */
class SuggestionSchedulingImpl @Inject constructor(
    private val scheduler: SuggestionsWorkScheduler,
) : SuggestionScheduling {

    override suspend fun ensureScheduled() = scheduler.ensureScheduled()
}
