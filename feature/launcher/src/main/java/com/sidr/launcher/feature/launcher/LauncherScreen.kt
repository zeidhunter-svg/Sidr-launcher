package com.sidr.launcher.feature.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.ui.component.AppTile
import com.sidr.launcher.core.ui.component.CommandBar
import com.sidr.launcher.core.ui.component.CommandBarItem
import com.sidr.launcher.core.ui.component.ConfirmActionCard
import com.sidr.launcher.core.ui.component.EmptyState
import com.sidr.launcher.core.ui.component.ErrorState
import com.sidr.launcher.core.ui.component.RouteChip
import com.sidr.launcher.core.ui.component.RouteChipRow
import com.sidr.launcher.core.ui.component.SectionHeader
import com.sidr.launcher.core.ui.component.SidrCommandPrompt
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.TopBarIcon
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.suggestions.Suggestion
import kotlinx.coroutines.delay

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
    val inputResults by viewModel.inputResults.collectAsStateWithLifecycle()
    val pendingRoutedAction by viewModel.pendingRoutedAction.collectAsStateWithLifecycle()
    val devConsoleOn by viewModel.devConsoleOn.collectAsStateWithLifecycle()
    val consoleLines by viewModel.consoleLines.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // AIL-6 status-line clock — ticks within the minute; screen-local (time is a pure UI concern).
    var currentTime by remember { mutableStateOf(currentHhMm()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = currentHhMm()
            delay(20_000L)
        }
    }

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
            // Top bar = identity (SIDR//) + a terminal status line (AIL-6). The discoverable Settings /
            // Assistant entry points moved to the bottom CommandBar (both stay typed shortcuts too).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Wordmark + hidden dev-mode arm: 7 rapid taps arm the Command console (DF-1).
                var tapCount by remember { androidx.compose.runtime.mutableStateOf(0) }
                var lastTap by remember { androidx.compose.runtime.mutableStateOf(0L) }
                Text(
                    text = "SIDR//",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val now = System.currentTimeMillis()
                            tapCount = if (now - lastTap < 3000L) tapCount + 1 else 1
                            lastTap = now
                            if (tapCount >= 7) {
                                tapCount = 0
                                viewModel.armDevMode()
                            }
                        },
                )
                // AIL-6: terminal status line instead of Material icons. Settings/Assistant moved to
                // the bottom CommandBar; the top bar now carries identity (SIDR//) + system state.
                HomeStatus(isOnline = isOnline, time = currentTime)
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
            SidrCommandPrompt(
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

            // AIL-5: a router-proposed action awaiting the user's go-ahead. CONFIRM-risk → the DF-4
            // confirm card; SAFE → a one-tap accelerator. Neither auto-executes (R4). Confirm routes
            // through the education flow first when the action declares a permission gate (inert in the
            // MVP catalog — no family gates — but wired so a future gated family is safe by design).
            pendingRoutedAction?.let { pending ->
                val onConfirm: () -> Unit = {
                    val gate = pending.permissionGate
                    if (gate != null) {
                        viewModel.navigateTo(Routes.PermissionEducation.routeFor(gate.name))
                    } else {
                        viewModel.confirmRoutedAction()
                    }
                }
                PendingActionArea(
                    pending = pending,
                    onConfirm = onConfirm,
                    onCancel = viewModel::cancelRoutedAction,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    // Hidden developer transcript (DF-1) — takes over the body while on.
                    devConsoleOn -> CommandConsole(lines = consoleLines)
                    // "Search overtakes": a non-blank buffer replaces the home body with results.
                    inputResults.active -> InputResultsPanel(
                        results = inputResults,
                        onAppClick = viewModel::onAppClicked,
                        onWeb = { viewModel.submitWebSearch(commandInput) },
                        onSite = { viewModel.submitSite(commandInput) },
                        onAsk = {
                            viewModel.navigateTo(Routes.Assistant.routeFor(Uri.encode(commandInput.trim())))
                        },
                    )
                    else -> when (val state = uiState) {
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

            // AIL-6 command bar: the launcher's persistent shell actions as bracketed terminal tokens
            // (replaces the top-bar icons). Hidden while the search-overtakes body or dev console is up.
            if (!inputResults.active && !devConsoleOn) {
                CommandBar(
                    items = listOf(
                        CommandBarItem("ask", "Assistant") { viewModel.navigateTo(Routes.Assistant.ROUTE) },
                        CommandBarItem("all apps", "All apps") { viewModel.navigateTo(Routes.AppDrawer.ROUTE) },
                        CommandBarItem("cfg", "Settings") { viewModel.navigateTo(Routes.Settings.ROUTE) },
                    ),
                )
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
        // Favorites/suggestions stay top-aligned; the All-apps action lives in the bottom CommandBar.
        Spacer(modifier = Modifier.weight(1f))
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

/**
 * "Search overtakes" results (AIL-3 / DF-1 + DF-3 hybrid): app matches as an icon+label list over a
 * bracketed route-chip row (`⌕ web`, `✦ ask`, `⌂ site`). App icons use the feature-local [AppTileIcon].
 */
@Composable
private fun InputResultsPanel(
    results: HomeInputResults,
    onAppClick: (InstalledApp) -> Unit,
    onWeb: () -> Unit,
    onSite: () -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chips = results.chips.map { kind ->
        when (kind) {
            RouteChipKind.WEB -> RouteChip("⌕ web", onWeb)
            RouteChipKind.ASK -> RouteChip("✦ ask", onAsk)
            RouteChipKind.SITE -> RouteChip("⌂ site", onSite)
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        RouteChipRow(chips = chips, modifier = Modifier.padding(vertical = Spacing.sm))
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(results.appMatches, key = { it.packageName }) { app ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app) }
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    AppTileIcon(app)
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = Spacing.md),
                    )
                }
            }
        }
    }
}

