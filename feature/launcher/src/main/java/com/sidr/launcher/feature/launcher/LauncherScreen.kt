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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.ui.component.AppTile
import com.sidr.launcher.core.ui.component.SidrActionGate
import com.sidr.launcher.core.ui.component.SidrActionGateType
import com.sidr.launcher.core.ui.component.SidrActionProposal
import com.sidr.launcher.core.ui.component.SidrActionProposalTone
import com.sidr.launcher.core.ui.component.EmptyState
import com.sidr.launcher.core.ui.component.ErrorState
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrRouteChip
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrUniversalInput
import com.sidr.launcher.core.ui.component.SidrUniversalInputState
import com.sidr.launcher.core.ui.component.TopBarIcon
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
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
    val inputResults by viewModel.inputResults.collectAsStateWithLifecycle()
    val pendingRoutedAction by viewModel.pendingRoutedAction.collectAsStateWithLifecycle()
    val devConsoleOn by viewModel.devConsoleOn.collectAsStateWithLifecycle()
    val consoleLines by viewModel.consoleLines.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // First-run nudge (Block X6): whether this launcher is already the system default HOME app.
    // A runtime Android query kept in the screen (like Settings' "Set as default"); the persisted
    // "dismissed" half lives in UserPreferences (LauncherUiState.setupHintDismissed).
    val isDefaultLauncher = remember { isDefaultLauncher(context) }
    // Tap anywhere outside a text field / clickable to dismiss the soft keyboard (2026-07-12). A tap
    // on the input, chips, tiles etc. is consumed by those; only taps on empty home area reach this.
    val focusManager = LocalFocusManager.current

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

    // Default home (Success, not typing, not the dev console) scrolls the WHOLE column as one unit so
    // nothing is clipped on landscape rotation (Bug fix 2026-07-12). The scroll is enabled ONLY in that
    // state: the "search-overtakes" panel and the dev console are LazyColumns and the Loading/Empty/Error
    // states fill the body, none of which may live inside a parent verticalScroll (infinite-height
    // measure). In those states the body keeps its weight(1f) box instead.
    val homeScrollState = rememberScrollState()
    val scrollDefaultHome = uiState is UiState.Success && !inputResults.active && !devConsoleOn

    SidrScaffold(
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .then(
                    if (scrollDefaultHome) Modifier.verticalScroll(homeScrollState) else Modifier,
                ),
        ) {
            // Shahada is the topmost element, then the date line (Hijri · Gregorian) directly beneath it
            // (owner layout). Both static — no prayer data — and hidden while typing so results overtake.
            HomeAnchorSlot(visible = !inputResults.active && !devConsoleOn)
            HomeDateLine(visible = !inputResults.active && !devConsoleOn)

            // DS-4: Universal Input replaces the legacy SidrCommandPrompt. Submit drives the existing
            // command pipeline byte-for-byte — `onSubmit` is parameterless (spec §7) and the screen owns
            // the value it forwards to the unchanged onCommandSubmitted. Clear routes through the existing
            // input-change path (no dedicated clear callback exists in the VM).
            SidrUniversalInput(
                value = commandInput,
                onValueChange = viewModel::onCommandChanged,
                // Submit the command AND drop focus so the soft keyboard closes on send (parity with
                // terminal/assistant and the tap-to-dismiss behaviour above).
                onSubmit = {
                    viewModel.onCommandSubmitted(commandInput)
                    focusManager.clearFocus()
                },
                state = if (commandInput.isNotBlank()) {
                    SidrUniversalInputState.Typing
                } else {
                    SidrUniversalInputState.Idle
                },
                voiceAvailable = showMic,
                onVoiceClick = onMicTap,
                onClearClick = { viewModel.onCommandChanged("") },
                routeContent = {
                    // Persistent route lane under the input (artifact): APP is the selected lane (its
                    // content is the app results / favorites below — no behaviour), WEB/ASK are always
                    // available, SITE appears only for a safe URL. WEB/SITE no-op on a blank query.
                    HomeRouteChips(
                        hasSiteRoute = inputResults.chips.contains(RouteChipKind.SITE),
                        onWeb = { viewModel.submitWebSearch(commandInput) },
                        onAsk = {
                            viewModel.navigateTo(Routes.Assistant.routeFor(Uri.encode(commandInput.trim())))
                        },
                        onSite = { viewModel.submitSite(commandInput) },
                    )
                },
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

            when {
                // Hidden developer transcript (DF-1) — takes over the body while on. LazyColumn: keep it
                // in a weighted box (parent scroll is disabled in this state).
                devConsoleOn -> Box(modifier = Modifier.weight(1f)) {
                    CommandConsole(lines = consoleLines)
                }
                // "Search overtakes": a non-blank buffer replaces the home body with results (LazyColumn).
                inputResults.active -> Box(modifier = Modifier.weight(1f)) {
                    InputResultsPanel(
                        results = inputResults,
                        onAppClick = viewModel::onAppClicked,
                    )
                }
                else -> when (val state = uiState) {
                    is UiState.Loading -> Box(modifier = Modifier.weight(1f)) { LoadingContent() }
                    is UiState.Empty -> Box(modifier = Modifier.weight(1f)) {
                        EmptyState(message = "No apps found")
                    }
                    is UiState.Error -> Box(modifier = Modifier.weight(1f)) {
                        ErrorState(
                            message = errorMessage(state.error),
                            onRetry = if (state.retryable) viewModel::retry else null,
                        )
                    }
                    // Default home: rendered inline (no weight) so it scrolls with the whole column
                    // (see [scrollDefaultHome]). HomeContent is a plain top-aligned column.
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

            // Task 7 + 2026-07-12 revision: the app-level bottom tab bar now owns
            // Home/Apps/Tasks/Agents/Activity/Terminal switching (AppNavHost's TabRootScaffold) —
            // "All apps" was promoted from a Home-body row into the "Apps" tab itself, so the old
            // three-row HomeBottomNav is retired with no replacement row here. Assistant stays
            // reachable via the ASK route chip above (spec: no duplicate standalone Assistant row).
            // The local-first privacy line now lives in AppNavHost's TabRootScaffold, below the tab
            // bar itself, not here (see [HomePrivacyLine] callers).
        }
    }
}

// ── DS-4 Home shell (sacred anchor, date line, bottom nav, brand/privacy) ────

/**
 * Date line under the Shahada (owner layout): Hijri first, then Gregorian, on one centred row. Hidden
 * while typing. A real calendar conversion (`HijrahDate`) — NOT prayer data (DS-6B owns that).
 */
@Composable
private fun HomeDateLine(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.md),
        contentAlignment = Alignment.Center,
    ) {
        SidrText(
            text = "${currentHijriDate()} · ${currentGregorianDate()}",
            role = SidrTextRole.SYSTEM,
            color = SidrTheme.colors.dim,
        )
    }
}

