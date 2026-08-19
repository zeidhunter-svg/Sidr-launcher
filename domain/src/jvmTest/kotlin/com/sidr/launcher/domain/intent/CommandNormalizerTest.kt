package com.sidr.launcher.domain.intent

import org.junit.Assert.assertEquals
import org.junit.Test

class CommandNormalizerTest {

    private fun norm(raw: String) = CommandNormalizer.normalize(raw)

    // --- table-driven cases ---

    @Test fun `trims leading and trailing spaces`() =
        assertEquals("open telegram", norm("  open telegram  "))

    @Test fun `collapses multiple internal spaces`() =
        assertEquals("open telegram", norm("Open   Telegram"))

    @Test fun `trims and collapses combined`() =
        assertEquals("open telegram", norm("  Open   Telegram "))

    @Test fun `lowercases ascii`() =
        assertEquals("hello world", norm("HELLO WORLD"))

    @Test fun `empty string stays empty`() =
        assertEquals("", norm(""))

    @Test fun `whitespace-only string becomes empty`() =
        assertEquals("", norm("   "))

    @Test fun `already normalized input is unchanged`() =
        assertEquals("open telegram", norm("open telegram"))

    @Test fun `single word is lowercased`() =
        assertEquals("settings", norm("Settings"))

    /**
     * Locale.ROOT stability: with Locale.ROOT, ASCII "I" → "i".
     * With Locale("tr") it would become "ı" (dotless i), breaking rule matching on Turkish devices.
     * This test documents and guards the stable-locale contract.
     */
    @Test fun `uppercase I lowercases to i with Locale ROOT`() =
        assertEquals("i", norm("I"))

    @Test fun `mixed case with numbers`() =
        assertEquals("open app2", norm("Open APP2"))
}