/** Hidden developer Command console transcript (AIL-3 / DF-1): `> command` + a one-line outcome. */
@Composable
private fun CommandConsole(
    lines: List<ConsoleLine>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        items(lines) { line ->
            Text(
                text = "> ${line.command}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "  ${line.result}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
    }
}

// ── Router proposal confirmation (AIL-5) ─────────────────────────────────────

/**
 * Renders a pending router proposal: the DF-4 [ConfirmActionCard] for a CONFIRM-risk action, or a
 * lighter one-tap [RouteChipRow] accelerator for a SAFE one. Both dispatch through [onConfirm]
 * (which the screen has already wrapped with the permission-gate check); the card also exposes CANCEL.
 */
@Composable
private fun PendingActionArea(
    pending: PendingRoutedAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pending.requiresConfirmation) {
        ConfirmActionCard(
            commandLine = pending.commandLine,
            riskLabel = pending.riskLabel,
            onConfirm = onConfirm,
            onCancel = onCancel,
            modifier = modifier,
        )
    } else {
        RouteChipRow(
            chips = listOf(RouteChip("▸ ${pending.commandLine}", onConfirm)),
            modifier = modifier.padding(vertical = Spacing.sm),
        )
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
 * AIL-6 home status line — replaces the off-theme Material top-bar icons. A monospace terminal status:
 * a phosphor `● online` (accent) / dim `○ offline` reachability indicator plus the clock, so the top bar
 * carries identity + live system state rather than glyphs from a different visual language.
 */
@Composable
private fun HomeStatus(
    isOnline: Boolean,
    time: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = (if (isOnline) "● online" else "○ offline") + "  ·  " + time,
        style = MaterialTheme.typography.labelMedium,
        color = if (isOnline) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

/** Current wall-clock time as `HH:mm` for the status line. */
private fun currentHhMm(): String =
    java.time.LocalTime.now().let { "%02d:%02d".format(it.hour, it.minute) }

/**
 * Builds the intent for the system default-launcher surface. Mirrors the Settings screen helper
 * (Fork X5-C): [RoleManager.ROLE_HOME] request on API 29+ when available **and not already held**, else
 * the Home-settings picker. Requesting a role the app already holds returns `RESULT_CANCELED` with no UI,
 * so once Sidr is default the request would no-op — [Settings.ACTION_HOME_SETTINGS] opens the changeable
 * Home-app picker instead. The caller launches it through an `ActivityResultContracts.StartActivityForResult`
 * launcher — a role request delivered via a plain `startActivity` arrives with a null calling package and is
 * rejected by the system's RequestRoleActivity.
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
