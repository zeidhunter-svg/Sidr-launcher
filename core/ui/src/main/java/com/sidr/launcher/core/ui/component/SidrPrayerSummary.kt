package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrStatusMarker
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrColors
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing
import java.util.Locale

/**
 * One prayer time slot for [SidrPrayerSummary] (DS-6B Task 7, spec §8). Pure presentation shape — no
 * calculation, no location, no domain/data types. [isNext] marks the upcoming prayer.
 */
data class SidrPrayerTimeUi(
    val name: String,
    val time: String,
    val isNext: Boolean = false,
)

/**
 * Health/provenance state for the whole prayer strip (spec §8, 11 states). Never rendered by colour
 * alone — always a label + [SidrStatus] marker ([statusToken]/[label]). `CachedFresh`/`VerifiedCurrent`
 * stay calm (INFO/SUCCESS); only genuinely degraded states (`CachedStale`, `CalculationFailed`,
 * `AuthorityUnavailable`, `LocationUnavailable`, `MethodRequired`, `TimezoneConflict`) use
 * ATTENTION/DANGER.
 */
enum class SidrPrayerSummaryStatus {
    VerifiedCurrent,
    CachedFresh,
    CachedStale,
    ManualLocation,
    LocationUnavailable,
    MethodRequired,
    AuthorityUnavailable,
    TimezoneConflict,
    CalculationFailed,
    NoData,
    Updating,
}

@Composable
@ReadOnlyComposable
internal fun SidrPrayerSummaryStatus.label(): String = when (this) {
    SidrPrayerSummaryStatus.VerifiedCurrent -> sidrString(R.string.ui_prayer_status_verified)
    SidrPrayerSummaryStatus.CachedFresh -> sidrString(R.string.ui_prayer_status_cached)
    SidrPrayerSummaryStatus.CachedStale -> sidrString(R.string.ui_prayer_status_stale)
    SidrPrayerSummaryStatus.ManualLocation -> sidrString(R.string.ui_prayer_status_manual_location)
    SidrPrayerSummaryStatus.LocationUnavailable -> sidrString(R.string.ui_prayer_status_location_unavailable)
    SidrPrayerSummaryStatus.MethodRequired -> sidrString(R.string.ui_prayer_status_method_required)
    SidrPrayerSummaryStatus.AuthorityUnavailable -> sidrString(R.string.ui_prayer_status_authority_unavailable)
    SidrPrayerSummaryStatus.TimezoneConflict -> sidrString(R.string.ui_prayer_status_timezone_conflict)
    SidrPrayerSummaryStatus.CalculationFailed -> sidrString(R.string.ui_prayer_status_calculation_failed)
    SidrPrayerSummaryStatus.NoData -> sidrString(R.string.ui_prayer_status_no_data)
    SidrPrayerSummaryStatus.Updating -> sidrString(R.string.ui_prayer_status_updating)
}

internal fun SidrPrayerSummaryStatus.statusToken(): SidrStatus = when (this) {
    SidrPrayerSummaryStatus.VerifiedCurrent -> SidrStatus.SUCCESS
    SidrPrayerSummaryStatus.CachedFresh -> SidrStatus.INFO
    SidrPrayerSummaryStatus.CachedStale -> SidrStatus.ATTENTION
    SidrPrayerSummaryStatus.ManualLocation -> SidrStatus.INFO
    SidrPrayerSummaryStatus.LocationUnavailable -> SidrStatus.ATTENTION
    SidrPrayerSummaryStatus.MethodRequired -> SidrStatus.ATTENTION
    SidrPrayerSummaryStatus.AuthorityUnavailable -> SidrStatus.ATTENTION
    SidrPrayerSummaryStatus.TimezoneConflict -> SidrStatus.DANGER
    SidrPrayerSummaryStatus.CalculationFailed -> SidrStatus.DANGER
    SidrPrayerSummaryStatus.NoData -> SidrStatus.CAUTION
    SidrPrayerSummaryStatus.Updating -> SidrStatus.INFO
}

/**
 * Calm, trustworthy states ([VerifiedCurrent], [CachedFresh], [ManualLocation], [Updating]) hide their
 * status chip on the Home strip (owner request: times only). Degraded states keep a visible warning so
 * possibly-wrong, stale, or timezone-conflicting times are never presented as trustworthy (spec §2 —
 * "stale cached data must be labelled stale"; "failure must be visible").
 */
internal fun SidrPrayerSummaryStatus.isCalm(): Boolean = when (this) {
    SidrPrayerSummaryStatus.VerifiedCurrent,
    SidrPrayerSummaryStatus.CachedFresh,
    SidrPrayerSummaryStatus.ManualLocation,
    SidrPrayerSummaryStatus.Updating,
    -> true
    else -> false
}

/** fontScale at/above which the five-cell row switches to a vertical list (spec §8). */
internal const val PRAYER_SUMMARY_VERTICAL_FONT_SCALE = 1.7f

/** Decorative marker prefix on the next-prayer cell; the cell is also inverted and TalkBack-labelled. */
private const val NEXT_MARKER = "▸"

