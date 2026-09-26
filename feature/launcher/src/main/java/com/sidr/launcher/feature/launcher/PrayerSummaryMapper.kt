package com.sidr.launcher.feature.launcher

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.sidr.launcher.core.ui.component.SidrPrayerSummaryStatus
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Freshness
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerContext
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.prayer.PrayerName
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
 *
 * I18N-1 Task 13 / I18N-2: [provenance] and each [prayers] entry's name carry typed identifiers
 * rather than a baked English sentence (the `feedbackText`/`FeedbackText` pattern from
 * `LauncherPresentation.kt`) — [sidrString] needs `@Composable` context, which this mapper's plain
 * (non-Composable) functions deliberately do not have, so they stay unit-testable from a plain JVM
 * test ([PrayerSummaryMapperTest]) with no Robolectric/Compose host. Resolution to a localized
 * string happens only at render time, in [prayerProvenanceText] / [prayerNameLabel].
 */
internal data class HomePrayerSummaryUi(
    val prayers: List<HomePrayerTimeUi>,
    val status: SidrPrayerSummaryStatus,
    val provenance: PrayerProvenanceUi,
    val locationLabel: String?,
    val locationSource: PrayerLocationSource?,
)

/**
 * One Home prayer cell, pre-render. [name] is the untranslated domain identifier — I18N-2: this used
 * to be `SidrPrayerTimeUi(name = name.name, ...)` built directly here, which put the raw enum name
 * (`"FAJR"`) into every cell's TalkBack `contentDescription`. Invisible in English (the locked
 * resource value happens to equal the enum name) but audible as literal English in `ru`/`tr`. Kept
 * typed here for the same JVM-testability reason as [PrayerProvenanceUi]; [prayerNameLabel] resolves
 * it at render time, mirroring `feature/prayer`'s `PrayerDetailScreen.toPrayerTimeUiList`.
 */
internal data class HomePrayerTimeUi(
    val name: PrayerName,
    val time: String,
    val isNext: Boolean,
)

