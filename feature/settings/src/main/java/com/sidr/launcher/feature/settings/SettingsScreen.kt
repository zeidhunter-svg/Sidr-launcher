package com.sidr.launcher.feature.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.sidr.launcher.core.ui.component.SidrChoiceRow
import com.sidr.launcher.core.ui.component.SidrFilterChip
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrNavigationRow
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrToggleRow
import com.sidr.launcher.core.ui.component.SidrTopBar
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

    SettingsContent(
        uiState = uiState,
        onBack = viewModel::navigateBack,
        onThemeSelected = viewModel::setThemeName,
        onAccentSelected = viewModel::setAccentColor,
        onAiSuggestionsChanged = viewModel::setAiSuggestionsEnabled,
        onUsageHistoryChanged = viewModel::setUsageHistoryEnabled,
        onFavoritesCountSelected = viewModel::setFavoritesCount,
        onMicInputChanged = viewModel::setMicInputEnabled,
        onAlwaysShowNavBarChanged = viewModel::setAlwaysShowNavBar,
        onLlmRouterChanged = viewModel::setLlmRouterEnabled,
        onAssistantProvider = viewModel::openAssistantProvider,
        onLearnedChoices = viewModel::openLearnedChoices,
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
    onAlwaysShowNavBarChanged: (Boolean) -> Unit,
    onLlmRouterChanged: (Boolean) -> Unit,
    onAssistantProvider: () -> Unit,
    onLearnedChoices: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = "Settings",
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
            SidrSectionHeader(text = "APPEARANCE")
            ThemeOption.ALL.forEach { (value, label) ->
                SidrChoiceRow(
                    title = label,
                    selected = uiState.themeName == value,
                    onClick = { onThemeSelected(value) },
                )
            }

            // Accent (brand phosphor) — AIL-6 / DF-7. Applies immediately + persists.
            // DS-1/ADR: accent is inert (grey identity), but the stored preference key is preserved.
            SidrSectionHeader(text = "ACCENT")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                AccentOption.ALL.forEach { (value, label) ->
                    SidrFilterChip(
                        label = label,
                        selected = uiState.accentColor == value,
                        onClick = { onAccentSelected(value) },
                    )
                }
            }

            // Auto-hiding bottom nav (2026-07-12). Off = the nav bar recedes after idle and is summoned
            // via a handle (calmer Home); on = it stays pinned. Presentation-only shell behaviour.
            SidrToggleRow(
                title = "Always show navigation bar",
                checked = uiState.alwaysShowNavBar,
                onCheckedChange = onAlwaysShowNavBarChanged,
                description = "Keep the bottom bar visible at all times. When off, it hides after a few " +
                    "seconds and reappears when you tap the handle at the bottom.",
            )

            // ── Suggestions ─────────────────────────────────────────────────────
            SidrSectionHeader(text = "SUGGESTIONS")
            SidrToggleRow(
                title = "AI suggestions",
                checked = uiState.aiSuggestionsEnabled,
                onCheckedChange = onAiSuggestionsChanged,
                description = "Show launcher suggestions and allow background precompute scheduling.",
            )

            // ── Home ────────────────────────────────────────────────────────────
            SidrSectionHeader(text = "HOME")
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
                title = "Personalize from usage",
                checked = uiState.usageHistoryEnabled,
                onCheckedChange = onUsageHistoryChanged,
                description = "Remember which apps you open to fill Favorites and improve suggestions.",
            )
            SidrToggleRow(
                title = "Voice input",
                checked = uiState.micInputEnabled,
                onCheckedChange = onMicInputChanged,
                description = "Show the microphone on the search field for spoken commands.",
            )

            // ── Memory ──────────────────────────────────────────────────────────
            SidrSectionHeader(text = "MEMORY")
            SidrNavigationRow(title = "Learned choices", onClick = onLearnedChoices)

            // ── Assistant ───────────────────────────────────────────────────────
            SidrSectionHeader(text = "ASSISTANT")
            SidrNavigationRow(title = "AI provider settings", onClick = onAssistantProvider)
            // AIL-4 — LLM Action Router opt-in. Off by default; needs a provider configured above.
            // When on, natural-language commands the rules can't handle are routed by the cloud LLM to
            // a registered action (proposals always confirm, never auto-execute).
            SidrToggleRow(
                title = "Smart command routing",
                checked = uiState.llmRouterEnabled,
                onCheckedChange = onLlmRouterChanged,
                description = "Use your AI provider to understand natural-language commands. " +
                    "Suggested actions always ask before running.",
            )

            // ── Default launcher ────────────────────────────────────────────────
            SidrSectionHeader(text = "SYSTEM")
            SidrNavigationRow(title = "Set as default launcher", onClick = onSetDefaultLauncher)

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
