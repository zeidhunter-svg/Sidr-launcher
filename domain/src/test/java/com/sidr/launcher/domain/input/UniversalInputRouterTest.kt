package com.sidr.launcher.domain.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UniversalInputRouterTest {

    @Test
    fun `blank buffer is Empty`() {
        assertEquals(InputIntent.Empty, UniversalInputRouter.classify("   "))
    }

    @Test
    fun `dev sentinel is recognized case-insensitively and trimmed`() {
        assertEquals(InputIntent.DevSentinel, UniversalInputRouter.classify("  //DEV-mode "))
    }

    @Test
    fun `plain text is a Query with no site url`() {
        val intent = UniversalInputRouter.classify("telegram")
        assertEquals(InputIntent.Query(raw = "telegram", siteUrl = null), intent)
    }

    @Test
    fun `safe url yields a Query carrying the openable site url`() {
        val intent = UniversalInputRouter.classify("github.com") as InputIntent.Query
        assertEquals("github.com", intent.raw)
        assertEquals("https://github.com", intent.siteUrl)
    }

    @Test
    fun `unsafe or unknown-tld domain yields a Query with no site url`() {
        // UrlDetector routes punycode / unknown TLD to SearchFallback → no site chip.
        val intent = UniversalInputRouter.classify("xn--nxasmq6b.com") as InputIntent.Query
        assertNull(intent.siteUrl)
    }

    @Test
    fun `raw is the trimmed original, not the lowercased url token`() {
        val intent = UniversalInputRouter.classify("  Hello World  ") as InputIntent.Query
        assertEquals("Hello World", intent.raw)
        assertNull(intent.siteUrl)
    }
}
