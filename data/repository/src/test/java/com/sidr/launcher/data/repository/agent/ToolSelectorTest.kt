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
     * Fix round 1, IMPORTANT 2. The blank-name-or-qualifier test above supplies both blank, so the
     * `qualifierTokens.isEmpty()` half of the guard short-circuits and the load-bearing half —
     * `normalizedName.isBlank()` — is never actually exercised. This is the case that bites: a qualifier
     * present but a blank shortcut name.
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
     * Fix round 1, CRITICAL 1. `ToolVocabulary.match` returns `null` for two different reasons — nothing
     * of ours claimed the text, or two of our own entries claimed it and it refuses to guess — and only
     * the second must stop the selector outright rather than fall through to a dynamic name that would
     * otherwise break the tie. The dynamic candidate below would match if the selector fell through, so
     * this fails red without the [ToolVocabulary.isAmbiguous] check.
     */
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
