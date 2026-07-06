package com.sidr.launcher.domain.ai

import com.sidr.launcher.domain.ai.OutboundContextPolicy.AllowedContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Block U5 — proves the suggestion engine (Block U) added no outbound surface. [AiRequestGuardTest]
 * pins [OutboundContextPolicy.ALLOWED] to exactly the vetted categories and proves
 * [PromptContextBuilder] assembles nothing else; this test is the Block-U-scoped regression on top of
 * that — it fails closed if a suggestion/calendar/location field is ever folded into the outbound
 * surface instead of staying inside [com.sidr.launcher.domain.suggestions.Suggestion]/`CachedSuggestion`.
 *
 * AIL-4 deliberately added exactly one category — [AllowedContext.ACTION_CATALOG_SCHEMA] (the static
 * router tool schema, guard-tested in `RouterOutboundGuardTest`). This test still fails closed if a
 * *suggestion/calendar/location* category were ever added.
 */
class SuggestionOutboundIsolationTest {

    @Test
    fun `allow-list adds only the AIL-4 schema category, never a suggestion-context category`() {
        assertEquals(
            setOf(
                AllowedContext.USER_COMMAND,
                AllowedContext.STATIC_SYSTEM_PROMPT,
                AllowedContext.GENERATION_LIMITS,
                AllowedContext.ACTION_CATALOG_SCHEMA,
            ),
            OutboundContextPolicy.ALLOWED,
        )
        val forbiddenCategoryTerms = listOf("suggestion", "calendar", "location", "usage")
        val violations = OutboundContextPolicy.ALLOWED.flatMap { category ->
            forbiddenCategoryTerms.filter { term -> category.name.contains(term, ignoreCase = true) }
        }
        assertFalse(
            "No suggestion/calendar/location/usage context category may be allow-listed: $violations",
            violations.isNotEmpty(),
        )
    }

    @Test
    fun `outbound field inventories are unchanged by Block U and carry no suggestion-context term`() {
        // Pinned to the exact pre-Block-U literal sets (Block L) — growth here would mean Block U wired
        // a new field into AiRequest/AiError, which it must never do (suggestions ride their own port).
        assertEquals(
            setOf("messages", "system", "maxOutputTokens", "model", "stopSequences"),
            OutboundContextPolicy.OUTBOUND_FIELD_NAMES,
        )
        assertEquals(
            setOf("retryAfterMs", "detail", "statusCode"),
            OutboundContextPolicy.AIERROR_FIELD_NAMES,
        )

        val suggestionTerms = listOf("suggestion", "calendar", "location")
        val allFieldNames = OutboundContextPolicy.OUTBOUND_FIELD_NAMES + OutboundContextPolicy.AIERROR_FIELD_NAMES
        val violations = allFieldNames.flatMap { field ->
            suggestionTerms.filter { term -> field.contains(term, ignoreCase = true) }
        }
        assertFalse(
            "Block U must never introduce a suggestion/calendar/location-named outbound field: $violations",
            violations.isNotEmpty(),
        )
    }
}
