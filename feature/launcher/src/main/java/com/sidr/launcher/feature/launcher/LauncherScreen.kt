package com.sidr.launcher.feature.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.component.AppTile
import com.sidr.launcher.core.ui.component.EmptyState
import com.sidr.launcher.core.ui.component.ErrorState
import com.sidr.launcher.core.ui.component.SectionHeader
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSearchField
import com.sidr.launcher.core.ui.component.TopBarIcon
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.suggestions.Suggestion

/**
 * The redesigned, decluttered home surface (Phase UX, Block X2).
 *
 * There is no full app grid here any more — the wall of icons moves to the App Drawer (Block X3).
 * Home shows only a small, useful set: a lightweight top bar with **discoverable** Settings +
 * Assistant icons, the unified [SidrSearchField] (search look, command-pipeline behaviour), the
 * existing Suggestions row (unchanged single-owner [LauncherUiState.suggestions]), a **Favorites**
 * row of the top-N most-used apps, and an **All apps** affordance. The full [LauncherUiState.apps]
 * list still loads (the drawer and suggestion resolution need it); it just isn't rendered as a grid.
 */
@Composable
fun LauncherScreen(
    modifier: Modifier = Modifier,
    viewModel: LauncherViewModel = hiltViewModel(),
    suggestionsContent: @Composable (suggestions: List<Suggestion>, onSuggestionTap: (Suggestion) -> Unit) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val commandInput by viewModel.commandInput.collectAsStateWithLifecycle()
    val feedback by viewModel.commandFeedback.collectAsStateWithLifecycle()
    val showMic by viewModel.showMic.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // First-run nudge (Block X6): whether this launcher is already the system default HOME app.
    // A runtime Android query kept in the screen (like Settings' "Set as default"); the persisted
    // "dismissed" half lives in UserPreferences (LauncherUiState.setupHintDismissed).
    val isDefaultLauncher = remember { isDefaultLauncher(context) }

    // The ROLE_HOME request must go through startActivityForResult so the permission controller can
    // read the calling package; a plain startActivity delivers a null caller and RequestRoleActivity
    // aborts ("Package name cannot be null") without showing the chooser. This launcher routes through
    // the host Activity's startActivityForResult. Result ignored — the nudge is dismissed on tap and
    // isDefaultLauncher re-reads on the next composition.
    val setDefaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* no-op */ }

    // Voice input (Block T). Tapping the mic starts recognition only when RECORD_AUDIO is held;
    // otherwise it routes to the permission-education screen for VOICE_INPUT (the Fork-5
    // education≠request flow). Framework checkSelfPermission keeps :feature:launcher dependency-free.
    val onMicTap: () -> Unit = {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.startVoiceInput()
        } else {
            viewModel.navigateTo(Routes.PermissionEducation.routeFor(PermissionFeature.VOICE_INPUT.name))
        }
    }

    SidrScaffold(
        modifier = modifier,
        topBar = {
            // Discoverable entry points — the only way to reach Settings / Assistant without typing
            // a command (both stay as typed power-user shortcuts too). Labelled for TalkBack.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                horizontalArrangement = Arrangement.End,
            ) {
                TopBarIcon(
                    icon = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    onClick = { viewModel.navigateTo(Routes.Settings.ROUTE) },
                )
                TopBarIcon(
                    painter = painterResource(R.drawable.ic_assistant_24),
                    contentDescription = "Assistant",
                    onClick = { viewModel.navigateTo(Routes.Assistant.ROUTE) },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            // Unified search + command field (Block X1). Submit drives the existing command pipeline
            // byte-for-byte; live app filtering lands in Block X4.
            SidrSearchField(
                value = commandInput,
                onValueChange = viewModel::onCommandChanged,
                onSubmit = viewModel::onCommandSubmitted,
                showMic = showMic,
                onMic = onMicTap,
            )

            // Command feedback — fallback UI for the last submitted command, shown just under the field.
            CommandFeedbackArea(
                feedback = feedback,
                onCandidateClick = viewModel::onAppClicked,
                onDismiss = viewModel::dismissFeedback,
            )

            Box(modifier = Modifier.weight(1f)) {
                when (val state = uiState) {
                    is UiState.Loading -> LoadingContent()
                    is UiState.Empty -> EmptyState(message = "No apps found")
                    is UiState.Error -> ErrorState(
                        message = errorMessage(state.error),
                        onRetry = if (state.retryable) viewModel::retry else null,
                    )
                    is UiState.Success -> HomeContent(
                        state = state.data,
                        onAppClick = viewModel::onAppClicked,
                        onSuggestionTap = viewModel::onSuggestionClicked,
                        onAllApps = { viewModel.navigateTo(Routes.AppDrawer.ROUTE) },
                        showSetupHint = !isDefaultLauncher && !state.data.setupHintDismissed,
                        onSetDefault = {
                            viewModel.dismissSetupHint()
                            try {
                                setDefaultLauncher.launch(defaultLauncherIntent(context))
                            } catch (_: ActivityNotFoundException) {
                                // No handler — never crash the launcher.
                            }
                        },
                        onDismissHint = viewModel::dismissSetupHint,
                        suggestionsContent = suggestionsContent,
                    )
                }
            }
        }
    }
}

