package com.sidr.launcher.feature.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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

    SettingsContent(
        uiState = uiState,
        onBack = viewModel::navigateBack,
        onThemeSelected = viewModel::setThemeName,
        onAiSuggestionsChanged = viewModel::setAiSuggestionsEnabled,
        onFavoritesCountSelected = viewModel::setFavoritesCount,
        onMicInputChanged = viewModel::setMicInputEnabled,
        onAssistantProvider = viewModel::openAssistantProvider,
        onSetDefaultLauncher = { launchDefaultLauncherSettings(context) },
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onThemeSelected: (String) -> Unit,
    onAiSuggestionsChanged: (Boolean) -> Unit,
    onFavoritesCountSelected: (Int) -> Unit,
    onMicInputChanged: (Boolean) -> Unit,
    onAssistantProvider: () -> Unit,
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
 * Opens the system surface for choosing the default launcher (Fork X5-C).
 *
 * API 29+ uses [RoleManager.ROLE_HOME] (`createRequestRoleIntent`) when the role is available;
 * otherwise falls back to the Home-settings screen. Best-effort: a missing handler is swallowed so
 * a tap can never crash the launcher.
 */
private fun launchDefaultLauncherSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No handler for either surface — nothing we can do; never crash the launcher.
    }
}
