package com.sidr.launcher.domain.sort

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The URL sort (A4′ phase 3b, fork 3b-C, B-5; Review Focus 5). Unlike duration and clock time it
 * NORMALISES: Android's intent-filter matching compares the scheme case-sensitively, so `HTTPS://…`
 * would reach no browser, and a space is not part of an address. Everything after the scheme is kept
 * byte for byte — the host's case included — because only the scheme is case-insensitive by RFC 3986.
 * Expected values are compared by equality (R14-43).
 */
class UrlSortTest {

    @Test
    fun `an upper-case scheme is lowered and the rest kept byte for byte`() = runTest {
        assertEquals("https://Quillmere.example/Path", UrlSort.normalized("HTTPS://Quillmere.example/Path"))
        assertEquals("http://Quillmere.example/Path", UrlSort.normalized("  HTTP://Quillmere.example/Path "))
        assertEquals("https://quillmere.example/path?q=a&b=c", UrlSort.normalized("https://quillmere.example/path?q=a&b=c"))

        assertEquals(
            SortResolution.Resolved("https://Quillmere.example/Path"),
            UrlSort.resolver.resolve("HTTPS://Quillmere.example/Path"),
        )
    }

    @Test
    fun `whitespace inside an address is refused`() = runTest {
        assertNull(UrlSort.normalized("https://quillmere.example/a b"))
        assertNull(UrlSort.normalized("https://quillmere.example/a\tb"))

        assertEquals(SortResolution.Unreadable, UrlSort.resolver.resolve("https://quillmere.example/a b"))
    }

    @Test
    fun `a scheme other than http or https is refused`() = runTest {
        assertNull(UrlSort.normalized("ftp://quillmere.example/path"))
        assertNull(UrlSort.normalized("quillmere.example"))

        assertEquals(SortResolution.Unreadable, UrlSort.resolver.resolve("ftp://quillmere.example/path"))
    }

    // --- The host check (owner ruling on batch 1 question 2, 2026-11-27: «отказ») ---
    //
    // The address is opened only when its host is plain ASCII with no punycode label, so that the consent card names
    // the host the browser will reach in the letters it will reach. Every refusal below is a host the browser would
    // turn into a different name than the card shows; the last test is the control — an ASCII host is untouched.

    /**
     * A Cyrillic «а» (U+0430) in the host, a Cyrillic «о» (U+043E) in the TLD, a whole IDN host — before a port, a path,
     * a query, a fragment; and after a colon that is not a port (only `:digits` is dropped as one, so nothing non-ASCII
     * hides there).
     */
    @Test
    fun `a host with any non-ASCII character is refused`() = runTest {
        listOf(
            "https://аpple.com",
            "HTTPS://аpple.com/path",
            "https://apple.cоm",
            "https://пример.рф",
            "https://аpple.com:443/path",
            "https://аpple.com?q=1",
            "https://аpple.com#top",
            "https://apple.com:аpple/path",
        ).forEach { address -> assertNull("'$address' must be refused", UrlSort.normalized(address)) }

        assertEquals(SortResolution.Unreadable, UrlSort.resolver.resolve("https://аpple.com"))
    }

    /** `xn--` is how an IDN label travels in ASCII — the same look-alike under another spelling; compared case-insensitively, in any label. */
    @Test
    fun `a host with a punycode label is refused, in any case and any label`() = runTest {
        listOf(
            "https://xn--pple-43d.com",
            "https://XN--PPLE-43D.com",
            "https://Xn--pple-43d.com/path",
            "https://www.xn--e1afmkfd.xn--p1ai/",
            "https://xn--pple-43d.com:8080/",
        ).forEach { address -> assertNull("'$address' must be refused", UrlSort.normalized(address)) }
    }

    /** Userinfo is refused outright: «apple.com@…» on a consent card is a look-alike of its own, whatever host follows the last «@». */
    @Test
    fun `an address with userinfo in its authority is refused`() = runTest {
        listOf(
            "https://apple.com@quillmere.example",
            "https://user:pass@quillmere.example/path",
            "https://@quillmere.example",
        ).forEach { address -> assertNull("'$address' must be refused", UrlSort.normalized(address)) }
    }

    /** A browser percent-decodes a host before it reads it as a name: `%D0%B0` is the Cyrillic «а» again. */
    @Test
    fun `a percent-encoded host is refused`() = runTest {
        listOf(
            "https://%D0%B0pple.com",
            "https://apple%2Ecom/path",
        ).forEach { address -> assertNull("'$address' must be refused", UrlSort.normalized(address)) }
    }

    @Test
    fun `an address with no host is refused`() = runTest {
        listOf(
            "https://",
            "https:///path",
            "https://:443/path",
            "https://?q=1",
            "https://#top",
        ).forEach { address -> assertNull("'$address' must be refused", UrlSort.normalized(address)) }
    }

    /**
     * The control: an ASCII host — with a port, a path, a query, a fragment, mixed case, an IP literal — normalises
     * exactly as before the host check (scheme lowered, the rest byte for byte). What is past the authority is not the
     * host: an «@», a non-ASCII character or an `xn--` there is kept as written.
     */
    @Test
    fun `an ASCII host normalises exactly as before, whatever follows it`() = runTest {
        mapOf(
            "HTTPS://Quillmere.Example:8443/Path?Q=A&b=c#Frag" to "https://Quillmere.Example:8443/Path?Q=A&b=c#Frag",
            "https://quillmere.example:443" to "https://quillmere.example:443",
            "http://127.0.0.1:8080/x" to "http://127.0.0.1:8080/x",
            "https://[::1]:8080/" to "https://[::1]:8080/",
            "https://quillmere.example/@ostravel" to "https://quillmere.example/@ostravel",
            "https://quillmere.example/xn--pple-43d?q=а" to "https://quillmere.example/xn--pple-43d?q=а",
            "https://quillmere.example#аpple.com" to "https://quillmere.example#аpple.com",
        ).forEach { (address, expected) -> assertEquals("'$address'", expected, UrlSort.normalized(address)) }
    }
}
