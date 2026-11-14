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
}
