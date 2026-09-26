package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.suggestions.SuggestionScheduling

/**
 * In-memory fake for [SuggestionScheduling]. Records how many times [ensureScheduled] was called
 * so tests can assert the WorkManager re-sync fired (or didn't). Not wired into any Hilt graph.
 */
class FakeSuggestionScheduling : SuggestionScheduling {

    var ensureScheduledCount: Int = 0
        private set

    override suspend fun ensureScheduled() {
        ensureScheduledCount++
    }
}
