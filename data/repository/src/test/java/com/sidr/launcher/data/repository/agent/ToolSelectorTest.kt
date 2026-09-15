package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolSelectorTest {

    @Test
    fun `an authored trigger wins outright over a third-party name that claims the same text`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.x/s"), "Settings", "system settings")),
        )
        assertEquals(Tier0ToolIds.OPEN_SYSTEM_SETTINGS, selector.select("system settings")?.id)
    }

    @Test
    fun `a dynamic candidate that does not name its app is declined`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "new message")),
        )
        assertNull(selector.select("new message"))
        assertEquals(ToolId("shortcut:com.a/new_chat"), selector.select("telegram new message")?.id)
    }

    @Test
    fun `two dynamic candidates of equal strength decline rather than guessing`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(
                DynamicToolName(ToolId("shortcut:com.a/new"), "Telegram", "new message"),
                DynamicToolName(ToolId("shortcut:com.b/new"), "Telegram", "new message"),
            ),
        )
        assertNull(selector.select("telegram new message"))
    }

    @Test
    fun `a partial shortcut name is not a match`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "new saved message")),
        )
        assertNull(selector.select("telegram new message"))
    }

    @Test
    fun `blank and empty text match nothing`() {
        val selector = ToolSelector(ToolVocabulary(), namesOf())
        assertNull(selector.select(""))
        assertNull(selector.select("   "))
    }

    @Test
    fun `a dynamic name with a blank qualifier or blank name can never match`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/x"), "", "")),
        )
        assertNull(selector.select("anything at all"))
    }

    /**
     * Fix round 1, IMPORTANT 2 — corrected in fix round 2, item 1. The original KDoc here called
     * `normalizedName.isBlank()` "the load-bearing half" of a guard that no longer exists: that was true
     * only of the token-set implementation this class started with. Fix round 1's IMPORTANT 3 replaced
     * that implementation with a contiguity check in the same round, and contiguity rejects a blank name
     * on its own, for every possible input — the guard disjunct has since been removed as dead code (see
     * `DynamicToolName.matches`'s KDoc). This test therefore pins the **behaviour** — a blank shortcut
     * name never selects, whatever the qualifier — not any particular line of the implementation.
     */
    @Test
    fun `a dynamic name with a blank name but a real qualifier can never match`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/x"), "Telegram", "")),
        )
        assertNull(selector.select("telegram"))
    }

    /**
     * Fix round 1, IMPORTANT 3 (adopted half). The shortcut's own name must appear as a contiguous,
     * word-bounded run — not merely as a set of tokens present somewhere in the command.
     */
    @Test
    fun `a shortcut name present as a contiguous run selects`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "new message")),
        )
        assertEquals(ToolId("shortcut:com.a/new_chat"), selector.select("telegram new message")?.id)
    }

    @Test
    fun `the same words in a different order do not match`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "new message")),
        )
        assertNull(selector.select("message new telegram"))
    }

    /**
     * Fix round 2, item 2. Contiguity requires the name to appear on **word boundaries**, not merely as
     * a substring — a plain `String.contains` would still find "new message" inside "renew messages"
     * (via "re**new** **messages**"), which is exactly the kind of false positive that fires an effect
     * for a reason no user could see. Mutation-verified: replacing the padded contiguity check with a raw
     * `normalizedText.contains(normalizedName)` turns this test red (see the fix-round-2 report).
     */
    @Test
    fun `the shortcut name must occur as a whole word run, not merely as a substring`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "new message")),
        )
        assertNull(selector.select("telegram renew messages"))
    }

    /**
     * Fix round 1, CRITICAL 1. `ToolVocabulary.match` returns `null` for two different reasons — nothing
     * of ours claimed the text, or two of our own entries claimed it and it refuses to guess — and only
     * the second must stop the selector outright rather than fall through to a dynamic name that would
     * otherwise break the tie. The dynamic candidate below would match if the selector fell through, so
     * this fails red without the [ToolVocabulary.isAmbiguous] check.
     */
    /**
     * Task 10b — the leftover-word rule. A sentence that merely *contains* the app token and the
     * shortcut's contiguous name run used to select regardless of what the surrounding words meant;
     * "отправь saved messages в telegram" ("send saved messages to telegram") is an intent to *send*,
     * not to open, yet both requirements the old code checked (contiguous name run, qualifier token
     * present) were satisfied. Every command token must now be accounted for by the shortcut's own name
     * or qualifier tokens — "отправь" and "в" are neither, so this must decline.
     */
    @Test
    fun `a command with unaccounted leftover words around a shortcut's name is declined`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/saved"), "Telegram", "Saved Messages")),
        )
        assertNull(selector.select("отправь saved messages в telegram"))
    }

    /**
     * Task 10b — the rule must not break the working case: a command that is exactly the app token
     * followed by the shortcut's own name (nothing left over) still selects.
     */
    @Test
    fun `a command consisting only of the app token and the shortcut name still selects`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "new message")),
        )
        assertEquals(ToolId("shortcut:com.a/new_chat"), selector.select("telegram new message")?.id)
    }

    /**
     * Task 10b — the subset direction is command-into-allowed, not the reverse: a two-token app label
     * need not be fully used by the command. "whatsapp new chat" never says "business", yet it must
     * still select, because every one of *its own* tokens ("whatsapp", "new", "chat") is covered by the
     * union of the qualifier's tokens ("whatsapp", "business") and the name's tokens ("new", "chat").
     */
    @Test
    fun `a two-token app label need not be fully used by the command`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.b/new_chat"), "WhatsApp Business", "New chat")),
        )
        assertEquals(ToolId("shortcut:com.b/new_chat"), selector.select("whatsapp new chat")?.id)
    }

    /**
     * Task 10b — rule 2 (a dynamic candidate must name its app) is unchanged by the new leftover-word
     * rule: a command that is exactly the shortcut's own name, with no qualifier token anywhere, still
     * declines. (Every command token is trivially accounted for by the name alone here — this pins that
     * the qualifier-presence check still fires independently.)
     */
    @Test
    fun `a command that is exactly the name with no qualifier token still declines`() {
        val selector = ToolSelector(
            vocabulary = ToolVocabulary(),
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "new message")),
        )
        assertNull(selector.select("new message"))
    }

    @Test
    fun `a text two authored entries both claim selects nothing even when a dynamic name would match`() {
        val colliding = ToolVocabulary(
            listOf(
                ToolVocabulary.Entry(
                    id = ToolId("alpha"),
                    prefixByLocale = mapOf("en" to setOf("do the thing")),
                ),
                ToolVocabulary.Entry(
                    id = ToolId("beta"),
                    prefixByLocale = mapOf("en" to setOf("do the thing")),
                ),
            ),
        )
        val selector = ToolSelector(
            vocabulary = colliding,
            dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/thing"), "Do", "the thing")),
        )

        assertNull(selector.select("do the thing"))
    }
}
