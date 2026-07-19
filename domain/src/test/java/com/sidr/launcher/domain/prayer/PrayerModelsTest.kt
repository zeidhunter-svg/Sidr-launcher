package com.sidr.launcher.domain.prayer

import com.sidr.launcher.domain.prayer.PrayerTestFixtures.ISTANBUL_ZONE
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.TODAY_ISTANBUL
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.epochAt
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.scheduleFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Model invariants (DS-6B Task 3): unlabelled or partial schedules are unrepresentable, sunrise is
 * structurally not a prayer, coordinates are structurally 2dp, and nothing prayer-flavoured accepts
 * blank identity fields. These invariants are what makes "no schedule without provenance" (spec §2)
 * enforceable by type rather than by discipline.
 */
class PrayerModelsTest {

    private fun instant(name: PrayerName, hour: Int, minute: Int) =
        PrayerInstant(name, epochAt(TODAY_ISTANBUL, hour, minute, ISTANBUL_ZONE))

    // ── PrayerDaySchedule: exactly the five daily prayers ─────────────────────

    @Test
    fun `schedule with empty instants cannot be constructed - Available is unreachable without times`() {
        // PrayerContext.Available(schedule, ...) with empty instants is unrepresentable: the
        // schedule itself refuses construction, so the Available branch can never carry it.
        assertThrows(IllegalArgumentException::class.java) {
            PrayerDaySchedule(dateInLocationTz = TODAY_ISTANBUL, instants = emptyList())
        }
    }

    @Test
    fun `schedule missing one of the five prayers throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerDaySchedule(
                dateInLocationTz = TODAY_ISTANBUL,
                instants = listOf(
                    instant(PrayerName.FAJR, 4, 30),
                    instant(PrayerName.DHUHR, 13, 10),
                    instant(PrayerName.ASR, 17, 5),
                    instant(PrayerName.MAGHRIB, 20, 35),
                    // ISHA missing
                ),
            )
        }
    }

    @Test
    fun `schedule with a duplicated prayer throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerDaySchedule(
                dateInLocationTz = TODAY_ISTANBUL,
                instants = listOf(
                    instant(PrayerName.FAJR, 4, 30),
                    instant(PrayerName.FAJR, 5, 0), // duplicate, DHUHR missing
                    instant(PrayerName.ASR, 17, 5),
                    instant(PrayerName.MAGHRIB, 20, 35),
                    instant(PrayerName.ISHA, 22, 15),
                ),
            )
        }
    }

    @Test
    fun `SUNRISE inside the instants list throws - sunrise is not a sixth prayer`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerDaySchedule(
                dateInLocationTz = TODAY_ISTANBUL,
                instants = scheduleFor(TODAY_ISTANBUL).instants +
                    instant(PrayerName.SUNRISE, 6, 5),
            )
        }
    }

    @Test
    fun `sunrise slot only accepts a SUNRISE-labelled instant`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerDaySchedule(
                dateInLocationTz = TODAY_ISTANBUL,
                instants = scheduleFor(TODAY_ISTANBUL).instants,
                sunrise = instant(PrayerName.DHUHR, 6, 5),
            )
        }
    }

    @Test
    fun `valid schedule with optional sunrise constructs and keeps sunrise outside the five`() {
        val schedule = PrayerDaySchedule(
            dateInLocationTz = TODAY_ISTANBUL,
            instants = scheduleFor(TODAY_ISTANBUL).instants,
            sunrise = instant(PrayerName.SUNRISE, 6, 5),
        )
        assertEquals(PrayerName.FIVE_PRAYERS, schedule.instants.map { it.name }.toSet())
        assertEquals(PrayerName.SUNRISE, schedule.sunrise?.name)
    }

    // ── PrayerLocation: 2dp coordinates, valid ranges, non-blank identity ─────

    @Test
    fun `location with more than 2dp latitude throws - precise coordinates are unrepresentable`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerTestFixtures.istanbulLocation().copy(lat2dp = 41.0123)
        }
    }

    @Test
    fun `location with more than 2dp longitude throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerTestFixtures.istanbulLocation().copy(lon2dp = 28.9784)
        }
    }

    @Test
    fun `location with out-of-range coordinates throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerTestFixtures.istanbulLocation().copy(lat2dp = 91.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrayerTestFixtures.istanbulLocation().copy(lon2dp = -181.0)
        }
    }

    @Test
    fun `location with blank label or blank tzId throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerTestFixtures.istanbulLocation().copy(label = "  ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PrayerTestFixtures.istanbulLocation().copy(tzId = "")
        }
    }

    @Test
    fun `valid 2dp location constructs`() {
        val location = PrayerTestFixtures.istanbulLocation()
        assertEquals(41.01, location.lat2dp, 0.0)
        assertEquals(28.98, location.lon2dp, 0.0)
    }

    // ── Identity fields elsewhere ──────────────────────────────────────────────

    @Test
    fun `blank calculation method key throws - no silent empty method`() {
        assertThrows(IllegalArgumentException::class.java) {
            CalculationMethodId(" ")
        }
    }

    @Test
    fun `provenance with blank location label throws - provenance is never anonymous`() {
        assertThrows(IllegalArgumentException::class.java) {
            PrayerScheduleProvenance(
                authority = PrayerAuthority.LOCAL_CALC,
                methodId = CalculationMethodId("MWL"),
                madhab = Madhab.STANDARD,
                locationLabel = "",
                computedAtMillis = 1L,
            )
        }
    }
}
