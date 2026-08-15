package com.sidr.launcher.feature.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.sidr.launcher.core.ui.component.SidrChoiceRow
import com.sidr.launcher.core.ui.component.SidrFilterChip
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrNavigationRow
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrToggleRow
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.theme.SidrTheme

/**
 * Real launcher settings surface (Block X5). Stateless render over [SettingsViewModel]; built on the
 * shared `core:ui` primitives so it reads like the drawer/home. Back + Assistant navigation flow
 * through the VM's [NavigationEvent] channel (collected by the app NavHost); "Set as default" is a
 * pure Android intent fired from here via [LocalContext] (Fork X5-C — no new port).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // A ROLE_HOME request MUST be launched via startActivityForResult so the permission controller can
    // read the calling package; a plain startActivity delivers a null caller and RequestRoleActivity
    // aborts ("Package name cannot be null") without ever showing the chooser. This launcher routes
    // through the host Activity's startActivityForResult. The result is ignored — the default-launcher
    // state is re-read when the surface next resumes.
    val setDefaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* no-op */ }

    // I18N-1 Task 14: AppCompatDelegate is the single reader AND writer of the per-app language - no
    // DataStore key, no ViewModel field, no mutableStateOf mirror. A locale change recreates this
    // Activity, which rebuilds this composition, so a plain read on every composition never goes stale.
    val currentLanguageTag = AppCompatDelegate.getApplicationLocales()
        .toLanguageTags()
        .takeIf { it.isNotBlank() }
        ?.substringBefore('-')
        ?: ""

    SettingsContent(
        uiState = uiState,
        onBack = viewModel::navigateBack,
        onThemeSelected = viewModel::setThemeName,
        onAccentSelected = viewModel::setAccentColor,
        onAiSuggestionsChanged = viewModel::setAiSuggestionsEnabled,
        onUsageHistoryChanged = viewModel::setUsageHistoryEnabled,
        onFavoritesCountSelected = viewModel::setFavoritesCount,
        onMicInputChanged = viewModel::setMicInputEnabled,
        onAutoHideNavBarChanged = viewModel::setAutoHideNavBar,
        currentLanguageTag = currentLanguageTag,
        onLanguageSelected = { tag ->
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        },
        onLlmRouterChanged = viewModel::setLlmRouterEnabled,
        onAssistantProvider = viewModel::openAssistantProvider,
        onLearnedChoices = viewModel::openLearnedChoices,
        onAliases = viewModel::openAliases,
        onPrayerSettings = viewModel::openPrayerSettings,
        onSetDefaultLauncher = {
            try {
                setDefaultLauncher.launch(defaultLauncherIntent(context))
            } catch (_: ActivityNotFoundException) {
                // No handler for either surface — never crash the launcher.
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onThemeSelected: (String) -> Unit,
    onAccentSelected: (String) -> Unit,
    onAiSuggestionsChanged: (Boolean) -> Unit,
    onUsageHistoryChanged: (Boolean) -> Unit,
    onFavoritesCountSelected: (Int) -> Unit,
    onMicInputChanged: (Boolean) -> Unit,
    onAutoHideNavBarChanged: (Boolean) -> Unit,
    currentLanguageTag: String,
    onLanguageSelected: (String) -> Unit,
    onLlmRouterChanged: (Boolean) -> Unit,
    onAssistantProvider: () -> Unit,
    onLearnedChoices: () -> Unit,
    onAliases: () -> Unit,
    onPrayerSettings: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = sidrString(R.string.settings_top_bar_title),
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = sidrString(R.string.settings_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { inner ->
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                // Settings content can exceed the viewport (more so on small screens / large fonts) —
                // make it scrollable so the SYSTEM section stays reachable. Scaffold insets stay outside
                // the scroll; content padding is inside it.
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            // ── Appearance ──────────────────────────────────────────────────────
            SidrSectionHeader(text = sidrString(R.string.settings_section_appearance))
            ThemeOption.ALL.forEach { (value, _) ->
                SidrChoiceRow(
                    title = themeLabel(value),
                    selected = uiState.themeName == value,
                    onClick = { onThemeSelected(value) },
                )
            }

            // Accent (brand phosphor) — AIL-6 / DF-7. Applies immediately + persists.
            // DS-1/ADR: accent is inert (grey identity), but the stored preference key is preserved.
            SidrSectionHeader(text = sidrString(R.string.settings_section_accent))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AccentOption.ALL.forEach { (value, _) ->
                    SidrFilterChip(
                        label = accentLabel(value),
                        selected = uiState.accentColor == value,
                        onClick = { onAccentSelected(value) },
                    )
                }
            }

            // Auto-hiding bottom nav (2026-07-12; inverted by DS-11 A1 2026-08-10). Off (the default)
            // = the bar stays pinned; on = it recedes after idle and is summoned via a handle. The
            // toggle is now named for the opt-in behaviour rather than for keeping the default on.
            // Presentation-only shell behaviour.
            SidrToggleRow(
                title = sidrString(R.string.settings_auto_hide_nav_title),
                checked = uiState.autoHideNavBar,
                onCheckedChange = onAutoHideNavBarChanged,
                description = sidrString(R.string.settings_auto_hide_nav_description),
            )

            // ── Language ────────────────────────────────────────────────────────
            // I18N-1 Task 14: AppCompatDelegate is the only writer (Step in SettingsScreen above) - no
            // persisted preference key, so this section can never disagree with the system's own
            // per-app language picker (Settings > Apps > Sidr > Language).
            SidrSectionHeader(text = sidrString(R.string.settings_section_language))
            listOf(
                "" to R.string.settings_language_system,
                "en" to R.string.settings_language_en,
                "ru" to R.string.settings_language_ru,
                "tr" to R.string.settings_language_tr,
            ).forEach { (tag, labelRes) ->
                SidrChoiceRow(
                    title = sidrString(labelRes),
                    selected = currentLanguageTag == tag,
                    onClick = { onLanguageSelected(tag) },
                )
            }

            // ── Suggestions ─────────────────────────────────────────────────────
            SidrSectionHeader(text = sidrString(R.string.settings_section_suggestions))
            SidrToggleRow(
                title = sidrString(R.string.settings_ai_suggestions_title),
                checked = uiState.aiSuggestionsEnabled,
                onCheckedChange = onAiSuggestionsChanged,
                description = sidrString(R.string.settings_ai_suggestions_description),
            )

            // ── Home ────────────────────────────────────────────────────────────
            SidrSectionHeader(text = sidrString(R.string.settings_section_home))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                FAVORITES_COUNT_OPTIONS.forEach { count ->
                    SidrFilterChip(
                        label = count.toString(),
                        selected = uiState.favoritesCount == count,
                        onClick = { onFavoritesCountSelected(count) },
                    )
                }
            }
            // Usage-history opt-in — without it no launches are recorded, so the Favorites row above
            // (and usage-based suggestion ranking) stay empty. Off by default (privacy-first).
            SidrToggleRow(
                title = sidrString(R.string.settings_personalize_usage_title),
                checked = uiState.usageHistoryEnabled,
                onCheckedChange = onUsageHistoryChanged,
                description = sidrString(R.string.settings_personalize_usage_description),
            )
            SidrToggleRow(
                title = sidrString(R.string.settings_voice_input_title),
                checked = uiState.micInputEnabled,
                onCheckedChange = onMicInputChanged,
                description = sidrString(R.string.settings_voice_input_description),
            )

            // ── Prayer ──────────────────────────────────────────────────────────
            SidrSectionHeader(text = sidrString(R.string.settings_section_prayer))
            SidrNavigationRow(title = sidrString(R.string.settings_prayer_times_title), onClick = onPrayerSettings)

            // ── Memory ──────────────────────────────────────────────────────────
            SidrSectionHeader(text = sidrString(R.string.settings_section_memory))
            SidrNavigationRow(title = sidrString(R.string.settings_learned_choices_title), onClick = onLearnedChoices)
            SidrNavigationRow(title = sidrString(R.string.settings_aliases_title), onClick = onAliases)

            // ── Assistant ───────────────────────────────────────────────────────
            SidrSectionHeader(text = sidrString(R.string.settings_section_assistant))
            SidrNavigationRow(title = sidrString(R.string.settings_ai_provider_title), onClick = onAssistantProvider)
            // AIL-4 — LLM Action Router opt-in. Off by default; needs a provider configured above.
            // When on, natural-language commands the rules can't handle are routed by the cloud LLM to
            // a registered action (proposals always confirm, never auto-execute).
            SidrToggleRow(
                title = sidrString(R.string.settings_smart_routing_title),
                checked = uiState.llmRouterEnabled,
                onCheckedChange = onLlmRouterChanged,
                description = sidrString(R.string.settings_smart_routing_description),
            )

            // ── Default launcher ────────────────────────────────────────────────
            SidrSectionHeader(text = sidrString(R.string.settings_section_system))
            SidrNavigationRow(title = sidrString(R.string.settings_set_default_launcher_title), onClick = onSetDefaultLauncher)

            uiState.errorMessage?.let { message ->
                com.sidr.launcher.core.ui.primitive.SidrText(
                    text = message,
                    role = com.sidr.launcher.core.ui.primitive.SidrTextRole.PROVENANCE,
                    color = SidrTheme.colors.danger,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/**
 * Builds the intent for the system default-launcher surface (Fork X5-C).
 *
 * API 29+ uses [RoleManager.ROLE_HOME] (`createRequestRoleIntent`) when the role is available **and not
 * already held**; otherwise falls back to the Home-settings picker. Requesting a role you already hold
 * returns `RESULT_CANCELED` with no UI, so once Sidr is the default launcher the request would silently
 * no-op and the user could never switch away — [Settings.ACTION_HOME_SETTINGS] opens the changeable
 * Home-app picker instead. The caller launches it through an `ActivityResultContracts.StartActivityForResult`
 * launcher — a role request delivered via a plain `startActivity` arrives with a null calling package and
 * the system's RequestRoleActivity rejects it.
 */
private fun defaultLauncherIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        ) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS)
    }
