package com.sidr.launcher.domain.sort

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The duration sort (A4′ phase 3b, fork 3b-C, B-5). [DurationSort.secondsOf] is the worker's former
 * private `parseSeconds`, moved verbatim — `Tier0IntentToolWorkerTest` still drives the worker
 * through it, so the two suites together prove the move did not change a reading. The resolver
 * validates and does not rewrite: a readable duration resolves to the words as written.
 */
class DurationSortTest {

    @Test
    fun `en ru tr unit words read as the worker read them`() = runTest {
        assertEquals(600, DurationSort.secondsOf("10 minutes"))
        assertEquals(600, DurationSort.secondsOf("10 минут"))
        assertEquals(600, DurationSort.secondsOf("10 dakika"))
        assertEquals(7200, DurationSort.secondsOf("2 saat"))
        assertEquals(30, DurationSort.secondsOf("30 сек"))

        assertEquals(SortResolution.Resolved("10 dakika"), DurationSort.resolver.resolve("10 dakika"))
    }

    /** Fix round 1 of A1′ (R11): a prefix match read «1 min 30 sec» as 60 s and «10 minecraft» as 600 s. */
    @Test
    fun `a unit is matched whole, never by prefix`() = runTest {
        assertNull(DurationSort.secondsOf("10 minecraft"))
        assertNull(DurationSort.secondsOf("1 min 30 sec"))

        assertEquals(SortResolution.Unreadable, DurationSort.resolver.resolve("10 minecraft"))
    }

    @Test
    fun `a bare number is minutes, five digits are refused`() {
        assertEquals(300, DurationSort.secondsOf("5"))
        assertEquals(599_940, DurationSort.secondsOf("9999"))
        assertNull(DurationSort.secondsOf("12345"))
    }
}