/**
 * DS-4 sacred anchor (spec §6, §8): the quiet English Shahada, serif, centered — a static spiritual
 * anchor, NOT prayer data. DS-6A owns the full Sacred Header and DS-6B owns prayer-time correctness;
 * neither prayer times nor sources are rendered here. Hidden while typing so results can overtake.
 */
@Composable
private fun HomeAnchorSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SidrText(text = "There is no deity except Allah", role = SidrTextRole.SACRED)
        SidrText(text = "Muhammad is the messenger of Allah", role = SidrTextRole.SACRED)
    }
}

/**
 * DS-4 route lane (artifact): four equal-width, hairline-bordered chips under the input. APP is the
 * selected lane (presentation-only — its content is the results/favorites below), WEB/ASK are always
 * present, SITE appears only for a safe URL. No route auto-submits.
 */
@Composable
private fun HomeRouteChips(
    hasSiteRoute: Boolean,
    onWeb: () -> Unit,
    onAsk: () -> Unit,
    onSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SidrRouteChip("APP", selected = true, onClick = {}, modifier = Modifier.weight(1f))
        SidrRouteChip("WEB", selected = false, onClick = onWeb, modifier = Modifier.weight(1f))
        if (hasSiteRoute) {
            SidrRouteChip("SITE", selected = false, onClick = onSite, modifier = Modifier.weight(1f))
        }
        SidrRouteChip("ASK", selected = false, onClick = onAsk, modifier = Modifier.weight(1f))
    }
}

// HomePrivacyLine moved to app/navigation/SidrTabScaffold.kt as SidrAppFooter (2026-07-12) — it now
// renders below the tab bar on every tab root, not just Home, so it lives with the shared tab-bar
// chrome rather than as Home-local content. LauncherViewModel::armDevMode is still threaded through
// from AppNavHost's Home call site (see TabRootScaffold's onArmDevMode param).

/** Today's Gregorian date for the top row, e.g. `Sat, 11 Jul`. */
private fun currentGregorianDate(): String =
    java.time.LocalDate.now().format(
        java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM", java.util.Locale.getDefault()),
    )

/**
 * Today's Hijri date, e.g. `25 Muharram`, via the platform [java.time.chrono.HijrahDate] (Umm al-Qura).
 * A real calendar conversion — NOT prayer data — so the day can differ from a local moon sighting;
 * authority/method-correct dates are DS-6B's concern.
 */
