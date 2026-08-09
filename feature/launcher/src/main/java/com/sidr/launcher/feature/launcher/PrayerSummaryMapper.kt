package com.sidr.launcher.feature.launcher

import com.sidr.launcher.core.ui.component.SidrPrayerSummaryStatus
import com.sidr.launcher.core.ui.component.SidrPrayerTimeUi
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Freshness
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerContext
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.prayer.SupportedPrayerMethods
import com.sidr.launcher.domain.prayer.TimeZoneState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Home's five daily prayers, in canonical display order (spec §3) — SUNRISE is never a member.
 * Deliberately duplicated (not shared) with `feature/prayer`'s identical constant: there is no
 * `feature -> feature` edge, and this list is tiny/stable enough that duplication beats coupling.
 */
private val FIVE_PRAYERS_ORDER =
    listOf(PrayerName.FAJR, PrayerName.DHUHR, PrayerName.ASR, PrayerName.MAGHRIB, PrayerName.ISHA)

/**
 * What Home's [com.sidr.launcher.core.ui.component.SidrPrayerSummary] strip may truthfully render
 * (DS-6B Task 9). Kept feature-local so `core/ui` stays domain-free.
 */
internal data class HomePrayerSummaryUi(
    val prayers: List<SidrPrayerTimeUi>,
    val status: SidrPrayerSummaryStatus,
    val provenance: String,
    val locationLabel: String?,
)

/**
 * Maps the domain [PrayerContext] to what Home may render, or `null` when nothing should render.
 *
 * `null` ⟺ [PrayerContext.Unavailable] (NOT_CONFIGURED, LOCATION_MISSING, or CALCULATION_FAILED).
 * The use case already turns "recompute failed but a usable cache exists" into
 * `Available(CACHED_STALE)`, so a genuine `Unavailable(CALCULATION_FAILED)` here means no usable
 * cache exists at all — Home shows nothing rather than fabricate a time.
 */
internal fun PrayerContext.toHomePrayerSummaryUi(): HomePrayerSummaryUi? {
    val available = this as? PrayerContext.Available ?: return null
    val zone = ZoneId.of(available.locationTzId)
    val prayers = FIVE_PRAYERS_ORDER.map { name ->
        val instant = available.schedule.instants.first { it.name == name }
        SidrPrayerTimeUi(
            name = name.name,
            time = formatTime(instant.epochMillis, zone),
            isNext = available.nextPrayer == name,
        )
    }
    return HomePrayerSummaryUi(
        prayers = prayers,
        status = available.toSummaryStatus(),
        provenance = available.toProvenanceText(),
        locationLabel = available.provenance.locationLabel,
    )
}

/**
 * TZ CONFLICT takes precedence (spec §6) over freshness — a schedule computed for the wrong
 * timezone is the salient warning even when it is otherwise verified/cached-fresh.
 */
private fun PrayerContext.Available.toSummaryStatus(): SidrPrayerSummaryStatus = when {
    timeZoneState == TimeZoneState.CONFLICT -> SidrPrayerSummaryStatus.TimezoneConflict
    freshness == Freshness.VERIFIED_CURRENT -> SidrPrayerSummaryStatus.VerifiedCurrent
    freshness == Freshness.CACHED_FRESH -> SidrPrayerSummaryStatus.CachedFresh
    freshness == Freshness.CACHED_STALE -> SidrPrayerSummaryStatus.CachedStale
    else -> SidrPrayerSummaryStatus.NoData // unreachable: Freshness has exactly the 3 cases above.
}

/** `"LOCAL CALC · <METHOD LABEL> · <MADHAB>"` (spec §0.7). */
private fun PrayerContext.Available.toProvenanceText(): String =
    "LOCAL CALC · ${methodLabel(provenance.methodId)} · ${madhabLabel(provenance.madhab)}"

/** [SupportedPrayerMethods] is the only place a method key is turned into display copy; falls back
 *  to the raw key if the catalog is ever out of sync (never blank, never a crash). */
private fun methodLabel(methodId: CalculationMethodId): String =
    SupportedPrayerMethods.ALL.firstOrNull { it.id == methodId }?.displayLabel ?: methodId.key

private fun madhabLabel(madhab: Madhab): String = when (madhab) {
    Madhab.STANDARD -> "Standard"
    Madhab.HANAFI -> "Hanafi"
}

/** `HH:mm` (24h) in [zone] — always the LOCATION zone (spec §6), never the device zone. */
private fun formatTime(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(zone)
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
