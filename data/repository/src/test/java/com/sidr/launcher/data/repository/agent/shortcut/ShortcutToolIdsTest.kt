package com.sidr.launcher.data.repository.agent.shortcut

import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The two properties a derived id must have, held directly rather than only through the source:
 * it round-trips, and anything that cannot round-trip is refused rather than guessed at.
 */
class ShortcutToolIdsTest {

    @Test
    fun `an id round-trips through parse`() {
        val id = ShortcutToolIds.of("com.example.chat", "new_message")
        assertEquals(ToolId("shortcut:com.example.chat/new_message"), id)
        assertEquals("com.example.chat" to "new_message", ShortcutToolIds.parse(id))
    }

    /** A shortcut id may itself contain a slash; the FIRST one separates, so the rest is kept whole. */
    @Test
    fun `only the first slash separates, so a shortcut id may contain one`() {
        val id = ShortcutToolIds.of("com.example.chat", "chat/42")
        assertEquals("com.example.chat" to "chat/42", ShortcutToolIds.parse(id))
    }

    @Test
    fun `an authored id is not a shortcut id`() {
        assertNull(ShortcutToolIds.parse(ToolId("set_timer")))
        assertNull(ShortcutToolIds.parse(ToolId("launch_app")))
    }

    @Test
    fun `a malformed shortcut id is refused rather than half-read`() {
        assertNull(ShortcutToolIds.parse(ToolId("shortcut:")))
        assertNull(ShortcutToolIds.parse(ToolId("shortcut:com.example.chat")))
        assertNull(ShortcutToolIds.parse(ToolId("shortcut:com.example.chat/")))
        assertNull(ShortcutToolIds.parse(ToolId("shortcut:/new_message")))
    }
}
