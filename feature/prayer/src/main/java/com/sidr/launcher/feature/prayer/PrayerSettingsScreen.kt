package com.sidr.launcher.feature.prayer

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.sidr.launcher.core.ui.component.SidrActionGate
import com.sidr.launcher.core.ui.component.SidrActionGateType
import com.sidr.launcher.core.ui.component.SidrChoiceRow
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrNavigationRow
import com.sidr.launcher.core.ui.component.SidrPrimaryButton
import com.sidr.launcher.core.ui.component.SidrPrivacyNotice
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSearchField
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.feature.prayer.R

/**
 * Prayer setup screen (DS-6B Task 8, spec §0.1/§0.2/§0.4/§0.5). Stateless render over
 * [PrayerSettingsViewModel] — the VM owns every decision; this file is presentation only.
 *
 * Method and madhab selectors are explicit radio rows with NO preselected default. City search
 * runs over the injected `CityIndex` with zero permissions. The "Use device location" button owns
 * the permission gate itself (the VM has none): a live [ContextCompat.checkSelfPermission] read
 * decides whether to call [PrayerSettingsViewModel.useDeviceLocation] directly (already granted —
 * skips a redundant education screen, the Block-T mic-affordance precedent) or route to the
 * permission-education flow first ([PrayerSettingsViewModel.openPermissionEducation]).
 */
@Composable
fun PrayerSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: PrayerSettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    PrayerSettingsContent(
        uiState = uiState,
        onBack = viewModel::navigateBack,
        onMethodSelected = viewModel::onMethodSelected,
        onMadhabSelected = viewModel::onMadhabSelected,
        onCityQueryChanged = viewModel::onCitySearchQueryChanged,
        onCitySelected = viewModel::onCitySelected,
        onUseDeviceLocation = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.useDeviceLocation() else viewModel.openPermissionEducation()
        },
        onClearLocation = viewModel::clearLocation,
        onClearSetup = viewModel::clearSetup,
        modifier = modifier,
    )
}

@Composable
private fun PrayerSettingsContent(
    uiState: PrayerSettingsUiState,
    onBack: () -> Unit,
    onMethodSelected: (CalculationMethodId) -> Unit,
    onMadhabSelected: (Madhab) -> Unit,
    onCityQueryChanged: (String) -> Unit,
    onCitySelected: (PrayerLocation) -> Unit,
    onUseDeviceLocation: () -> Unit,
    onClearLocation: () -> Unit,
    onClearSetup: () -> Unit,
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
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // `section == null` is the whole setup page (Settings entry point + first run); a
            // non-null section is a deep link from the detail screen's Method/Madhab/Location rows
            // and renders ONLY that section, so each of those rows lands somewhere visibly its own.
            val section = uiState.section

            if (section == null || section == PrayerSettingsSection.METHOD) {
                MethodSection(
                    uiState = uiState,
                    onMethodSelected = onMethodSelected,
                )
            }

            if (section == null || section == PrayerSettingsSection.MADHAB) {
                MadhabSection(
                    uiState = uiState,
                    onMadhabSelected = onMadhabSelected,
                )
            }

            if (section == null || section == PrayerSettingsSection.LOCATION) {
                LocationSection(
                    uiState = uiState,
                    onCityQueryChanged = onCityQueryChanged,
                    onCitySelected = onCitySelected,
                    onUseDeviceLocation = onUseDeviceLocation,
                )
            }

            uiState.statusMessage?.let { message ->
                SidrText(
                    text = message,
                    role = SidrTextRole.PROVENANCE,
                    color = SidrTheme.colors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ── Destructive actions, gated by SidrActionGate ────────────────────
            // Whole-page only: "clear setup" wipes method AND madhab AND location, so it must not
            // sit inside a view that shows just one of them.
            if (section == null) {
                if (uiState.location != null || uiState.isConfigured) {
                    SidrSectionHeader(text = sidrString(R.string.prayer_settings_reset_section_header))
                }
                if (uiState.location != null) {
                    DestructiveRow(
                        rowTitle = sidrString(R.string.prayer_settings_clear_location_action),
                        gateTitle = sidrString(R.string.prayer_settings_clear_location_action),
                        consequence = sidrString(R.string.prayer_settings_clear_location_consequence),
                        confirmLabel = sidrString(R.string.prayer_settings_clear_location_action),
                        onConfirmed = onClearLocation,
                    )
                }
                if (uiState.isConfigured) {
                    DestructiveRow(
                        rowTitle = sidrString(R.string.prayer_settings_clear_setup_action),
                        gateTitle = sidrString(R.string.prayer_settings_clear_setup_action),
                        consequence = sidrString(R.string.prayer_settings_clear_setup_consequence),
                        confirmLabel = sidrString(R.string.prayer_settings_clear_setup_confirm_label),
                        onConfirmed = onClearSetup,
                    )
                }
            }
        }
    }
}

