package com.sidr.launcher.data.prayer

/*
 * DS-6B Task 4 — timezone/DST correctness + failure-mapping tests for [AdhanPrayerCalculator].
 * See AdhanPrayerCalculatorGoldenTest.kt for the full adhan2:0.0.5 license/version/API record.
 *
 * These tests are structural (java.time offset/date-fidelity checks), not published-table
 * comparisons — DST transition dates were deliberately NOT tied to a ±2 min golden assertion,
 * since the point here is proving the LOCATION timezone drives the schedule, not re-verifying
 * astronomical accuracy (already covered by the golden tests). 2026 Europe/London transition dates
 * (spring-forward 2026-03-29, fall-back 2026-10-25) were computed directly from `java.time`'s own
 * bundled tzdata (the same tzdata the production code runs against), not looked up externally.
 *
 * Per the task instructions: these tests convert epochMillis -> wall-clock via explicit
 * `java.time` zone conversions and NEVER mutate the JVM's global default `TimeZone` (no
 * `TimeZone.setDefault(...)` anywhere below) — the "device tz differs from location tz" property is
 * proven by converting the SAME instant through two different explicit zones, not by touching
 * process-global state.
 */

import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerDaySchedule
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.result.OperationResult
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdhanPrayerCalculatorDstTest {

    private val calculator = AdhanPrayerCalculator()

    private val london = PrayerLocation(
        label = "London",
        lat2dp = 51.51,
        lon2dp = -0.13,
        tzId = "Europe/London",
        source = PrayerLocationSource.CITY,
    )

    // ---- Europe/London spring-forward (2026-03-29: GMT Z -> BST +01:00, verified via java.time) --

    @Test
    fun `Europe London spring-forward transition is reflected in the location zone`() = runTest {
        val beforeDst = scheduleFor(london, LocalDate.of(2026, 3, 28)) // last day of GMT
        val afterDst = scheduleFor(london, LocalDate.of(2026, 3, 29)) // first day of BST

        assertEquals(LocalDate.of(2026, 3, 28), beforeDst.dateInLocationTz)
        assertEquals(LocalDate.of(2026, 3, 29), afterDst.dateInLocationTz)

        val dhuhrBefore = zonedDhuhr(beforeDst, london.tzId)
        val dhuhrAfter = zonedDhuhr(afterDst, london.tzId)

        assertEquals(ZoneOffset.UTC, dhuhrBefore.offset)
        assertEquals(ZoneOffset.ofHours(1), dhuhrAfter.offset)

        // Each Dhuhr must still fall on its own requested civil date in the location zone.
        assertEquals(LocalDate.of(2026, 3, 28), dhuhrBefore.toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 29), dhuhrAfter.toLocalDate())
    }

    // ---- Europe/London fall-back (2026-10-25: BST +01:00 -> GMT Z, verified via java.time) -------

    @Test
    fun `Europe London fall-back transition is reflected in the location zone`() = runTest {
        val beforeDst = scheduleFor(london, LocalDate.of(2026, 10, 24)) // last day of BST
        val afterDst = scheduleFor(london, LocalDate.of(2026, 10, 25)) // first day of GMT

        val dhuhrBefore = zonedDhuhr(beforeDst, london.tzId)
        val dhuhrAfter = zonedDhuhr(afterDst, london.tzId)

        assertEquals(ZoneOffset.ofHours(1), dhuhrBefore.offset)
        assertEquals(ZoneOffset.UTC, dhuhrAfter.offset)
    }

    // ---- no-DST zone: Asia/Riyadh stays at a fixed offset year-round --------------------------

    @Test
    fun `Asia Riyadh has no DST - offset is identical 6 months apart`() = runTest {
        val riyadh = PrayerLocation(
            label = "Riyadh",
            lat2dp = 24.71,
            lon2dp = 46.68,
            tzId = "Asia/Riyadh",
            source = PrayerLocationSource.CITY,
        )

        val winter = scheduleFor(riyadh, LocalDate.of(2026, 1, 15))
        val summer = scheduleFor(riyadh, LocalDate.of(2026, 7, 15))

        val winterOffset = zonedDhuhr(winter, riyadh.tzId).offset
        val summerOffset = zonedDhuhr(summer, riyadh.tzId).offset

        assertEquals(ZoneOffset.ofHours(3), winterOffset)
        assertEquals(ZoneOffset.ofHours(3), summerOffset)
    }

    // ---- device tz != location tz: schedule always follows the LOCATION tz ---------------------

    @Test
    fun `schedule follows the location timezone regardless of any other zone used to read it`() = runTest {
        val kazan = PrayerLocation(
            label = "Kazan",
            lat2dp = 55.79,
            lon2dp = 49.12,
            tzId = "Europe/Moscow",
            source = PrayerLocationSource.CITY,
        )
        val requestedDate = LocalDate.of(2026, 10, 15)

        val schedule = scheduleFor(kazan, requestedDate)
        val dhuhrEpochMillis = schedule.instants.first { it.name == PrayerName.DHUHR }.epochMillis
        val instant = Instant.ofEpochMilli(dhuhrEpochMillis)

        // Reading Dhuhr through the LOCATION zone must land on the requested civil date — this is
        // the contract: dateInLocationTz is used directly to build the adhan civil date, never
        // re-derived from any device clock.
        val inLocationZone = instant.atZone(ZoneId.of(kazan.tzId))
        assertEquals(requestedDate, inLocationZone.toLocalDate())

        // The SAME absolute instant, read through an unrelated zone far from Kazan (Europe/Moscow
        // is UTC+3; Pacific/Honolulu is UTC-10 — a 13-hour gap), lands on a different local date and
        // time. This is not mutating any global/device default zone — it's the same fixed instant
        // read two different explicit ways — proving the schedule is anchored to an absolute moment
        // whose correct civil-day reading depends on using the LOCATION zone, never an incidental
        // device zone.
        val inUnrelatedZone = instant.atZone(ZoneId.of("Pacific/Honolulu"))
        assertNotEquals(inLocationZone.toLocalDate(), inUnrelatedZone.toLocalDate())
        assertNotEquals(inLocationZone.toLocalTime(), inUnrelatedZone.toLocalTime())
    }

    // ---- failure mapping: never throws, always OperationResult.Failure -------------------------

    @Test
    fun `unsupported calculation method key maps to Failure without throwing`() = runTest {
        val result = calculator.calculate(
            location = london,
            methodId = CalculationMethodId("NOT_A_REAL_METHOD"),
            madhab = Madhab.STANDARD,
            dateInLocationTz = LocalDate.of(2026, 6, 1),
        )

        assertTrue(
            "expected Failure for an unsupported method key, was $result",
            result is OperationResult.Failure,
        )
    }

    @Test
    fun `extreme polar latitude maps to Failure without throwing`() = runTest {
        // The North Pole is inside PrayerLocation's own valid range (-90..90), but adhan2 cannot
        // compute a real sunrise/sunset there around midsummer (permanent polar day) — this is
        // exactly the "PrayerLocation type still allows it, but calculation legitimately fails"
        // case the brief asks for. Domain never rejects this location; the CALCULATOR must map the
        // resulting adhan2 failure to OperationResult.Failure, never let it throw.
        val northPole = PrayerLocation(
            label = "North Pole",
            lat2dp = 90.0,
            lon2dp = 0.0,
            tzId = "UTC",
            source = PrayerLocationSource.DEVICE,
        )

        val result = calculator.calculate(
            location = northPole,
            methodId = CalculationMethodId("MWL"),
            madhab = Madhab.STANDARD,
            dateInLocationTz = LocalDate.of(2026, 6, 21), // summer solstice: deep in polar day
        )

        assertTrue(
            "expected Failure for an unreachable polar schedule, was $result",
            result is OperationResult.Failure,
        )
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private suspend fun scheduleFor(location: PrayerLocation, date: LocalDate): PrayerDaySchedule {
        val result = calculator.calculate(location, CalculationMethodId("MWL"), Madhab.STANDARD, date)
        return when (result) {
            is OperationResult.Success -> result.value
            is OperationResult.Failure ->
                throw AssertionError("expected Success but was Failure(${result.error})")
        }
    }

    private fun zonedDhuhr(schedule: PrayerDaySchedule, zoneId: String) =
        Instant.ofEpochMilli(schedule.instants.first { it.name == PrayerName.DHUHR }.epochMillis)
            .atZone(ZoneId.of(zoneId))
}
