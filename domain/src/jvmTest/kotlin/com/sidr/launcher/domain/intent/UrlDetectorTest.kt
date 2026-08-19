package com.sidr.launcher.domain.intent

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlDetectorTest {

    private fun classify(input: String) = UrlDetector.classify(input)

    // ── High-confidence, safe URLs → open ────────────────────────────────────

    @Test fun `bare domain with known TLD is a URL`() {
        assertEquals(UrlClassification.Url("https://github.com"), classify("github.com"))
    }

    @Test fun `www host with known TLD is a URL`() {
        assertEquals(UrlClassification.Url("https://www.youtube.com"), classify("www.youtube.com"))
    }

    @Test fun `simple path without query is a URL`() {
        assertEquals(UrlClassification.Url("https://github.com/anthropics"), classify("github.com/anthropics"))
    }

    @Test fun `explicit https scheme is preserved`() {
        assertEquals(UrlClassification.Url("https://example.com"), classify("https://example.com"))
    }

    @Test fun `explicit http scheme is preserved (not upgraded)`() {
        assertEquals(UrlClassification.Url("http://example.com"), classify("http://example.com"))
    }

    @Test fun `explicit scheme allows an uncommon TLD`() {
        // The user typed a full URL — honour it even though `.internal` isn't a curated TLD.
        assertEquals(UrlClassification.Url("https://box.internal"), classify("https://box.internal"))
    }

    @Test fun `multi-part ccTLD host opens`() {
        assertEquals(UrlClassification.Url("https://bbc.co.uk"), classify("bbc.co.uk"))
    }

    // ── Ambiguous / unsafe → web search fallback ─────────────────────────────

    @Test fun `unknown TLD falls back to search`() {
        assertEquals(UrlClassification.SearchFallback("example.foobar"), classify("example.foobar"))
    }

    @Test fun `code-ish token with alpha extension falls back to search not open`() {
        assertEquals(UrlClassification.SearchFallback("node.js"), classify("node.js"))
    }

    @Test fun `query string routes to search (case guard)`() {
        assertEquals(
            UrlClassification.SearchFallback("youtube.com/watch?v=abc12"),
            classify("youtube.com/watch?v=abc12"),
        )
    }

    @Test fun `explicit scheme with query routes to search`() {
        assertEquals(
            UrlClassification.SearchFallback("https://x.com/a?b=c"),
            classify("https://x.com/a?b=c"),
        )
    }

    @Test fun `punycode host is not opened silently`() {
        assertEquals(UrlClassification.SearchFallback("xn--80ak6aa92e.com"), classify("xn--80ak6aa92e.com"))
    }

    @Test fun `non-ascii host is not opened silently (homograph guard)`() {
        assertEquals(UrlClassification.SearchFallback("münchen.de"), classify("münchen.de"))
    }

    @Test fun `host label with leading hyphen is unsafe`() {
        assertEquals(UrlClassification.SearchFallback("-bad.com"), classify("-bad.com"))
    }

    // ── Not a URL at all → None (normal rule pipeline continues) ─────────────

    @Test fun `bare word without a dot is not a URL`() {
        assertEquals(UrlClassification.None, classify("telegram"))
    }

    @Test fun `numeric dotted token is not a domain`() {
        assertEquals(UrlClassification.None, classify("3.14"))
    }

    @Test fun `version-like token is not a domain`() {
        assertEquals(UrlClassification.None, classify("v1.2"))
    }

    @Test fun `multi-word input is never a URL`() {
        assertEquals(UrlClassification.None, classify("search cats online"))
    }

    @Test fun `empty input is None`() {
        assertEquals(UrlClassification.None, classify(""))
    }

    // ── Scheme allow-list: everything but http(s) is never opened ────────────

    @Test fun `intent scheme is never opened`() {
        assertEquals(UrlClassification.None, classify("intent://scan/#intent;end"))
    }

    @Test fun `javascript scheme is never opened`() {
        assertEquals(UrlClassification.None, classify("javascript:alert(1)"))
    }

    @Test fun `market scheme is never opened from user input`() {
        assertEquals(UrlClassification.None, classify("market://details?id=com.x"))
    }

    @Test fun `tel scheme is never opened`() {
        assertEquals(UrlClassification.None, classify("tel:12345"))
    }

    @Test fun `mailto scheme is never opened`() {
        assertEquals(UrlClassification.None, classify("mailto:a@b.com"))
    }

    @Test fun `ftp scheme is never opened`() {
        assertEquals(UrlClassification.None, classify("ftp://files.example.com"))
    }
}