/** Method (mandatory, no default) — spec §0.1. */
@Composable
private fun MethodSection(
    uiState: PrayerSettingsUiState,
    onMethodSelected: (CalculationMethodId) -> Unit,
) {
    SidrSectionHeader(text = sidrString(R.string.prayer_settings_method_section_header))
    uiState.methodOptions.forEach { method ->
        SidrChoiceRow(
            title = methodLabel(method.id),
            selected = uiState.selectedMethod == method.id,
            onClick = { onMethodSelected(method.id) },
        )
    }
}

/** Madhab (mandatory, no default) — spec §0.2. */
@Composable
private fun MadhabSection(
    uiState: PrayerSettingsUiState,
    onMadhabSelected: (Madhab) -> Unit,
) {
    SidrSectionHeader(text = sidrString(R.string.prayer_settings_madhab_section_header))
    uiState.madhabOptions.forEach { madhab ->
        SidrChoiceRow(
            title = madhabLabel(madhab),
            selected = uiState.selectedMadhab == madhab,
            onClick = { onMadhabSelected(madhab) },
        )
    }
}

/** Location (optional; the manual city path needs zero permission) — spec §0.4/§0.5. */
@Composable
private fun LocationSection(
    uiState: PrayerSettingsUiState,
    onCityQueryChanged: (String) -> Unit,
    onCitySelected: (PrayerLocation) -> Unit,
    onUseDeviceLocation: () -> Unit,
) {
    SidrSectionHeader(text = sidrString(R.string.prayer_settings_location_section_header))
    SidrSearchField(
        value = uiState.citySearchQuery,
        onValueChange = onCityQueryChanged,
        onSubmit = {},
        placeholder = sidrString(R.string.prayer_settings_city_search_placeholder),
    )
    uiState.citySearchResults.forEach { city ->
        SidrNavigationRow(
            title = city.label,
            description = city.tzId,
            onClick = { onCitySelected(city) },
        )
    }
    uiState.location?.let { location ->
        SidrText(
            text = sidrString(
                R.string.prayer_settings_current_location,
                locationLabelText(location),
                locationSourceLabel(location.source),
            ),
            role = SidrTextRole.PROVENANCE,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
    SidrPrimaryButton(
        text = if (uiState.isResolvingDeviceLocation) {
            sidrString(R.string.prayer_settings_locating_button)
        } else {
            sidrString(R.string.prayer_settings_use_device_location_button)
        },
        onClick = onUseDeviceLocation,
        enabled = !uiState.isResolvingDeviceLocation,
        loading = uiState.isResolvingDeviceLocation,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )

    // Local-only, never-sent privacy notice (spec §0.5 / DS-5 SidrPrivacyNotice).
    SidrPrivacyNotice(
        title = sidrString(R.string.prayer_settings_privacy_title),
        body = sidrString(R.string.prayer_settings_privacy_body),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun DestructiveRow(
    rowTitle: String,
    gateTitle: String,
    consequence: String,
    confirmLabel: String,
    onConfirmed: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        SidrActionGate(
            type = SidrActionGateType.Destructive,
            title = gateTitle,
            consequence = consequence,
            confirmLabel = confirmLabel,
            onConfirm = {
                confirming = false
                onConfirmed()
            },
            onCancel = { confirming = false },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else {
        SidrNavigationRow(title = rowTitle, onClick = { confirming = true })
    }
}

// methodLabel()/methodLabelResId() moved to PrayerLabels.kt (Task 7 fix-round) — shared with
// PrayerDetailScreen so there is one place to keep in sync and one place for
// MethodLabelCoverageTest to pin against domain.prayer.SupportedPrayerMethods.ALL.

@Composable
private fun madhabLabel(madhab: Madhab): String = when (madhab) {
    Madhab.STANDARD -> sidrString(R.string.prayer_madhab_standard)
    Madhab.HANAFI -> sidrString(R.string.prayer_madhab_hanafi)
}

@Composable
private fun locationSourceLabel(source: PrayerLocationSource): String = when (source) {
    PrayerLocationSource.CITY -> sidrString(R.string.prayer_location_source_city)
    PrayerLocationSource.DEVICE -> sidrString(R.string.prayer_location_source_device)
}

/**
 * Resolves [PrayerLocation.label] for display (I18N-2). A THIRD leak site alongside
 * `PrayerDetailScreen.kt`'s: this "Current: %1$s (%2$s)" line already translated the parenthetical
 * source via [locationSourceLabel], but was still splicing the raw `location.label` in front of
 * it — "Текущее: Current location (местоположение устройства)" in `ru`. Same render-time seam as
 * [locationSourceLabel] / [madhabLabel]; the stored identity value is untouched (see
 * `AndroidPrayerLocationProvider.DEVICE_LOCATION_LABEL`'s kdoc).
 */
@Composable
private fun locationLabelText(location: PrayerLocation): String =
    if (location.source == PrayerLocationSource.DEVICE) {
        sidrString(R.string.prayer_location_current_device)
    } else {
        location.label
    }