private fun currentHijriDate(): String =
    java.time.chrono.HijrahDate.now().format(
        java.time.format.DateTimeFormatter.ofPattern("d MMMM", java.util.Locale.getDefault()),
    )

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
    // Plain top-aligned column: the caller scrolls the WHOLE default-home column (Shahada → date →
    // input → chips → this content) together, so on landscape rotation everything scrolls as one and
    // the Favorites grid stays reachable instead of being clipped off the bottom (2026-07-12 fix).
    // Hence fillMaxWidth (not fillMaxSize) and no weight(1f) spacer — both would break the parent scroll.
    Column(modifier = modifier.fillMaxWidth()) {
        if (showSetupHint) {
            SetupNudge(onSetDefault = onSetDefault, onDismiss = onDismissHint)
        }
        if (state.suggestions.isNotEmpty()) {
            suggestionsContent(state.suggestions, onSuggestionTap)
        }
        if (state.favorites.isNotEmpty()) {
            SidrSectionHeader(text = "Favorites")
            FavoritesGrid(favorites = state.favorites, onAppClick = onAppClick)
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

/**
 * Favorites as a 4-column tile grid (artifact): monogram/icon tiles with labels, rows of four. A plain
 * chunked grid (favorites are capped small) so it composes inside the scrolling Home column; empty cells
 * keep the last row aligned.
 */
@Composable
private fun FavoritesGrid(
    favorites: List<InstalledApp>,
    onAppClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        favorites.chunked(FAVORITES_COLUMNS).forEach { rowApps ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                rowApps.forEach { app ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AppTile(
                            label = app.label,
                            onClick = { onAppClick(app) },
                            icon = { AppTileIcon(app) },
                        )
                    }
                }
                repeat(FAVORITES_COLUMNS - rowApps.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private const val FAVORITES_COLUMNS = 4

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
 * "Search overtakes" results (DS-4): app matches as an icon+label list. The route lane (APP/WEB/SITE/ASK)
 * is the persistent strip under the input, so it is not repeated here. App icons use the feature-local
 * [AppTileIcon]; each row carries a fixed minimum height so an async-loaded icon never resizes the row
 * or shifts the input above it (spec §4, §8).
 */
@Composable
private fun InputResultsPanel(
    results: HomeInputResults,
    onAppClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(results.appMatches, key = { it.packageName }) { app ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.minTouchTarget)
                    .clickable { onAppClick(app) }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            ) {
                AppTileIcon(app)
                SidrText(
                    text = app.label,
                    role = SidrTextRole.HUMAN_BODY,
                    modifier = Modifier.padding(start = Spacing.md),
                )
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
 * Renders a pending router proposal: the DS-3 [SidrActionGate] for a CONFIRM-risk action, or a
 * lighter one-tap [RouteChipRow] accelerator for a SAFE one. Both dispatch through [onConfirm]
 * (which the screen has already wrapped with the permission-gate check); the gate also exposes CANCEL.
 */
@Composable
private fun PendingActionArea(
    pending: PendingRoutedAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pending.requiresConfirmation) {
        SidrActionGate(
            type = SidrActionGateType.ExternalHandoff,
            title = "Execute?",
            consequence = "This will run: ${pending.commandLine}",
            target = pending.commandLine,
            confirmLabel = "Confirm",
            onConfirm = onConfirm,
            onCancel = onCancel,
            modifier = modifier,
        )
    } else {
        // SAFE routed proposal — a one-tap accelerator, deliberately distinct from the CONFIRM gate.
        // Still requires a deliberate tap (R4: nothing auto-executes).
        SidrActionProposal(
            title = pending.commandLine,
            description = "Safe proposal. It still waits for your tap.",
            tone = SidrActionProposalTone.Safe,
            onExecute = onConfirm,
            onCancel = onCancel,
            executeLabel = "Run",
            modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
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

        // Ambiguity reads as a clarification prompt, not an error (spec §8): a quiet "Did you mean:"
        // header over the candidate list. A candidate launches only on an explicit tap.
        is CommandFeedback.Ambiguous -> Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            SidrText(text = "Did you mean:", role = SidrTextRole.PROVENANCE)
            Spacer(modifier = Modifier.height(Spacing.xs))
            feedback.candidates.forEach { app ->
                SidrText(
                    text = app.label,
                    role = SidrTextRole.HUMAN_BODY,
                    color = SidrTheme.colors.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.minTouchTarget)
                        .clickable { onCandidateClick(app) }
                        .padding(vertical = Spacing.sm),
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
    SidrText(
        text = text,
        role = SidrTextRole.HUMAN_BODY,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .clickable(onClick = onDismiss)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .wrapContentHeight(),
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