/**
 * A non-empty [prayers] schedule must always render with non-blank [provenance] — truth is never shown
 * without its source (spec §8). Extracted from the composable body so it is plain-JUnit testable without
 * a Compose rule.
 */
internal fun requirePrayerSummaryInvariant(prayers: List<SidrPrayerTimeUi>, provenance: String) {
    require(prayers.isEmpty() || provenance.isNotBlank()) {
        "a non-empty prayer schedule must always render with provenance"
    }
}

/**
 * Home prayer strip + status rendering (DS-6B Task 7, spec §8). Presentation-only: no calculation, no
 * location resolution — [prayers]/[status]/[provenance]/[locationLabel] are supplied by the caller.
 *
 * - A quiet mono row of five `NAME HH:MM` cells; the [SidrPrayerTimeUi.isNext] cell is inverted
 *   chip-style with a marker glyph + label (never colour alone). At `fontScale >= 1.7` the row becomes a
 *   vertical list instead.
 * - [status] always renders as a label + [SidrStatusMarker] chip.
 * - [provenance] (+ optional [locationLabel]) renders via [SidrProvenanceLine].
 * - Empty [prayers] is valid for no-data/failure statuses: only the status chip (+ any provenance) shows.
 * - [onOpenDetails] makes the strip a `Role.Button`; when null the strip is not clickable.
 */
@Composable
fun SidrPrayerSummary(
    prayers: List<SidrPrayerTimeUi>,
    status: SidrPrayerSummaryStatus,
    provenance: String,
    modifier: Modifier = Modifier,
    locationLabel: String? = null,
    onOpenDetails: (() -> Unit)? = null,
) {
    requirePrayerSummaryInvariant(prayers, provenance)

    val colors = SidrTheme.colors
    val statusLabel = status.label()
    val nextPrayer = prayers.firstOrNull { it.isNext }
    val summaryLabel = sidrString(R.string.ui_prayer_summary_content_description)
    val nextPrayerLabel = nextPrayer?.let {
        sidrString(R.string.ui_prayer_summary_next_prayer, it.name, it.time)
    }
    val description = buildList {
        add(summaryLabel)
        add(statusLabel)
        nextPrayerLabel?.let { add(it) }
        if (provenance.isNotBlank()) add(provenance)
        locationLabel?.let { add(it) }
    }.joinToString(", ")

    val vertical = LocalDensity.current.fontScale >= PRAYER_SUMMARY_VERTICAL_FONT_SCALE

    var strip: Modifier = modifier.fillMaxWidth()
    if (onOpenDetails != null) {
        strip = strip.clickable(
            onClickLabel = sidrString(R.string.ui_prayer_details_action_label),
            role = Role.Button,
            onClick = onOpenDetails,
        )
    }
    strip = strip.semantics { contentDescription = description }.padding(Spacing.md)

    Column(
        modifier = strip,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Calm/verified states show times only (owner request); a degraded state still shows its warning
        // label so stale/wrong times are never presented as trustworthy (spec §2). Empty schedules keep
        // the label because it is the only content they have.
        if (prayers.isEmpty() || !status.isCalm()) {
            SidrStatusMarker(status = status.statusToken(), label = statusLabel)
        }

        if (prayers.isNotEmpty()) {
            if (vertical) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    prayers.forEach { PrayerCell(prayer = it, colors = colors, modifier = Modifier.fillMaxWidth()) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    // Each cell owns an equal weighted slot so five "HH:MM" times never overlap a
                    // neighbour — they wrap within their own slot instead (spec §8 "quiet mono row").
                    prayers.forEach { PrayerCell(prayer = it, colors = colors, modifier = Modifier.weight(1f)) }
                }
            }
        }

        // Provenance is intentionally NOT drawn on the strip (owner request: times only). It is still
        // supplied by the caller (the invariant above holds), announced via the strip's contentDescription
        // for TalkBack, and shown in full on the prayer detail screen.
    }
}

@Composable
private fun PrayerCell(prayer: SidrPrayerTimeUi, colors: SidrColors, modifier: Modifier = Modifier) {
    // Times only on the strip (owner request). The prayer name is dropped from the visible text but kept
    // in each cell's contentDescription so TalkBack still announces which prayer each time belongs to.
    // DISPLAY (spoken): the prayer name is caller-supplied human copy, so it folds under the user's
    // locale like every other displayed string.
    val spoken = "${prayer.name.uppercase(Locale.getDefault())} ${prayer.time}"
    // Hoisted out of `semantics { }` below: that lambda is not inline, so it cannot read a string.
    val nextSpoken = sidrString(R.string.ui_prayer_next_cell_content_description, spoken)
    if (prayer.isNext) {
        // The next prayer stays distinguishable by a marker glyph + inverted chip, never colour alone.
        SidrText(
            text = "$NEXT_MARKER ${prayer.time}",
            role = SidrTextRole.SYSTEM,
            color = colors.ground,
            modifier = modifier
                .background(colors.text, SidrShapes.small)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .semantics { contentDescription = nextSpoken },
        )
    } else {
        SidrText(
            text = prayer.time,
            role = SidrTextRole.SYSTEM,
            color = colors.dim,
            modifier = modifier.semantics { contentDescription = spoken },
        )
    }
}
