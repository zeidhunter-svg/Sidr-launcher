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
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Freshness
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerContext
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.prayer.SupportedPrayerMethods
import com.sidr.launcher.domain.prayer.TimeZoneState
import com.sidr.launcher.domain.prayer.UnavailableReason
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
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = "Prayer times",
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
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
                        title = "No prayer times yet",
                        body = unavailableMessage(context.reason),
                    )
                    SidrNavigationRow(title = "Prayer settings", onClick = onOpenSettings)
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
                            text = "SUNRISE · NOT A PRAYER  ${formatTime(sunrise.epochMillis, uiState.tzId)}",
                            role = SidrTextRole.PROVENANCE,
                        )
                    }

                    if (context.timeZoneState == TimeZoneState.CONFLICT) {
                        SidrText(
                            text = "Your device's timezone differs from the prayer location's " +
                                "timezone — times below use the LOCATION's timezone.",
                            role = SidrTextRole.PROVENANCE,
                            color = SidrTheme.colors.danger,
                        )
                    }

                    SidrSectionHeader(text = "SETUP")
                    SidrNavigationRow(
                        title = "Method",
                        value = methodLabel(context.provenance.methodId),
                        onClick = onOpenSettings,
                    )
                    SidrNavigationRow(
                        title = "Madhab",
                        value = madhabLabel(context.provenance.madhab),
                        onClick = onOpenSettings,
                    )
                    SidrNavigationRow(
                        title = "Location",
                        value = context.provenance.locationLabel,
                        onClick = onOpenSettings,
                    )
                }
            }
        }
    }
}

/** Exactly the five daily prayers, in canonical display order — SUNRISE is never a member. */
private val FIVE_PRAYERS_ORDER =
    listOf(PrayerName.FAJR, PrayerName.DHUHR, PrayerName.ASR, PrayerName.MAGHRIB, PrayerName.ISHA)

private fun PrayerContext.Available.toPrayerTimeUiList(tzId: String?): List<SidrPrayerTimeUi> =
    FIVE_PRAYERS_ORDER.map { name ->
        val instant = schedule.instants.first { it.name == name }
        SidrPrayerTimeUi(
            name = name.name,
            time = formatTime(instant.epochMillis, tzId),
            isNext = nextPrayer == name,
        )
    }

private fun PrayerContext.Available.toSummaryStatus(): SidrPrayerSummaryStatus = when {
    timeZoneState == TimeZoneState.CONFLICT -> SidrPrayerSummaryStatus.TimezoneConflict
    freshness == Freshness.VERIFIED_CURRENT -> SidrPrayerSummaryStatus.VerifiedCurrent
    freshness == Freshness.CACHED_FRESH -> SidrPrayerSummaryStatus.CachedFresh
    freshness == Freshness.CACHED_STALE -> SidrPrayerSummaryStatus.CachedStale
    else -> SidrPrayerSummaryStatus.NoData
}

private fun PrayerContext.Available.toProvenanceText(): String =
    "local calculation · ${methodLabel(provenance.methodId)} · ${madhabLabel(provenance.madhab)}"

private fun unavailableMessage(reason: UnavailableReason): String = when (reason) {
    UnavailableReason.NOT_CONFIGURED ->
        "Choose a calculation method and madhab to start seeing prayer times."
    UnavailableReason.LOCATION_MISSING ->
        "Method and madhab are set — pick a city or use device location to see prayer times."
    UnavailableReason.CALCULATION_FAILED ->
        "Prayer times couldn't be calculated for your current setup. Try again, or check your " +
            "location and method."
}

private fun methodLabel(methodId: CalculationMethodId): String =
    SupportedPrayerMethods.ALL.firstOrNull { it.id == methodId }?.displayLabel ?: methodId.key

private fun madhabLabel(madhab: Madhab): String = when (madhab) {
    Madhab.STANDARD -> "Standard (Shafi'i / Maliki / Hanbali)"
    Madhab.HANAFI -> "Hanafi"
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
