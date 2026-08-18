package com.sidr.launcher.feature.prayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrNavigationRow
import com.sidr.launcher.core.ui.component.SidrPrayerSummary
import com.sidr.launcher.core.ui.component.SidrPrayerSummaryStatus
import com.sidr.launcher.core.ui.component.SidrPrivacyNotice
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrPrayerTimeUi
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.prayer.Freshness
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerContext
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.prayer.TimeZoneState
import com.sidr.launcher.domain.prayer.UnavailableReason
import com.sidr.launcher.feature.prayer.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Prayer detail screen (DS-6B Task 8, spec §3/§6). Stateless render over [PrayerDetailViewModel] —
 * all five daily prayers via the (Task 7) [SidrPrayerSummary], Sunrise as a SEPARATE row explicitly
 * labelled "SUNRISE · NOT A PRAYER" (never inside the five-cell strip, never presented as a sixth
 * prayer), provenance + freshness, and method/madhab/location rows linking back to settings.
 */
@Composable
fun PrayerDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: PrayerDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    PrayerDetailContent(
        uiState = uiState,
        onBack = viewModel::navigateBack,
        onOpenSettings = viewModel::openSettings,
        modifier = modifier,
    )
}

@Composable
private fun PrayerDetailContent(
    uiState: PrayerDetailUiState,
    onBack: () -> Unit,
    onOpenSettings: (PrayerSettingsSection?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = sidrString(R.string.prayer_top_bar_title),
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = sidrString(R.string.prayer_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val context = uiState.context) {
                is PrayerContext.Unavailable -> {
                    SidrPrivacyNotice(
                        title = sidrString(R.string.prayer_detail_unavailable_title),
                        body = unavailableMessage(context.reason),
                    )
                    // Nothing is configured yet — the whole setup page is the honest destination.
                    SidrNavigationRow(
                        title = sidrString(R.string.prayer_detail_settings_row_title),
                        onClick = { onOpenSettings(null) },
                    )
                }

                is PrayerContext.Available -> {
                    SidrPrayerSummary(
                        prayers = context.toPrayerTimeUiList(uiState.tzId),
                        status = context.toSummaryStatus(),
                        provenance = context.toProvenanceText(),
                        locationLabel = context.provenance.locationLabel,
                    )

                    // Sunrise is explicitly NOT one of the five prayers (spec §3) — a separate,
                    // clearly-labelled row, never a member of the SidrPrayerSummary strip above.
                    context.schedule.sunrise?.let { sunrise ->
                        SidrText(
                            text = sidrString(
                                R.string.prayer_detail_sunrise_row,
                                sidrString(R.string.prayer_name_sunrise),
                                formatTime(sunrise.epochMillis, uiState.tzId),
                            ),
                            role = SidrTextRole.PROVENANCE,
                        )
                    }

                    if (context.timeZoneState == TimeZoneState.CONFLICT) {
                        SidrText(
                            text = sidrString(R.string.prayer_detail_timezone_conflict_warning),
                            role = SidrTextRole.PROVENANCE,
                            color = SidrTheme.colors.danger,
                        )
                    }

                    SidrSectionHeader(text = sidrString(R.string.prayer_detail_setup_section_header))
                    // Each row opens setup scoped to ITS OWN section. They used to share one
                    // argument-less route, which always landed on the method list at the top of the
                    // page, so madhab/location were below the fold and every row looked identical.
                    SidrNavigationRow(
                        title = sidrString(R.string.prayer_detail_method_row_title),
                        value = methodLabel(context.provenance.methodId),
                        onClick = { onOpenSettings(PrayerSettingsSection.METHOD) },
                    )
                    SidrNavigationRow(
                        title = sidrString(R.string.prayer_detail_madhab_row_title),
                        value = madhabLabel(context.provenance.madhab),
                        onClick = { onOpenSettings(PrayerSettingsSection.MADHAB) },
                    )
                    SidrNavigationRow(
                        title = sidrString(R.string.prayer_detail_location_row_title),
                        value = context.provenance.locationLabel,
                        onClick = { onOpenSettings(PrayerSettingsSection.LOCATION) },
                    )
                }
            }
        }
    }
}

/** Exactly the five daily prayers, in canonical display order — SUNRISE is never a member. */
private val FIVE_PRAYERS_ORDER =
    listOf(PrayerName.FAJR, PrayerName.DHUHR, PrayerName.ASR, PrayerName.MAGHRIB, PrayerName.ISHA)

@Composable
private fun PrayerContext.Available.toPrayerTimeUiList(tzId: String?): List<SidrPrayerTimeUi> =
    FIVE_PRAYERS_ORDER.map { name ->
        val instant = schedule.instants.first { it.name == name }
        SidrPrayerTimeUi(
            name = prayerNameLabel(name),
            time = formatTime(instant.epochMillis, tzId),
            isNext = nextPrayer == name,
        )
    }

@Composable
private fun prayerNameLabel(name: PrayerName): String = when (name) {
    PrayerName.FAJR -> sidrString(R.string.prayer_name_fajr)
    PrayerName.SUNRISE -> sidrString(R.string.prayer_name_sunrise)
    PrayerName.DHUHR -> sidrString(R.string.prayer_name_dhuhr)
    PrayerName.ASR -> sidrString(R.string.prayer_name_asr)
    PrayerName.MAGHRIB -> sidrString(R.string.prayer_name_maghrib)
    PrayerName.ISHA -> sidrString(R.string.prayer_name_isha)
}

private fun PrayerContext.Available.toSummaryStatus(): SidrPrayerSummaryStatus = when {
    timeZoneState == TimeZoneState.CONFLICT -> SidrPrayerSummaryStatus.TimezoneConflict
    freshness == Freshness.VERIFIED_CURRENT -> SidrPrayerSummaryStatus.VerifiedCurrent
    freshness == Freshness.CACHED_FRESH -> SidrPrayerSummaryStatus.CachedFresh
    freshness == Freshness.CACHED_STALE -> SidrPrayerSummaryStatus.CachedStale
    else -> SidrPrayerSummaryStatus.NoData
}

@Composable
private fun PrayerContext.Available.toProvenanceText(): String =
    sidrString(
        R.string.prayer_provenance_frame,
        methodLabel(provenance.methodId),
        madhabLabel(provenance.madhab),
    )

@Composable
private fun unavailableMessage(reason: UnavailableReason): String = when (reason) {
    UnavailableReason.NOT_CONFIGURED -> sidrString(R.string.prayer_detail_unavailable_not_configured)
    UnavailableReason.LOCATION_MISSING -> sidrString(R.string.prayer_detail_unavailable_location_missing)
    UnavailableReason.CALCULATION_FAILED -> sidrString(R.string.prayer_detail_unavailable_calculation_failed)
}

// methodLabel()/methodLabelResId() moved to PrayerLabels.kt (Task 7 fix-round) — shared with
// PrayerSettingsScreen so there is one place to keep in sync and one place for
// MethodLabelCoverageTest to pin against domain.prayer.SupportedPrayerMethods.ALL.

@Composable
private fun madhabLabel(madhab: Madhab): String = when (madhab) {
    Madhab.STANDARD -> sidrString(R.string.prayer_madhab_standard)
    Madhab.HANAFI -> sidrString(R.string.prayer_madhab_hanafi)
}

/** Formats an epoch millis instant as `HH:mm` in [tzId] (falling back to the device zone when the
 *  id is absent/unparseable — display-only; day-boundary math itself stays in the domain layer). */
private fun formatTime(epochMillis: Long, tzId: String?): String {
    val zone = tzId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    return Instant.ofEpochMilli(epochMillis)
        .atZone(zone)
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
}