// ── Home content (no full grid — Block X2) ──────────────────────────────────

@Composable
private fun HomeContent(
    state: LauncherUiState,
    onAppClick: (InstalledApp) -> Unit,
    onSuggestionTap: (Suggestion) -> Unit,
    onAllApps: () -> Unit,
    showSetupHint: Boolean,
    onSetDefault: () -> Unit,
    onDismissHint: () -> Unit,
    suggestionsContent: @Composable (suggestions: List<Suggestion>, onSuggestionTap: (Suggestion) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        if (showSetupHint) {
            SetupNudge(onSetDefault = onSetDefault, onDismiss = onDismissHint)
        }
        if (state.suggestions.isNotEmpty()) {
            suggestionsContent(state.suggestions, onSuggestionTap)
        }
        if (state.favorites.isNotEmpty()) {
            SectionHeader(text = "Favorites")
            FavoritesRow(favorites = state.favorites, onAppClick = onAppClick)
        }
        // Push the All apps affordance to the bottom of the (small) home surface.
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            onClick = onAllApps,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = Spacing.md),
        ) {
            Text(text = "All apps")
        }
    }
}

/**
 * First-run nudge (Block X6): a dismissible card prompting the user to make Sidr the default
 * launcher, plus a one-line "type or search" hint. Shown only until the user acts or dismisses
 * (the choice persists via [UserPreferences.setupHintDismissed]).
 */
@Composable
private fun SetupNudge(
    onSetDefault: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Make Sidr your home screen",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                TopBarIcon(
                    icon = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    onClick = onDismiss,
                )
            }
            Text(
                text = "Set Sidr as your default launcher, then type or search from the field above to open apps.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            TextButton(
                onClick = onSetDefault,
                modifier = Modifier.padding(top = Spacing.sm),
            ) {
                Text(text = "Set as default")
            }
        }
    }
}

@Composable
private fun FavoritesRow(
    favorites: List<InstalledApp>,
    onAppClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(favorites, key = { it.packageName }) { app ->
            AppTile(
                label = app.label,
                onClick = { onAppClick(app) },
                icon = { AppTileIcon(app) },
            )
        }
    }
}

/**
 * Icon slot for an [AppTile]: loads the app's launcher icon (a `PackageManager`/`Drawable` concern
 * that belongs to this feature module, not `core/ui`), falling back to a coloured monogram box.
 * Decorative — the enclosing [AppTile] already carries the app label as its content description.
 */
@Composable
private fun AppTileIcon(app: InstalledApp) {
    val icon by rememberAppIcon(app.packageName)
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = Modifier.size(Sizes.appIcon),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Sizes.appIcon)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ),
        ) {
            Text(
                text = app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ── Command feedback ────────────────────────────────────────────────────────

@Composable
private fun CommandFeedbackArea(
    feedback: CommandFeedback,
    onCandidateClick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (feedback) {
        CommandFeedback.None -> Unit

        is CommandFeedback.Message -> FeedbackText(
            text = feedback.text,
            onDismiss = onDismiss,
            modifier = modifier,
        )

        is CommandFeedback.Suggestion -> FeedbackText(
            text = feedback.text,
            onDismiss = onDismiss,
            modifier = modifier,
        )

        is CommandFeedback.Ambiguous -> Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Did you mean:",
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            feedback.candidates.forEach { app ->
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCandidateClick(app) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FeedbackText(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// ── State surfaces ─────────────────────────────────────────────────────────
// Empty/Error now render through core/ui EmptyState/ErrorState (Block X6); only the launcher-local
// Loading spinner remains here.

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        CircularProgressIndicator()
    }
}

/** Display-safe message for a [UiError] on the home error surface. */
private fun errorMessage(error: UiError): String = when (error) {
    is UiError.Message -> error.text
    UiError.Network -> "Network error — check your connection"
    UiError.Unknown -> "Something went wrong"
}

// ── Default-launcher helpers (Block X6, first-run nudge) ─────────────────────

/**
 * Whether this app currently holds the default HOME role. API 29+ uses [RoleManager.isRoleHeld];
 * older releases resolve the HOME intent and compare the winning package. Best-effort: any failure
 * is treated as "not default" so the nudge can still surface.
 */
private fun isDefaultLauncher(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager != null &&
            roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    } else {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        resolved?.activityInfo?.packageName == context.packageName
    }
} catch (_: Exception) {
    false
}

/**
 * Builds the intent for the system default-launcher surface. Mirrors the Settings screen helper
 * (Fork X5-C): [RoleManager.ROLE_HOME] request on API 29+ when available, else Home settings. The
 * caller launches it through an `ActivityResultContracts.StartActivityForResult` launcher — a role
 * request delivered via a plain `startActivity` arrives with a null calling package and is rejected
 * by the system's RequestRoleActivity.
 */
private fun defaultLauncherIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
            roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            Intent(Settings.ACTION_HOME_SETTINGS)
        }
    } else {
        Intent(Settings.ACTION_HOME_SETTINGS)
    }
