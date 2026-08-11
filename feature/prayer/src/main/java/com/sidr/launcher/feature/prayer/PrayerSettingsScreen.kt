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
            // ── Method (mandatory, no default) ─────────────────────────────────
            SidrSectionHeader(text = sidrString(R.string.prayer_settings_method_section_header))
            uiState.methodOptions.forEach { method ->
                SidrChoiceRow(
                    title = methodLabel(method.id),
                    selected = uiState.selectedMethod == method.id,
                    onClick = { onMethodSelected(method.id) },
                )
            }

            // ── Madhab (mandatory, no default) ─────────────────────────────────
            SidrSectionHeader(text = sidrString(R.string.prayer_settings_madhab_section_header))
            uiState.madhabOptions.forEach { madhab ->
                SidrChoiceRow(
                    title = madhabLabel(madhab),
                    selected = uiState.selectedMadhab == madhab,
                    onClick = { onMadhabSelected(madhab) },
                )
            }

            // ── Location (optional; manual city path needs zero permission) ────
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
                        location.label,
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

            uiState.statusMessage?.let { message ->
                SidrText(
                    text = message,
                    role = SidrTextRole.PROVENANCE,
                    color = SidrTheme.colors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ── Destructive actions, gated by SidrActionGate ────────────────────
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

@Composable
private fun methodLabel(methodId: CalculationMethodId): String = when (methodId.key) {
    "MWL" -> sidrString(R.string.prayer_method_mwl)
    "EGYPTIAN" -> sidrString(R.string.prayer_method_egyptian)
    "KARACHI" -> sidrString(R.string.prayer_method_karachi)
    "UMM_AL_QURA" -> sidrString(R.string.prayer_method_umm_al_qura)
    "DUBAI" -> sidrString(R.string.prayer_method_dubai)
    "MOON_SIGHTING_COMMITTEE" -> sidrString(R.string.prayer_method_moon_sighting_committee)
    "NORTH_AMERICA" -> sidrString(R.string.prayer_method_north_america)
    "KUWAIT" -> sidrString(R.string.prayer_method_kuwait)
    "QATAR" -> sidrString(R.string.prayer_method_qatar)
    "SINGAPORE" -> sidrString(R.string.prayer_method_singapore)
    "TURKEY" -> sidrString(R.string.prayer_method_turkey)
    else -> methodId.key
}

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
