package com.sidr.launcher.core.ui.component

import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Plain-JUnit test (no Compose rule needed) for the structural invariant a non-empty [SidrPrayerTimeUi]
 * schedule must always render with non-blank provenance (spec §8) — exercised directly against
 * [requirePrayerSummaryInvariant], the pure function the composable's `require(...)` delegates to.
 */
class SidrPrayerSummaryInvariantTest {

    @Test fun throws_for_non_empty_prayers_with_blank_provenance() {
        val prayers = listOf(SidrPrayerTimeUi(name = "Asr", time = "15:42", isNext = true))
        assertThrows(IllegalArgumentException::class.java) {
            requirePrayerSummaryInvariant(prayers = prayers, provenance = "")
        }
    }

    @Test fun throws_for_non_empty_prayers_with_whitespace_only_provenance() {
        val prayers = listOf(SidrPrayerTimeUi(name = "Asr", time = "15:42"))
        assertThrows(IllegalArgumentException::class.java) {
            requirePrayerSummaryInvariant(prayers = prayers, provenance = "   ")
        }
    }

    @Test fun does_not_throw_for_non_empty_prayers_with_provenance() {
        val prayers = listOf(SidrPrayerTimeUi(name = "Asr", time = "15:42"))
        requirePrayerSummaryInvariant(prayers = prayers, provenance = "cached")
    }

    @Test fun does_not_throw_for_empty_prayers_with_blank_provenance() {
        requirePrayerSummaryInvariant(prayers = emptyList(), provenance = "")
    }
}
