package com.sidr.launcher.domain.memory.alias

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasTest {
    @Test
    fun `appPackageOrNull returns package for app target`() {
        val target: AliasTarget = AliasTarget.App("com.telegram")
        assertEquals("com.telegram", target.appPackageOrNull())
    }

    @Test
    fun `max phrase length is 64`() {
        assertEquals(64, MAX_ALIAS_PHRASE_LENGTH)
    }

    @Test
    fun `alias holds phrase target and timestamp`() {
        val alias = Alias(
            phrase = "work chat",
            target = AliasTarget.App("com.telegram"),
            createdAtEpochMs = 5L,
        )

        assertEquals("work chat", alias.phrase)
        assertEquals(AliasTarget.App("com.telegram"), alias.target)
        assertEquals(5L, alias.createdAtEpochMs)
        assertNull((alias.target as AliasTarget.App).packageName.let { if (it.isEmpty()) it else null })
    }
}