/** Untranslated identifiers behind the Home provenance line — see [HomePrayerSummaryUi.provenance]. */
internal data class PrayerProvenanceUi(
    val methodId: CalculationMethodId,
    val madhab: Madhab,
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
        HomePrayerTimeUi(
            name = name,
            time = formatTime(instant.epochMillis, zone),
            isNext = available.nextPrayer == name,
        )
    }
    return HomePrayerSummaryUi(
        prayers = prayers,
        status = available.toSummaryStatus(),
        provenance = PrayerProvenanceUi(
            methodId = available.provenance.methodId,
            madhab = available.provenance.madhab,
        ),
        locationLabel = available.provenance.locationLabel,
        locationSource = available.provenance.locationSource,
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

/**
 * `"LOCAL CALC · <METHOD LABEL> · <MADHAB>"` (spec §0.7). I18N-1 Task 13: the frame is Class A locked
 * (`launcher_prayer_provenance_frame`, `translatable="false"` — spec §7.1, byte-identical to the
 * pre-I18N-1 literal) while the method/madhab labels are Class B locked (spec §7.2 religious
 * terminology, owner sign-off) — see `strings_locked.xml`.
 */
@Composable
internal fun prayerProvenanceText(provenance: PrayerProvenanceUi): String =
    sidrString(
        R.string.launcher_prayer_provenance_frame,
        methodLabel(provenance.methodId),
        madhabLabel(provenance.madhab),
    )

/**
 * Single source of truth for calculation-method key -> locked label resource (mirrors
 * `feature/prayer`'s `PrayerLabels.kt`). [CalculationMethodId] wraps a plain `String` key (a
 * non-sealed value class), so this `when` can never be made compiler-exhaustive - a 12th
 * [com.sidr.launcher.domain.prayer.SupportedPrayerMethods.ALL] entry shipping without a matching
 * branch here would silently fall through to [methodLabel]'s `?:` fallback and render its raw,
 * untranslated key. [MethodLabelCoverageTest] is the anti-drift guard: it drives this function
 * directly off the real catalog from a plain JVM test (no Robolectric/Compose host needed, since this
 * function is deliberately not `@Composable`).
 */
@StringRes
internal fun methodLabelResId(methodId: CalculationMethodId): Int? = when (methodId.key) {
    "MWL" -> R.string.launcher_prayer_method_mwl
    "EGYPTIAN" -> R.string.launcher_prayer_method_egyptian
    "KARACHI" -> R.string.launcher_prayer_method_karachi
    "UMM_AL_QURA" -> R.string.launcher_prayer_method_umm_al_qura
    "DUBAI" -> R.string.launcher_prayer_method_dubai
    "MOON_SIGHTING_COMMITTEE" -> R.string.launcher_prayer_method_moon_sighting_committee
    "NORTH_AMERICA" -> R.string.launcher_prayer_method_north_america
    "KUWAIT" -> R.string.launcher_prayer_method_kuwait
    "QATAR" -> R.string.launcher_prayer_method_qatar
    "SINGAPORE" -> R.string.launcher_prayer_method_singapore
    "TURKEY" -> R.string.launcher_prayer_method_turkey
    else -> null
}

/** Never blank, never a crash: falls back to the raw key if the catalog is ever out of sync with
 *  [methodLabelResId] (same defensive shape `feature/prayer`'s `methodLabel()` has). */
@Composable
private fun methodLabel(methodId: CalculationMethodId): String =
    methodLabelResId(methodId)?.let { sidrString(it) } ?: methodId.key

@Composable
private fun madhabLabel(madhab: Madhab): String = when (madhab) {
    Madhab.STANDARD -> sidrString(R.string.launcher_prayer_madhab_standard)
    Madhab.HANAFI -> sidrString(R.string.launcher_prayer_madhab_hanafi)
}

/**
 * Resolves [HomePrayerTimeUi.name] to the localized label spoken/shown for a prayer cell (I18N-2).
 * Exhaustive by construction — a real `when` over [PrayerName], not a lookup table keyed by a raw
 * string like [methodLabelResId] needs, so a future addition to the enum fails the build here rather
 * than silently falling through. [PrayerName.SUNRISE] is deliberately unreachable: Home's
 * [FIVE_PRAYERS_ORDER] never includes it (sunrise is a detail-screen-only row, spec §3), so it has no
 * locked resource in this module — mirrors that list's own comment, not an omission.
 */
@Composable
internal fun prayerNameLabel(name: PrayerName): String = when (name) {
    PrayerName.FAJR -> sidrString(R.string.launcher_prayer_name_fajr)
    PrayerName.DHUHR -> sidrString(R.string.launcher_prayer_name_dhuhr)
    PrayerName.ASR -> sidrString(R.string.launcher_prayer_name_asr)
    PrayerName.MAGHRIB -> sidrString(R.string.launcher_prayer_name_maghrib)
    PrayerName.ISHA -> sidrString(R.string.launcher_prayer_name_isha)
    PrayerName.SUNRISE -> error("SUNRISE is never a member of FIVE_PRAYERS_ORDER; Home has no sunrise row")
}

/**
 * Resolves [HomePrayerSummaryUi.locationLabel] for display (I18N-2). The STORED value of a
 * [PrayerLocationSource.DEVICE] label never changes — `AndroidPrayerLocationProvider`'s
 * `"Current location"` identity doubles as `GetPrayerContextUseCase`'s cache-validity key — but the
 * DISPLAYED text now folds under the user's locale here, at the same render-time seam as
 * [prayerNameLabel] / [prayerProvenanceText]. [PrayerLocationSource.CITY]'s label is a proper name
 * from the offline GeoNames index and is shown verbatim in every locale — there is nothing to
 * translate.
 */
@Composable
internal fun homeLocationLabelText(locationLabel: String?, locationSource: PrayerLocationSource?): String? {
    if (locationLabel == null) return null
    return if (locationSource == PrayerLocationSource.DEVICE) {
        sidrString(R.string.launcher_prayer_location_current_device)
    } else {
        locationLabel
    }
}

/** `HH:mm` (24h) in [zone] — always the LOCATION zone (spec §6), never the device zone. */
private fun formatTime(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(zone)
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
