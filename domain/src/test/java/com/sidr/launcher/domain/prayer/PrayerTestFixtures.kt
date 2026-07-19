package com.sidr.launcher.domain.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Shared DS-6B Task 3 test fixtures. Times are built through `java.time` in the location zone so
 * every epoch in a test is derived, never hand-computed.
 */
internal object PrayerTestFixtures {

    val ISTANBUL_ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
    val TOKYO_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    /** 13:00 local in Istanbul (UTC+3 in July) on 2026-07-13. */
    val NOW: Instant = Instant.parse("2026-07-13T10:00:00Z")
    val TODAY_ISTANBUL: LocalDate = LocalDate.of(2026, 7, 13)

    fun istanbulLocation(label: String = "Istanbul") = PrayerLocation(
        label = label,
        lat2dp = 41.01,
        lon2dp = 28.98,
        tzId = "Europe/Istanbul",
        source = PrayerLocationSource.CITY,
    )

    fun tokyoLocation() = PrayerLocation(
        label = "Tokyo",
        lat2dp = 35.68,
        lon2dp = 139.77,
        tzId = "Asia/Tokyo",
        source = PrayerLocationSource.CITY,
    )

    fun turkeySetup(location: PrayerLocation? = istanbulLocation()) = PrayerSetup(
        methodId = CalculationMethodId("TURKEY"),
        madhab = Madhab.STANDARD,
        location = location,
    )

    fun epochAt(date: LocalDate, hour: Int, minute: Int, zone: ZoneId): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Fajr 04:30, Dhuhr 13:10, Asr 17:05, Maghrib 20:35, Isha 22:15 local on [date] in [zone]. */
    fun scheduleFor(date: LocalDate, zone: ZoneId = ISTANBUL_ZONE) = PrayerDaySchedule(
        dateInLocationTz = date,
        instants = listOf(
            PrayerInstant(PrayerName.FAJR, epochAt(date, 4, 30, zone)),
            PrayerInstant(PrayerName.DHUHR, epochAt(date, 13, 10, zone)),
            PrayerInstant(PrayerName.ASR, epochAt(date, 17, 5, zone)),
            PrayerInstant(PrayerName.MAGHRIB, epochAt(date, 20, 35, zone)),
            PrayerInstant(PrayerName.ISHA, epochAt(date, 22, 15, zone)),
        ),
    )

    fun provenanceFor(setup: PrayerSetup, computedAtMillis: Long = 1L) = PrayerScheduleProvenance(
        authority = PrayerAuthority.LOCAL_CALC,
        methodId = setup.methodId,
        madhab = setup.madhab,
        locationLabel = setup.location!!.label,
        computedAtMillis = computedAtMillis,
    )
}
