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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.component.SectionHeader
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.TopBarIcon

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
    onLlmRouterChanged: (Boolean) -> Unit,
    onAssistantProvider: () -> Unit,
    onLearnedChoices: () -> Unit,
    onSetDefaultLauncher: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SidrScaffold(
        modifier = modifier,
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                TopBarIcon(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        },
    ) { inner ->
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                // Settings content can exceed the viewport (more so on small screens / large fonts) —
                // make it scrollable so the SYSTEM section stays reachable. Scaffold insets stay outside
                // the scroll; content padding is inside it.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            // ── Appearance ──────────────────────────────────────────────────────
            SectionHeader(text = "APPEARANCE")
            Column(Modifier.selectableGroup().fillMaxWidth()) {
                ThemeOption.ALL.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = uiState.themeName == value,
                                role = Role.RadioButton,
                                onClick = { onThemeSelected(value) },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        RadioButton(
                            selected = uiState.themeName == value,
                            onClick = null,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }

            // Accent (brand phosphor) — AIL-6 / DF-7. Applies immediately + persists.
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Accent",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "The luminous brand colour of the terminal interface.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .selectableGroup(),
                ) {
                    AccentOption.ALL.forEach { (value, label) ->
                        FilterChip(
                            selected = uiState.accentColor == value,
                            onClick = { onAccentSelected(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            // ── Suggestions ─────────────────────────────────────────────────────
            SectionHeader(text = "SUGGESTIONS")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI suggestions",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Show launcher suggestions and allow background precompute scheduling.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = uiState.aiSuggestionsEnabled,
                    onCheckedChange = onAiSuggestionsChanged,
                )
            }

            // ── Home ────────────────────────────────────────────────────────────
            SectionHeader(text = "HOME")
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "Favorites shown",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "How many most-used apps appear on the home screen.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .selectableGroup(),
                ) {
                    FAVORITES_COUNT_OPTIONS.forEach { count ->
                        FilterChip(
                            selected = uiState.favoritesCount == count,
                            onClick = { onFavoritesCountSelected(count) },
                            label = { Text(count.toString()) },
                            modifier = Modifier.semantics {
                                contentDescription = "Show $count favorites"
                            },
                        )
                    }
                }
            }
            // Usage-history opt-in — without it no launches are recorded, so the Favorites row above
            // (and usage-based suggestion ranking) stay empty. Off by default (privacy-first).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Personalize from usage",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Remember which apps you open to fill Favorites and improve suggestions.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = uiState.usageHistoryEnabled,
                    onCheckedChange = onUsageHistoryChanged,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Voice input",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Show the microphone on the search field for spoken commands.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = uiState.micInputEnabled,
                    onCheckedChange = onMicInputChanged,
                )
            }

            // ── Memory ──────────────────────────────────────────────────────────
            SectionHeader(text = "MEMORY")
            Button(
                onClick = onLearnedChoices,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(text = "[ learned choices ]")
            }

            // ── Assistant ───────────────────────────────────────────────────────
            SectionHeader(text = "ASSISTANT")
            Button(
                onClick = onAssistantProvider,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(text = "AI provider settings")
            }
            // AIL-4 — LLM Action Router opt-in. Off by default; needs a provider configured above.
            // When on, natural-language commands the rules can't handle are routed by the cloud LLM to
            // a registered action (proposals always confirm, never auto-execute).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart command routing",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Use your AI provider to understand natural-language commands. " +
                            "Suggested actions always ask before running.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = uiState.llmRouterEnabled,
                    onCheckedChange = onLlmRouterChanged,
                )
            }

            // ── Default launcher ────────────────────────────────────────────────
            SectionHeader(text = "SYSTEM")
            Button(
                onClick = onSetDefaultLauncher,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(text = "Set as default launcher")
            }

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
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
