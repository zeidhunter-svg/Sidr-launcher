package com.sidr.launcher.domain.sort

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The clock-time sort (A4′ phase 3b, fork 3b-C, B-5). [ClockTimeSort.timeOf] is the worker's former
 * private `parseClockTime`, moved verbatim: `H:MM` or `HH:MM`, hours 0–23, minutes 0–59, nothing else.
 * The resolver validates and does not rewrite.
 *
 * The plan names the first test «H:MM and HH:MM resolve»; a JVM method name cannot hold `:`, so the
 * colon is spelled out (the precedent of Task 1's `… as reasoning max_tokens`).
 */
class ClockTimeSortTest {

    @Test
    fun `H colon MM and HH colon MM resolve`() = runTest {
        assertEquals(7 to 30, ClockTimeSort.timeOf("7:30"))
        assertEquals(19 to 45, ClockTimeSort.timeOf("19:45"))
        assertEquals(0 to 0, ClockTimeSort.timeOf("0:00"))
        assertEquals(23 to 59, ClockTimeSort.timeOf("23:59"))

        assertEquals(SortResolution.Resolved("7:30"), ClockTimeSort.resolver.resolve("7:30"))
    }

    @Test
    fun `out of range and other shapes are refused`() = runTest {
        assertNull(ClockTimeSort.timeOf("24:00"))
        assertNull(ClockTimeSort.timeOf("7:60"))
        assertNull(ClockTimeSort.timeOf("7.30"))
        assertNull(ClockTimeSort.timeOf("7:3"))

        assertEquals(SortResolution.Unreadable, ClockTimeSort.resolver.resolve("7.30"))
    }
}
