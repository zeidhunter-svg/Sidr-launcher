package com.sidr.launcher.domain.prayer

import java.time.LocalDate
import kotlin.math.round

/**
 * Names for the daily schedule entries (DS-6B Task 3).
 *
 * Only the five daily prayers ([FAJR], [DHUHR], [ASR], [MAGHRIB], [ISHA]) are prayers.
 * [SUNRISE] is **not** one of the five daily prayers — it is an astronomical marker that may appear
 * in a detail surface only (spec §3); [PrayerDaySchedule] keeps it structurally outside the five
 * mandatory instants so Home can never present it as a prayer.
 */
enum class PrayerName {
    FAJR,

    /** NOT a prayer. Detail-surface marker only; never a member of [PrayerDaySchedule.instants]. */
    SUNRISE,

    DHUHR,
    ASR,
    MAGHRIB,
    ISHA,
    ;

    companion object {
        /** The five daily prayers — exactly the set a [PrayerDaySchedule] must carry. */
        val FIVE_PRAYERS: Set<PrayerName> = setOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
    }
}

/**
 * Opaque key of a parameterized calculation method (e.g. `"MWL"`, `"ISNA"`, `"TURKEY"`).
 *
 * The method catalog lives with the calculation adapter (`:data:prayer`); domain never defaults or
 * guesses a method (spec §0.1/§0.2 — method is an explicit first-run user choice).
 */
@JvmInline
value class CalculationMethodId(val key: String) {
    init {
        require(key.isNotBlank()) { "A calculation method key must not be blank." }
    }
}

/** Asr juristic basis. An explicit mandatory user choice — never defaulted (spec §0.2). */
enum class Madhab {
    STANDARD,
    HANAFI,
}

/**
 * Where a schedule's times come from. v1 has exactly one source: local offline calculation
 * (spec §0.3). This enum is the deliberate seam for future official-source adapters
 * (e.g. a national authority) added as separate values in a later block.
 */
enum class PrayerAuthority {
    LOCAL_CALC,
}

/** How the user's prayer location was obtained (spec §0.4). */
enum class PrayerLocationSource {
    /** Picked from the bundled offline city index. */
    CITY,

    /** One-shot device location read, coordinates rounded before they reach domain. */
    DEVICE,
}

/**
 * The user's prayer location. Coordinates are ALWAYS rounded to 2 decimal places (~1.1 km,
 * ≤ ~1 min schedule error — spec §0.5); precise coordinates are structurally unrepresentable here.
 * [tzId] is an IANA zone id (e.g. `"Europe/Istanbul"`) — day boundaries use THIS zone, never
 * blindly the device zone (spec §6).
 */
data class PrayerLocation(
    val label: String,
    val lat2dp: Double,
    val lon2dp: Double,
    val tzId: String,
    val source: PrayerLocationSource,
) {
    init {
        require(label.isNotBlank()) { "A prayer location must carry a display label." }
        require(tzId.isNotBlank()) { "A prayer location must carry an IANA timezone id." }
        require(lat2dp in -90.0..90.0) { "Latitude out of range." }
        require(lon2dp in -180.0..180.0) { "Longitude out of range." }
        require(isRoundedTo2dp(lat2dp) && isRoundedTo2dp(lon2dp)) {
            "Coordinates must be pre-rounded to 2 decimal places — precise coordinates are unrepresentable in domain."
        }
    }

    private companion object {
        fun isRoundedTo2dp(value: Double): Boolean = round(value * 100.0) / 100.0 == value
    }
}

/** One labelled time. The label is part of the type — an unlabelled time is unrepresentable. */
data class PrayerInstant(
    val name: PrayerName,
    val epochMillis: Long,
)

/**
 * One day's schedule, dated in the **location** timezone (spec §6).
 *
 * [instants] must be exactly the five daily prayers. [sunrise] is optional and kept structurally
 * apart so it can never be rendered as a sixth prayer (spec §3).
 */
data class PrayerDaySchedule(
    val dateInLocationTz: LocalDate,
    val instants: List<PrayerInstant>,
    val sunrise: PrayerInstant? = null,
) {
    init {
        require(instants.size == 5 && instants.map { it.name }.toSet() == PrayerName.FIVE_PRAYERS) {
            "A day schedule must carry exactly the five daily prayers, each labelled once."
        }
        require(sunrise == null || sunrise.name == PrayerName.SUNRISE) {
            "The sunrise slot only accepts a SUNRISE-labelled instant."
        }
    }
}

/**
 * Where, how, and when a schedule was computed. Every visible schedule carries one — "no schedule
 * without provenance" (spec §2) is enforced by [PrayerContext.Available] requiring this by type.
 *
 * [locationSource] (I18N-2) is the discriminator a renderer needs to localize [locationLabel]:
 * [PrayerLocationSource.DEVICE]'s label is the fixed English identity
 * `AndroidPrayerLocationProvider.DEVICE_LOCATION_LABEL` (never translated — it doubles as
 * [com.sidr.launcher.domain.prayer.GetPrayerContextUseCase]'s cache-validity key via
 * `matchesSetup`, so its stored VALUE must never change), while [PrayerLocationSource.CITY]'s label
 * is a proper name from the offline GeoNames index and is shown verbatim in every locale.
 */
data class PrayerScheduleProvenance(
    val authority: PrayerAuthority,
    val methodId: CalculationMethodId,
    val madhab: Madhab,
    val locationLabel: String,
    val computedAtMillis: Long,
    val locationSource: PrayerLocationSource,
) {
    init {
        require(locationLabel.isNotBlank()) { "Provenance must name the location it was computed for." }
    }
}

/** Freshness of a visible schedule (spec §6). Stale data is always labelled stale (spec §2). */
enum class Freshness {
    /** Just recomputed locally for the current day/setup. */
    VERIFIED_CURRENT,

    /** Cached, same day in the location timezone + same setup — shown for instant first frame. */
    CACHED_FRESH,

    /** Cached and recompute failed — visible only with an explicit stale label, never silently. */
    CACHED_STALE,
}

/** Whether the device timezone agrees with the prayer-location timezone (spec §6: conflict visible). */
enum class TimeZoneState {
    MATCHES_DEVICE,
    CONFLICT,
}

/** Why no prayer context can be truthfully shown (spec §2: failure visible, never faked). */
enum class UnavailableReason {
    /** First-run: method/madhab never chosen. A real state — nothing is guessed (spec §0.1). */
    NOT_CONFIGURED,

    /** Method/madhab chosen but no location picked yet. */
    LOCATION_MISSING,

    /** Recompute failed and no usable labelled cache exists. */
    CALCULATION_FAILED,
}
