package com.sidr.launcher.domain.ai

import com.sidr.launcher.domain.ai.OutboundContextPolicy.AllowedContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Outbound-context guard for [PromptContextBuilder] (Block L / Fork P5-3, fail-closed).
 *
 * Proves the builder assembles only allow-listed context and that the privacy denylist is scanned
 * over **static, non-user-controlled text only** — never over the user's typed command (which is
 * legitimate free text and may contain any word). Reflection-free: hand-synced inventories +
 * literal denylist scans + constructed sentinels (Phase-4 `PrivacyInventoryGuardTest` pattern).
 */
class AiRequestGuardTest {

    @Test
    fun `allow-list holds exactly the three vetted categories`() {
        // Positive allow-list: adding a category is a deliberate, reviewed change. If a new context
        // source is wired in without being allow-listed here, this fails closed.
        assertEquals(
            setOf(
                AllowedContext.USER_COMMAND,
                AllowedContext.STATIC_SYSTEM_PROMPT,
                AllowedContext.GENERATION_LIMITS,
            ),
            OutboundContextPolicy.ALLOWED,
        )
    }

    @Test
    fun `no allow-listed category name is a forbidden context source`() {
        val violations = OutboundContextPolicy.ALLOWED.flatMap { category ->
            val name = category.name
            OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS
                .filter { term -> name.contains(term, ignoreCase = true) }
                .map { term -> name to term }
        }
        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { (name, term) ->
                "  allowed category \"$name\" contains forbidden context term \"$term\"" }
            fail("Allow-listed category names a forbidden context source:\n$detail")
        }
    }

    @Test
    fun `static system prompt contains no forbidden context term`() {
        // Denylist scanned over the static, builder-injected text ONLY (never user content).
        val prompt = PromptContextBuilder.DEFAULT_SYSTEM_PROMPT
        val hits = OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS
            .filter { term -> prompt.contains(term, ignoreCase = true) }
        if (hits.isNotEmpty()) {
            fail("DEFAULT_SYSTEM_PROMPT leaks forbidden context term(s): $hits")
        }
    }

    @Test
    fun `build adds no hidden context - exactly one verbatim USER message, static system, null model`() {
        val sentinel = "<<SENTINEL_CMD>>"
        val request = PromptContextBuilder().build(sentinel)

        // Exactly one message, USER role, content verbatim — no ASSISTANT or system-as-message smuggling.
        assertEquals(1, request.messages.size)
        assertEquals(AiRole.USER, request.messages.single().role)
        assertEquals(sentinel, request.messages.single().content)

        // Static system prompt, no pinned model, no extra outbound surface.
        assertEquals(PromptContextBuilder.DEFAULT_SYSTEM_PROMPT, request.system)
        assertNull(request.model)
        assertTrue(request.stopSequences.isEmpty())
        assertEquals(PromptContextBuilder.DEFAULT_MAX_OUTPUT_TOKENS, request.maxOutputTokens)
    }

    @Test
    fun `user content is passed through unfiltered - a denylist word in the command survives`() {
        // Regression guard against an over-eager filter that would corrupt legitimate commands:
        // the denylist must NEVER be applied to user content. "calendar" is a FORBIDDEN_CONTEXT_TERM
        // yet is perfectly legal inside a user's typed command.
        val command = "open my calendar"
        val request = PromptContextBuilder().build(command)
        assertEquals(command, request.messages.single().content)
        assertTrue(
            "user command must be preserved verbatim, denylist not applied to it",
            request.messages.single().content.contains("calendar"),
        )
    }
}
