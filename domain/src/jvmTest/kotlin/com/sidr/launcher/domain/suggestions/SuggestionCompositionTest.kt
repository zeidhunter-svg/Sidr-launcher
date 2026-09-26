package com.sidr.launcher.domain.suggestions

import com.sidr.launcher.core.testing.FakeSuggestionProvider
import com.sidr.launcher.domain.preferences.CachedSuggestion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Block-S **form-composition** checks (no engine impl — that is Block U; no reconciliation behaviour —
 * that is Block W2). These prove the ports/models compose as the plan requires:
 *  - aggregating providers → ranking → bound works with an opt-in provider returning empty (degrade
 *    with zero sensitive data);
 *  - a [Suggestion] is a strict superset of the display-safe `CachedSuggestion(label, actionId)`, so
 *    the existing `SuggestionsCacheRepository` caches engine output with **no new port** (Fork F7-8
 *    single-source). Supersede-not-merge first-paint behaviour is wired/tested in W2
 *    (architecture.md:254-256).
 */
class SuggestionCompositionTest {

    @Test
    fun `aggregate providers then rank - opt-in provider absent contributes nothing`() = runTest {
        val ctx = SuggestionContext(TimeOfDay.WORK, nowEpochMs = 0L)

        val usage = FakeSuggestionProvider(
            listOf(
                Suggestion("Camera", "com.cam", SuggestionSource.RECENT_USAGE, 9.0),
                Suggestion("Maps", "com.maps", SuggestionSource.FREQUENT_USAGE, 4.0),
            ),
        )
        // Opt-in calendar denied → provider returns empty (the default).
        val calendar = FakeSuggestionProvider(emptyList())

        // Aggregate exactly as the Block-U engine will: flatten enabled providers, then rank.
        val candidates = usage.provide(ctx) + calendar.provide(ctx)
        val ranked = HeuristicSuggestionRanker().rank(candidates, ctx)

        // Only the two usage candidates survive; calendar adds nothing; usage (9 * 1.0) > frequent (4 * 0.95).
        assertEquals(listOf("com.cam", "com.maps"), ranked.map { it.actionId })
        // Both providers were consulted with the same shared context.
        assertEquals(listOf(ctx), usage.receivedContexts)
        assertEquals(listOf(ctx), calendar.receivedContexts)
    }

    @Test
    fun `cache projection - Suggestion carries exactly the CachedSuggestion display fields, no new port`() {
        val suggestion = Suggestion(
            label = "Camera",
            actionId = "com.android.camera",
            source = SuggestionSource.RECENT_USAGE,
            score = 1.0,
        )

        // Construct the display cache element straight from the suggestion — no extra field needed.
        val cached = CachedSuggestion(label = suggestion.label, actionId = suggestion.actionId)

        assertEquals("Camera", cached.label)
        assertEquals("com.android.camera", cached.actionId)
    }
}
