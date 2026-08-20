package com.sidr.launcher.feature.launcher

import androidx.lifecycle.SavedStateHandle
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.input.InputIntent
import com.sidr.launcher.domain.input.UniversalInputRouter
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.resolution.ResolvedCommand
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Task 4 / A0. The command pipeline — typed/voice input, the AIL-3 universal-input live results, the
 * transient command feedback, the AIL-5 pending router proposal, and the whole submit → resolve →
 * [applyOutcome] → confirm/cancel cycle — extracted unchanged from `LauncherViewModel`. Same
 * `resolveCommand.resolve()` branching, same dev-console pre-check and append points, same
 * `applyOutcome` exhaustive `when` (still no `else` — Task 11 adds a branch here, not an escape hatch).
 * This class is a move, not a redesign; `LauncherViewModelTest` must not need one character of edit.
 *
 * [setCommandInput] writes to [savedStateHandle] so the typed command survives process death (H3) —
 * that behaviour moves here unchanged.
 *
 * Ruling R2 (controller): `outcomeSummary()` moves here too — Tasks 1-3 deliberately left it in the
 * ViewModel until every collaborator existed.
 *
 * Naming: the Produces section of the task brief renames three of the four top-level properties on
 * the move (`commandInput` → [input], `commandFeedback` → [feedback], `inputResults` → [liveResults]);
 * [pendingRoutedAction] keeps its name, as the brief lists it unchanged. `LauncherViewModel` keeps
 * every original external name as a delegating one-liner, so nothing outside this class sees the
 * rename.
 *
 * [appList] is not in the brief's "Consumes" line but is a real, load-bearing, **read-only** dependency
 * (per the controller's Tasks 1-3 precedent note): the `AutoLaunch` branch of [submit] needs the
 * already-loaded app's `activityName` to pass a fully-resolved launch to [appLaunch], exactly as the
 * original `onCommandSubmitted` read `uiState.value` (== `appList.state.value`) inline. The dependency
 * direction stays one-way, `CommandSession -> AppList`; nothing here writes back into `appList`.
 *
 * [onNavigate] is the narrow callback for the one piece of ViewModel-owned state moved code here still
 * needs to touch: the `Channel`-backed navigation events ([applyOutcome]'s `OpenAssistant`/
 * `OpenSettings` branches used to call the ViewModel's own `navigateTo` directly). Same pattern as
 * [LauncherAppLaunch]'s `onFeedback`.
 *
 * [showFeedback] is new, and is the mirror image of that same problem: four call sites that stay in
 * `LauncherViewModel` (not part of the moved region) used to write `_commandFeedback.value` directly —
 * `LauncherAppLaunch`'s own `onFeedback` callback, `armDevMode()`, `startVoiceInput()`'s
 * mic-unavailable/voice-error branches, and `LauncherVoiceInput`'s `onError` callback. Now that
 * [feedback]'s backing field lives here, those VM-retained sites need a narrow way back in;
 * `onSuggestionClicked()`'s one touch point (`CommandFeedback.None`) reuses the already-public
 * [dismissFeedback] instead, since that is exactly what it does.
 */
internal class LauncherCommandSession(
    private val resolveCommand: ResolveCommandWithAliasUseCase,
    private val executeAction: ExecuteActionUseCase,
    private val actionCatalog: ActionCatalog,
    private val universalInputRouter: UniversalInputRouter,
    private val savedStateHandle: SavedStateHandle,
    private val appLaunch: LauncherAppLaunch,
    private val appList: LauncherAppList,
    private val devConsole: LauncherDevConsole,
    private val scope: CoroutineScope,
    private val onNavigate: (String) -> Unit,
) {

    // ── Command input — backed by SavedStateHandle so the typed text survives process death (H3).
    // Every writer goes through setCommandInput(...) so the handle stays the single source of truth.
    val input: StateFlow<String> = savedStateHandle.getStateFlow(KEY_COMMAND_INPUT, "")

    internal fun setCommandInput(text: String) {
        savedStateHandle[KEY_COMMAND_INPUT] = text
    }

    // ── Universal-input live results (AIL-3) — derived purely from the buffer + the loaded app list.
    // Enter still routes through the unchanged command pipeline; this only decides what the
    // "search overtakes" panel shows.
    val liveResults: StateFlow<HomeInputResults> = combine(
        input,
        appList.rawAppsResult,
    ) { buffer, appsResult ->
        when (val intent = universalInputRouter.classify(buffer)) {
            InputIntent.Empty, InputIntent.DevSentinel -> HomeInputResults()
            is InputIntent.Query -> {
                val loaded = (appsResult as? OperationResult.Success)?.value ?: emptyList()
                val chips = buildList {
                    add(RouteChipKind.WEB)
                    add(RouteChipKind.ASK)
                    if (intent.siteUrl != null) add(RouteChipKind.SITE)
                }
                HomeInputResults(
                    active = true,
                    appMatches = filterApps(loaded, intent.raw),
                    chips = chips,
                )
            }
        }
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = HomeInputResults(),
    )

    // ── Command feedback — transient result of the last submitted command ──
    private val _feedback = MutableStateFlow<CommandFeedback>(CommandFeedback.None)
    val feedback: StateFlow<CommandFeedback> = _feedback

    // ── Pending router proposal (AIL-5) — a RoutedAction awaiting confirm/one-tap. Null = none. ──
    private val _pendingRoutedAction = MutableStateFlow<PendingRoutedAction?>(null)
    val pendingRoutedAction: StateFlow<PendingRoutedAction?> = _pendingRoutedAction

    fun onChanged(text: String) {
        setCommandInput(text)
        // Editing a new command clears stale feedback and any pending confirm card.
        _feedback.value = CommandFeedback.None
        _pendingRoutedAction.value = null
        // Editing/clearing the buffer without submitting abandons any pending ambiguity: drop the
        // learning token so a later unrelated grid/suggestion tap (both funnel through onAppClicked)
        // can't be misrecorded as an explicit resolution of that stale ambiguity.
        appLaunch.rememberLearningToken(null)
    }

    fun submit(text: String) {
        // Additive AIL-3 pre-check: an ARMED "//dev-mode" toggles the console and is consumed here so it
        // never reaches HandleUserCommandUseCase. Un-armed, it falls through unchanged (Unknown), so the
        // command pipeline stays byte-for-byte for every real input.
        if (devConsole.armed.value && universalInputRouter.classify(text) is InputIntent.DevSentinel) {
            devConsole.toggle(!devConsole.consoleOn.value)
            setCommandInput("")
            _feedback.value = CommandFeedback.Message(
                if (devConsole.consoleOn.value) "dev console on" else "dev console off",
            )
            return
        }
        scope.launch {
            when (val resolved = resolveCommand.resolve(text)) {
                is ResolvedCommand.Outcome -> {
                    // Non-ambiguous → learningToken is null (parity: identical to the pre-S2-1 outcome
                    // path). Ambiguous with no stored preference → outcome is the original
                    // NeedsConfirmation, unchanged, plus a token for a later candidate tap to record.
                    appLaunch.rememberLearningToken(resolved.learningToken)
                    applyOutcome(resolved.outcome)
                    if (devConsole.consoleOn.value) {
                        devConsole.append(text, outcomeSummary(resolved.outcome))
                    }
                }
                is ResolvedCommand.AutoLaunch -> {
                    // A confident learned preference — launch it directly. Executed-like semantics
                    // (input clear) come only from launchApp's real success; a failure renders the
                    // decorator's reordered fallback outcome exactly as a typed command would.
                    appLaunch.rememberLearningToken(null)
                    val packageName = resolved.target.packageName
                    val activityName = (appList.state.value as? UiState.Success)
                        ?.data
                        ?.apps
                        ?.firstOrNull { it.packageName == packageName }
                        ?.activityName
                    if (devConsole.consoleOn.value) {
                        devConsole.append(text, "auto $packageName")
                    }
                    appLaunch.launch(
                        packageName = packageName,
                        activityName = activityName,
                        onResult = { success ->
                            if (success) setCommandInput("") else applyOutcome(resolved.fallback)
                        },
                    )
                }
            }
        }
    }

    /**
     * One-line console summary of a [CommandOutcome] (dev console only; display-safe, session-only,
     * developer-facing — exempt from I18N-1 per spec §3.2). [CommandOutcome.Message]/[CommandOutcome.Failed]
     * render their typed payload's own `toString()` (a `data object`/`data class` gives a readable
     * name like `Help` or `NoAppFound(query=telegram)`) rather than resurrecting a user-facing English
     * constant that I18N-1 deleted.
     */
    private fun outcomeSummary(outcome: CommandOutcome): String = when (outcome) {
        CommandOutcome.Empty -> "empty"
        CommandOutcome.Executed -> "✓ executed"
        CommandOutcome.NoOp -> "no-op"
        is CommandOutcome.Message -> outcome.message.toString()
        is CommandOutcome.NeedsConfirmation -> "? ${outcome.candidates.size} candidates"
        is CommandOutcome.Suggest -> "? suggest"
        CommandOutcome.LowConfidence -> "low confidence"
        is CommandOutcome.Unknown -> "unknown"
        is CommandOutcome.Failed -> "✗ ${outcome.failure}"
        CommandOutcome.OpenAssistant -> "→ assistant"
        CommandOutcome.OpenSettings -> "→ settings"
        CommandOutcome.ShowApps -> "→ apps"
        CommandOutcome.ClearInput -> "cleared"
        is CommandOutcome.RoutedAction -> "→ route ${outcome.action.id.value}"
    }

    /**
     * Web-search route chip (AIL-3): prefix the buffer with the `search` verb and route it through the
     * UNCHANGED command pipeline (AIL-2 web search, already history-redacted). No new executor path.
     */
    fun submitWebSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        // Avoid "search search x" when the buffer already carries the search verb.
        val command = if (q.lowercase(Locale.ROOT).startsWith("search ")) q else "search $q"
        submit(command)
    }

    /**
     * Open-site route chip (AIL-3): the buffer is already a safe URL (the chip is offered only then), so
     * submitting it as-is routes to AIL-2's OpenUrl through the UNCHANGED pipeline. One-tap "submit".
     */
    fun submitSite(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        submit(q)
    }

    fun dismissFeedback() {
        _feedback.value = CommandFeedback.None
    }

    /**
     * Narrow write-back for the four VM-retained call sites that used to write `_commandFeedback.value`
     * directly before this state moved here: [LauncherAppLaunch]'s `onFeedback` callback, `armDevMode()`,
     * and `startVoiceInput()`'s mic-unavailable/voice-error branches (the latter two still live in
     * `LauncherViewModel`, not part of this task's moved region). Not part of the brief's stated public
     * surface — added because ownership of [feedback]'s backing field crossed the class boundary.
     */
    fun showFeedback(feedback: CommandFeedback) {
        _feedback.value = feedback
    }

    // ── CommandOutcome → UI — exhaustive when, no else branch ──────────────
    // Add a new branch here whenever CommandOutcome gains a new variant.
    fun applyOutcome(outcome: CommandOutcome) {
        // Any new outcome dismisses a stale confirm card; the RoutedAction branch re-arms it below.
        _pendingRoutedAction.value = null
        when (outcome) {
            CommandOutcome.Empty ->
                _feedback.value = CommandFeedback.EmptyInput

            CommandOutcome.Executed -> {
                setCommandInput("")
                _feedback.value = CommandFeedback.None
            }

            CommandOutcome.NoOp ->
                _feedback.value = CommandFeedback.None

            is CommandOutcome.Message ->
                _feedback.value = CommandFeedback.Domain(outcome.message)

            is CommandOutcome.NeedsConfirmation ->
                _feedback.value = CommandFeedback.Ambiguous(outcome.candidates)

            is CommandOutcome.Suggest ->
                _feedback.value = CommandFeedback.Suggestion(suggestedIntentFor(outcome.intent))

            CommandOutcome.LowConfidence ->
                _feedback.value = CommandFeedback.LowConfidence

            is CommandOutcome.Unknown ->
                _feedback.value = CommandFeedback.UnknownCommand

            is CommandOutcome.Failed ->
                _feedback.value = CommandFeedback.Failure(outcome.failure)

            CommandOutcome.OpenAssistant -> {
                setCommandInput("")
                _feedback.value = CommandFeedback.None
                // Route string belongs to the UI layer — the domain only said "OpenAssistant".
                onNavigate(Routes.Assistant.ROUTE)
            }

            CommandOutcome.OpenSettings -> {
                setCommandInput("")
                _feedback.value = CommandFeedback.None
                onNavigate(Routes.Settings.ROUTE)
            }

            CommandOutcome.ShowApps -> {
                setCommandInput("")
                _feedback.value = CommandFeedback.None
            }

            CommandOutcome.ClearInput -> {
                setCommandInput("")
                _feedback.value = CommandFeedback.None
            }

            // AIL-4/5: the LLM router proposed a registered action. Per Fork R4 it is NEVER
            // auto-executed — it is surfaced as a pending affordance (confirm card for CONFIRM risk,
            // one-tap for SAFE) that the user must act on. Execution happens in confirm().
            is CommandOutcome.RoutedAction -> {
                _feedback.value = CommandFeedback.None
                _pendingRoutedAction.value = PendingRoutedAction(
                    action = outcome.action,
                    commandLine = commandLineFor(outcome.action),
                    riskLabel = RISK_CONFIRM_LABEL,
                    requiresConfirmation = outcome.needsConfirmation,
                    permissionGate = actionCatalog.descriptor(outcome.action.id)?.permissionGate,
                )
            }
        }
    }

    /**
     * Execute the pending router proposal (AIL-5) — the user confirmed the card or tapped the SAFE
     * one-tap affordance. Runs through [ExecuteActionUseCase] (resolve → execute) and feeds the result
     * back through [applyOutcome], so a successful launch clears the input, a navigation routes, and an
     * ambiguous/not-found result surfaces exactly as a typed command would. No-op if nothing is pending.
     */
    fun confirm() {
        val pending = _pendingRoutedAction.value ?: return
        _pendingRoutedAction.value = null
        scope.launch {
            val outcome = executeAction.execute(pending.action)
            applyOutcome(outcome)
            if (devConsole.consoleOn.value) {
                devConsole.append("confirm ${pending.action.id.value}", outcomeSummary(outcome))
            }
        }
    }

    /** Dismiss the pending router proposal without executing (CANCEL). Leaves the typed text in place. */
    fun cancel() {
        _pendingRoutedAction.value = null
        _feedback.value = CommandFeedback.None
    }

    /** The `>`-prefixed command form shown on the confirm card / one-tap chip (display-safe). */
    private fun commandLineFor(action: LauncherAction): String = when (action) {
        is LauncherAction.LaunchApp -> "open ${action.query}"
        is LauncherAction.WebSearch -> "search ${action.query}"
        LauncherAction.OpenSettings -> "settings"
        is LauncherAction.OpenAssistant -> "assistant"
        LauncherAction.ShowApps -> "apps"
        is LauncherAction.OpenUrl -> "open ${action.url}"
        is LauncherAction.PlayStoreSearch -> "install ${action.query}"
    }

    /**
     * What `describe()` used to word, named instead (I18N-1 spec §3.5) — one [SuggestedIntent]
     * variant per [LauncherIntent] branch; `LauncherPresentation.feedbackText` picks the sentence.
     */
    private fun suggestedIntentFor(intent: LauncherIntent): SuggestedIntent = when (intent) {
        is LauncherIntent.LaunchAppIntent -> SuggestedIntent.LaunchApp(intent.displayNameQuery)
        is LauncherIntent.SearchIntent -> SuggestedIntent.Search(intent.query)
        is LauncherIntent.OpenSettingsIntent -> SuggestedIntent.OpenSettings
        is LauncherIntent.SimpleCommandIntent -> SuggestedIntent.SimpleCommand
        is LauncherIntent.OpenUrlIntent -> SuggestedIntent.OpenUrl(intent.url)
        is LauncherIntent.PlayStoreSearchIntent -> SuggestedIntent.PlayStoreSearch(intent.query)
        is LauncherIntent.UnknownIntent -> SuggestedIntent.Unknown
    }

    private companion object {
        // AIL-5: the bracketed risk tag on the confirm card. MVP produces a card only for CONFIRM-risk
        // (or unregistered) proposals; DANGEROUS is reserved for Stage 3.
        const val RISK_CONFIRM_LABEL = "CONFIRM"
        // SavedStateHandle key for the typed command text (H3 process-death restoration).
        const val KEY_COMMAND_INPUT = "command_input"
    }
}
