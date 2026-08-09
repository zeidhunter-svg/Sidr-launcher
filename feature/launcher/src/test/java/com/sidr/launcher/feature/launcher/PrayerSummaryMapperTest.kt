package com.sidr.launcher.feature.launcher

import com.sidr.launcher.core.ui.component.SidrPrayerSummaryStatus
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Freshness
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerAuthority
import com.sidr.launcher.domain.prayer.PrayerContext
import com.sidr.launcher.domain.prayer.PrayerDaySchedule
import com.sidr.launcher.domain.prayer.PrayerInstant
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.prayer.PrayerScheduleProvenance
import com.sidr.launcher.domain.prayer.TimeZoneState
import com.sidr.launcher.domain.prayer.UnavailableReason
import java.time.LocalDate
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage gap closed (DS-6B Task 9 review): [toHomePrayerSummaryUi] is the ONLY code that turns
 * [PrayerContext.Available.locationTzId] + each [PrayerInstant.epochMillis] into a displayed
 * `HH:mm` string, and until this test existed nothing exercised that formatting — VM tests only
 * check the domain `locationTzId` field passes through, and the screen tests happen to use a
 * device zone equal to the location zone (no real tz conflict). This test plants a genuine
 * mismatch (JVM default = America/New_York, schedule computed for Asia/Tokyo) so a future
 * accidental swap to `ZoneId.systemDefault()` in the mapper would fail this test.
 */
class PrayerSummaryMapperTest {

    private lateinit var originalDefaultZone: TimeZone

    @Before
    fun setUp() {
        originalDefaultZone = TimeZone.getDefault()
        // Deliberately DIFFERENT from the schedule's Asia/Tokyo location zone — this is what makes
        // the test genuinely catch a `ZoneId.systemDefault()` regression rather than passing by
        // coincidence.
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalDefaultZone)
    }

    private fun tokyoEpochMillis(hour: Int, minute: Int): Long =
        LocalDate.of(2026, 8, 10)
            .atTime(hour, minute)
            .atZone(java.time.ZoneId.of("Asia/Tokyo"))
            .toInstant()
            .toEpochMilli()

    private fun tokyoSchedule(): PrayerDaySchedule =
        PrayerDaySchedule(
            dateInLocationTz = LocalDate.of(2026, 8, 10),
            instants = listOf(
                PrayerInstant(PrayerName.FAJR, tokyoEpochMillis(3, 30)),
                PrayerInstant(PrayerName.DHUHR, tokyoEpochMillis(11, 40)),
                PrayerInstant(PrayerName.ASR, tokyoEpochMillis(15, 10)),
                PrayerInstant(PrayerName.MAGHRIB, tokyoEpochMillis(18, 20)),
                PrayerInstant(PrayerName.ISHA, tokyoEpochMillis(19, 45)),
            ),
        )

    private fun available(
        schedule: PrayerDaySchedule = tokyoSchedule(),
        freshness: Freshness = Freshness.VERIFIED_CURRENT,
        timeZoneState: TimeZoneState = TimeZoneState.MATCHES_DEVICE,
        nextPrayer: PrayerName? = PrayerName.ASR,
        locationTzId: String = "Asia/Tokyo",
        methodId: CalculationMethodId = CalculationMethodId("MWL"),
        madhab: Madhab = Madhab.STANDARD,
        locationLabel: String = "Tokyo",
    ): PrayerContext.Available =
        PrayerContext.Available(
            schedule = schedule,
            provenance = PrayerScheduleProvenance(
                authority = PrayerAuthority.LOCAL_CALC,
                methodId = methodId,
                madhab = madhab,
                locationLabel = locationLabel,
                computedAtMillis = tokyoEpochMillis(0, 0),
            ),
            freshness = freshness,
            timeZoneState = timeZoneState,
            nextPrayer = nextPrayer,
            locationTzId = locationTzId,
        )

    @Test
    fun `formats each prayer time in the location timezone, never the device zone`() {
        val ui = available().toHomePrayerSummaryUi()

        checkNotNull(ui)
        val timesByName = ui.prayers.associate { it.name to it.time }
        assertEquals(
            mapOf(
                "FAJR" to "03:30",
                "DHUHR" to "11:40",
                "ASR" to "15:10",
                "MAGHRIB" to "18:20",
                "ISHA" to "19:45",
            ),
            timesByName,
        )
    }

    @Test
    fun `isNext is set on exactly the cell matching Available nextPrayer`() {
        val ui = available(nextPrayer = PrayerName.MAGHRIB).toHomePrayerSummaryUi()

        checkNotNull(ui)
        val nextCells = ui.prayers.filter { it.isNext }
        assertEquals(1, nextCells.size)
        assertEquals("MAGHRIB", nextCells.single().name)
    }

    @Test
    fun `no prayer is marked next when nextPrayer is null`() {
        val ui = available(nextPrayer = null).toHomePrayerSummaryUi()

        checkNotNull(ui)
        assertTrue(ui.prayers.none { it.isNext })
    }

    @Test
    fun `TZ CONFLICT takes precedence over freshness`() {
        val ui = available(
            freshness = Freshness.VERIFIED_CURRENT,
            timeZoneState = TimeZoneState.CONFLICT,
        ).toHomePrayerSummaryUi()

        checkNotNull(ui)
        assertEquals(SidrPrayerSummaryStatus.TimezoneConflict, ui.status)
    }

    @Test
    fun `MATCHES_DEVICE plus CACHED_STALE maps to CachedStale`() {
        val ui = available(
            freshness = Freshness.CACHED_STALE,
            timeZoneState = TimeZoneState.MATCHES_DEVICE,
        ).toHomePrayerSummaryUi()

        checkNotNull(ui)
        assertEquals(SidrPrayerSummaryStatus.CachedStale, ui.status)
    }

    @Test
    fun `provenance string is LOCAL CALC dot method label dot madhab`() {
        val ui = available(
            methodId = CalculationMethodId("UMM_AL_QURA"),
            madhab = Madhab.HANAFI,
        ).toHomePrayerSummaryUi()

        checkNotNull(ui)
        assertEquals("LOCAL CALC · Umm al-Qura University, Makkah · Hanafi", ui.provenance)
    }

    @Test
    fun `Unavailable maps to null - nothing to render`() {
        val ui = PrayerContext.Unavailable(UnavailableReason.LOCATION_MISSING).toHomePrayerSummaryUi()

        assertNull(ui)
    }
}
