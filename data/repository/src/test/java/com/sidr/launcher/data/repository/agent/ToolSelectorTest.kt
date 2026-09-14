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
}
