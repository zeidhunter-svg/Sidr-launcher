package com.sidr.launcher.feature.launcher

import androidx.lifecycle.SavedStateHandle
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.UnresolvedTarget
import com.sidr.launcher.domain.agent.bind
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.SidrDestination
import com.sidr.launcher.domain.ai.bridge.choose
import com.sidr.launcher.domain.ai.bridge.plan
import com.sidr.launcher.domain.ai.router.toCommandOutcome
import com.sidr.launcher.domain.input.InputIntent
import com.sidr.launcher.domain.input.UniversalInputRouter
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.RecordLearnedPlanUseCase
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
import com.sidr.launcher.domain.memory.resolution.ResolvedCommand
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.feature.launcher.agent.LauncherAgentSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Task 4 / A0. The command pipeline — typed/voice input, the AIL-3 universal-input live results, the
 * transient command feedback, and the whole submit → resolve → [applyOutcome] cycle — extracted
 * unchanged from `LauncherViewModel`; since A4′ phase 3a it also holds the interpretation card (the
 * model's reading, shown before anything runs) where the deleted router's pending proposal and its
 * confirm/cancel used to be. Same
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
 * the move (`commandInput` → [input], `commandFeedback` → [feedback], `inputResults` → [liveResults]).
 * `LauncherViewModel` keeps every original external name as a delegating one-liner, so nothing outside
 * this class sees the rename.
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
    private val universalInputRouter: UniversalInputRouter,
    private val savedStateHandle: SavedStateHandle,
    private val appLaunch: LauncherAppLaunch,
    private val appList: LauncherAppList,
    private val devConsole: LauncherDevConsole,
    // Task 12 / A0: the seventh collaborator. `applyOutcome` hands it a started session and stops
    // there — the command pipeline neither drives the agent nor reads its state, which is what keeps
    // the two surfaces separable.
    private val agentSession: LauncherAgentSession,
    // A4′ phase 3a: «Выполнить» on the interpretation card starts the model's plan through the
    // engine — `startPlanned` validates it against the federation, the engine gates it by risk.
    private val startAgentSession: StartAgentSessionUseCase,
    // A4′ phase 3b, Task 11 (fork 3b-A, B-10): the remainder of a model plan that acted is bound AGAIN on
    // the device before its card — no model is asked ([showRemainder]).
    private val binder: MentionBinder,
    // A4′ phase 4, Task 7 (B-8): «Выполнить» on a fresh reading records what it teaches once its session started —
    // on [applicationScope], like `LauncherAppLaunch.recordChoiceIfPending`, so the write survives the ViewModel.
    private val recordLearnedPlan: RecordLearnedPlanUseCase,
    private val applicationScope: CoroutineScope,
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

    // ── The interpretation card (A4′ phase 3a, spec §7.12.3 condition 1) — transient (plan §0.2 B-12).
    private val _interpretation = MutableStateFlow<Interpretation?>(null)
    val interpretation: StateFlow<Interpretation?> = _interpretation

    // Beside the card standing now (A4′ phase 4, plan §0.2 B-7, B-8), and cleared with it: what a fresh reading
    // would teach if it runs ([CommandOutcome.Interpreted.learnable]), and the form of the learned command a
    // replay card came from ([CommandOutcome.Interpreted.learned]). Never both — step 7 sets the first, step 2c
    // the second. Read once, by «Выполнить» ([runInterpretation]).
    private var learnable: LearnedPlanShape? = null
    private var learned: CommandForm? = null

    // ── The visible wait (spec §7.12.2, plan §0.2 B-10) and the command it waits for. ──
    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking
    private var pending: Job? = null

    // The picks made on the card standing now, in the order they were made — which is step order, because a
    // pick answers the HEAD owed pick only and the head is the earliest step still owed (A4′ phase 3b, B-19,
    // controller ruling E10-P4). Each keeps the token of the pick IT answered: recorded at «Выполнить», one
    // by one, never at the pick. Cleared with the card — Run, «Отмена», an edit, any new outcome.
    private val picks = mutableListOf<CardPick>()

    // ── The 2b target pick (A4′ phase 3b, fork 3b-E; plan §0.2 B-7) — transient, like the card, and
    // NOT the card: the model read nothing here. A tap binds and starts the session directly.
    private val _targetPick = MutableStateFlow<UnresolvedTarget?>(null)
    val targetPick: StateFlow<UnresolvedTarget?> = _targetPick

    fun onChanged(text: String) {
        setCommandInput(text)
        // Editing a new command clears stale feedback and any interpretation card standing for the old one.
        _feedback.value = CommandFeedback.None
        _interpretation.value = null
        picks.clear()
        clearLearnedBesideCard()
        // A 2b pick stands for the old text as much as a card does (phase 3b).
        _targetPick.value = null
        // A4′ phase 3a, Task 9 review I1 (Review Focus 2, in flight): an edit also abandons a command
        // still waiting on its answer. Left running, its late answer would put up a card for text the
        // user has already left — one tap from running on a SAFE step. Every caller is a user gesture
        // (a text change in the field, its ✕, a voice partial, leaving the agent surface); the
        // session's own buffer writes go through setCommandInput and cancel nothing.
        pending?.cancel()
        pending = null
        _thinking.value = false
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
        pending?.cancel()
        pending = scope.launch {
            // Plan §0.2 B-10: FastPath answers before 300 ms and shows nothing; only a slow answer — the
            // model's — shows the wait, with «Отмена».
            val wait = launch {
                delay(THINKING_DELAY_MS)
                _thinking.value = true
            }
            try {
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
            } finally {
                wait.cancel()
                _thinking.value = false
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
        is CommandOutcome.OpenAssistant -> "→ assistant"
        CommandOutcome.OpenSettings -> "→ settings"
        CommandOutcome.ShowApps -> "→ apps"
        CommandOutcome.ClearInput -> "cleared"
        is CommandOutcome.AgentSessionStarted -> "→ agent ${outcome.id.value}"
        is CommandOutcome.Interpreted -> "? interpretation"
        is CommandOutcome.ChooseTarget -> "? ${outcome.target.candidates.size} targets"
    }

    /**
     * Web-search route chip (AIL-3): the user has chosen the destination, so the chip dispatches the
     * act — [LauncherAction.WebSearch] through [ExecuteActionUseCase], with the same [applyOutcome]
     * handling and the same dev-console line. No new executor path. Since A4′ phase 3a this is the one
     * direct execution of a [LauncherAction] through [ExecuteActionUseCase] in the launcher
     * (`OneModelPathGuardTest`): the USER points it at a destination, so it is not the model's path (spec
     * §7.12.3). It is not the only thing the launcher runs directly — app taps, suggestions and a learned
     * AutoLaunch start apps through [LauncherAppLaunch] — but those are launches the user chose.
     *
     * **Why it no longer goes through the command pipeline** (A4′ phase 1). It used to prefix the buffer
     * with `search` and [submit] it, so FastPath re-scored the user's choice as if it were a typed
     * command — and since phase 1 FastPath's search arm withdraws a query that carries a second goal
     * (`SecondGoal`, «… и закажи такси») to `LowConfidence`. An explicit destination must not be
     * re-scored: the chip would refuse exactly the text a user reaches for it with, and with a provider
     * configured that text would go to the cloud model instead of the browser the user picked. So the
     * chip never consults the `IntentMatcher`. It therefore also records no `intent_match` SEARCH row
     * any more — a row whose text was redacted anyway (`IntentMatchMapper`).
     *
     * A buffer that already starts with the `search` verb drops it rather than searching for the word
     * ("search cats" searches "cats"), as the old prefix de-duplication did.
     */
    fun submitWebSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val searchQuery = if (q.lowercase(Locale.ROOT).startsWith(SEARCH_VERB_PREFIX)) {
            q.substring(SEARCH_VERB_PREFIX.length).trim()
        } else {
            q
        }
        if (searchQuery.isEmpty()) return
        // A web search answers no app ambiguity. The chip used to be a fresh submit, which always
        // replaced the pending learning token; now that it is not, it drops the token itself — or a
        // later grid tap on a former candidate would be recorded as the answer to an ambiguity the
        // user walked away from (the same misrecord [onChanged] guards against).
        appLaunch.rememberLearningToken(null)
        // The user chose the browser: a command still waiting on the model is abandoned (T9 m2).
        supersedeWaitingCommand()
        val action = LauncherAction.WebSearch(searchQuery)
        scope.launch {
            val outcome = executeAction.execute(action)
            applyOutcome(outcome)
            if (devConsole.consoleOn.value) {
                devConsole.append(commandLineFor(action), outcomeSummary(outcome))
            }
        }
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

    /**
     * A4′ phase 3a, Task 11 (Task 9 review m2): a gesture that does something ELSE abandons a command
     * still waiting on the model. Left running, the slow answer would land over what the user just did:
     * `OpenAssistant(question)` navigates away from the app or screen they opened, a card stands over the
     * search they chose. The command's coroutine is cancelled, the model call with it, and the wait goes.
     * Its callers, all of them:
     *  - the WEB chip — [submitWebSearch], here;
     *  - a tap on an app, from the grid or an ambiguity candidate — `LauncherViewModel.onAppClicked`;
     *  - every suggestion tap, before its branch — `LauncherViewModel.onSuggestionClicked` (its app branch
     *    reaches `onAppClicked` and calls again, finding nothing pending);
     *  - the ASK chip and the prayer strip's details, from `LauncherScreen` — `LauncherViewModel.navigateByUser`.
     *    The prayer-details half is wired, and held by no test (plant F1-PRAYER stays green). It is
     *    reachable during a wait: the strip hides while the field holds text, but «Продолжить» on a
     *    model-written plan re-routes its goal with the field empty;
     *  - a remainder card brought up from the agent surface — [showRemainder] (A4′ phase 3b, Task 11), held by
     *    no test.
     * [submitSite] needs no call: it is a [submit], which supersedes on its own. [onChanged] and
     * [cancelThinking] keep their own lines, written out where they are.
     *
     * NAMED, not built: a switch of the bottom tab leaves Home without passing through the ViewModel
     * (`AppNavHost`), and the mic's permission-education route and the «Set up provider» button still call
     * `navigateTo` — a command waiting on the model survives those three: its outcome is still applied,
     * and a navigation it carries fires when Home collects its events again.
     */
    internal fun supersedeWaitingCommand() {
        pending?.cancel()
        pending = null
        _thinking.value = false
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
        // Any new outcome dismisses a stale interpretation card, the picks made on it, and a stale 2b pick;
        // the Interpreted and ChooseTarget branches re-arm their own below. The learning token is NOT
        // cleared here: the submit path armed it for THIS outcome just before (a FastPath ambiguity's token),
        // and every branch that must not learn clears it itself.
        _interpretation.value = null
        picks.clear()
        clearLearnedBesideCard()
        _targetPick.value = null
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
                _feedback.value = CommandFeedback.Ambiguous(outcome.candidates, outcome.confirm)

            is CommandOutcome.Suggest ->
                _feedback.value = CommandFeedback.Suggestion(suggestedIntentFor(outcome.intent))

            CommandOutcome.LowConfidence ->
                _feedback.value = CommandFeedback.LowConfidence

            is CommandOutcome.Unknown ->
                _feedback.value = CommandFeedback.UnknownCommand

            is CommandOutcome.Failed ->
                _feedback.value = CommandFeedback.Failure(outcome.failure)

            is CommandOutcome.OpenAssistant -> {
                setCommandInput("")
                _feedback.value = CommandFeedback.None
                // Route string belongs to the UI layer — the domain only said "OpenAssistant". A question
                // the model read arrives prefilled and UNSENT (spec §7.12.3 condition 3).
                onNavigate(outcome.question?.let(Routes.Assistant::routeForPrompt) ?: Routes.Assistant.ROUTE)
            }

            CommandOutcome.OpenSettings -> {
                setCommandInput("")
                _feedback.value = CommandFeedback.None
                onNavigate(Routes.Settings.ROUTE)
            }

            // Fork 3b-F (A4′ phase 3b, Task 7): the app grid is the drawer's own route — Home draws
            // favourites only — so «apps» opens it, in ONE place for every path: FastPath's own «apps» and
            // the card's «Выполнить» for a reading of the grid both arrive here, and navigate once.
            CommandOutcome.ShowApps -> {
                setCommandInput("")
                _feedback.value = CommandFeedback.None
                onNavigate(Routes.AppDrawer.ROUTE)
            }

            CommandOutcome.ClearInput -> {
                setCommandInput("")
                _feedback.value = CommandFeedback.None
            }

            // Task 11 started a session and persisted it; Task 12's job here is to hand the id over
            // and get out of the way. The feedback line is cleared because the agent surface is now
            // the honest account of what is happening — leaving "no such app" underneath it would
            // contradict the plan that was made precisely because of it.
            // A4′ phase 4 (B-7, B-10): a learned command's session — started at once by step 2c, or by a learned
            // card's «Выполнить» — carries its form on into the agent surface, which marks that session's card.
            is CommandOutcome.AgentSessionStarted -> {
                _feedback.value = CommandFeedback.None
                agentSession.attach(outcome.id, outcome.learned)
            }

            // A4′ phase 3a (condition 1): the model's reading waits on the card. A pick binds; «Выполнить»
            // records (B-3). Phase 3b, B-19 (ruling E10-P4): no token is armed while the card stands — each
            // pick carries its own, armed and recorded one by one at «Выполнить», in step order — so none is
            // left from before either (a grid tap under the card answers no ambiguity).
            is CommandOutcome.Interpreted -> {
                _feedback.value = CommandFeedback.None
                appLaunch.rememberLearningToken(null)
                learnable = outcome.learnable
                learned = outcome.learned
                _interpretation.value = outcome.interpretation
            }

            // A4′ phase 3b (B-7, R14-39 closed): a target step 2b claimed and could not bind waits on the
            // device's own pick list — not on the interpretation card, and not on FastPath's launch list,
            // whose tap launches. Nothing is learned from it: 2b's tools never read a learned launch
            // choice (B-3), so no learning token is armed and none is left from before.
            is CommandOutcome.ChooseTarget -> {
                _feedback.value = CommandFeedback.None
                appLaunch.rememberLearningToken(null)
                _targetPick.value = outcome.target
            }
        }
    }

    /** «Отмена» on the wait (spec §7.12.2): the command's coroutine — the model call with it — is cancelled. */
    fun cancelThinking() {
        pending?.cancel()
        pending = null
        _thinking.value = false
    }

    /**
     * A pick on the card's list (spec §7.12.3 condition 2): it BINDS the step's app; it launches nothing. It
     * answers the HEAD owed pick ([choose]); that pick's step and its OWN learning token are kept with the
     * package — read from the head BEFORE [choose] takes it off — for «Выполнить» to record (B-19).
     */
    fun chooseApp(packageName: String) {
        val act = _interpretation.value as? Interpretation.Act ?: return
        val head = act.choices.firstOrNull() ?: return
        val bound = act.choose(packageName) ?: return
        picks += CardPick(step = head.step, learning = head.choice.learning, packageName = packageName)
        _interpretation.value = bound
    }

    /**
     * «Выполнить» (condition 1). A navigation opens its screen. A reading of steps starts a session through
     * the engine only once no pick is owed ([plan] is `null` until then, and Run does nothing); the engine
     * gates every step by its risk exactly as any plan, so a `CONFIRM` step costs a second card — the
     * regression spec §7.12.3 accepts. The card is taken down BEFORE the start, so a second tap finds
     * nothing to run (Review Focus 1).
     *
     * **The picks are learned here, in step order, each under its own token** (B-19, ruling E10-P4): for
     * every pick whose token is non-null — a launch pick; `uninstall_app`'s pick carries none — the token is
     * armed and the package handed to [LauncherAppLaunch.recordChoiceIfPending], the ONE gated record call
     * (its membership check is the check; there is no second record path). Each call consumes the token it
     * was armed with, so no pick can be recorded under another pick's mention.
     *
     * **What a fresh reading teaches is recorded only once its session started** (A4′ phase 4, plan §0.2 B-8):
     * the card's `learnable` is taken down with the card — a second tap finds neither — and recorded on
     * [applicationScope] only after `startPlanned` succeeded; a start that failed teaches nothing. A replay card's
     * `learned` form records nothing and rides on into [CommandOutcome.AgentSessionStarted.learned], so a learned
     * command keeps its mark whether or not it needed the card (B-7; fork 4-5, review S4).
     */
    fun runInterpretation() {
        when (val reading = _interpretation.value ?: return) {
            is Interpretation.Navigate -> when (reading.destination) {
                SidrDestination.LAUNCHER_SETTINGS -> applyOutcome(CommandOutcome.OpenSettings)
                // Final review A1, then phase 3b Task 7 (fork 3b-F): the card promises the app grid, and
                // `ShowApps` itself opens the drawer now — the one navigate, shared with FastPath's «apps».
                SidrDestination.APP_GRID -> applyOutcome(CommandOutcome.ShowApps)
            }
            is Interpretation.Act -> {
                val plan = reading.plan() ?: return // a pick is still owed
                _interpretation.value = null
                val teaches = learnable
                val mark = learned
                clearLearnedBesideCard()
                for (pick in picks.sortedBy { it.step }) {
                    val token = pick.learning ?: continue
                    appLaunch.rememberLearningToken(token)
                    appLaunch.recordChoiceIfPending(pick.packageName)
                }
                picks.clear()
                scope.launch {
                    val started = (startAgentSession.startPlanned(reading.goal, plan) as? OperationResult.Success)?.value
                    if (started == null) {
                        applyOutcome(CommandOutcome.Failed(CommandFailure.Generic))
                        return@launch
                    }
                    teaches?.let(::recordLearned)
                    applyOutcome(CommandOutcome.AgentSessionStarted(started.id, mark))
                }
            }
        }
    }

    /**
     * A tap on a 2b pick (spec criterion 15, second half): the domain's one binding function writes the
     * package and the user's words into the pending step ([bind], controller ruling E5-P2 — not re-spelled
     * here), and the session starts at once through `startPlanned` — no interpretation card in between.
     * The engine gates the step by its risk, so `uninstall_app` still stops at its consent card. The pick
     * is taken down BEFORE the start, so a second tap finds nothing to start; a package that was not
     * offered changes nothing.
     */
    fun chooseTarget(packageName: String) {
        val target = _targetPick.value ?: return
        val plan = target.bind(packageName) ?: return
        _targetPick.value = null
        scope.launch {
            val started = (startAgentSession.startPlanned(target.goal, plan) as? OperationResult.Success)?.value
            applyOutcome(
                if (started != null) CommandOutcome.AgentSessionStarted(started.id)
                else CommandOutcome.Failed(CommandFailure.Generic),
            )
        }
    }

    /**
     * The remainder of a model plan that acted — «Продолжить» on it (`Resumption.NeedsCard`) or «Повторить
     * остаток» on its finished card (A4′ phase 3b, Task 11; fork 3b-A, B-10). Its apps are bound AGAIN on the
     * device ([MentionBinder.rebind]) and what that finds reaches the screen through step 7's own mapping
     * ([toCommandOutcome], controller ruling E11-P5): the interpretation card — the same card, so its picks,
     * their tokens and «Выполнить» work exactly as for a fresh reading (B-19) — or «нет такого приложения»,
     * «не понял», a failure. No model is asked; nothing runs before «Выполнить», which starts a NEW session.
     * A command still waiting on the model is abandoned first ([supersedeWaitingCommand]); the binding rides
     * [pending], so an edit abandons it too.
     */
    fun showRemainder(goal: AgentGoal, plan: ExecutionPlan) {
        supersedeWaitingCommand()
        // A4′ phase 4 (B-8): a remainder is not a fresh reading — it teaches nothing, and carries no mark.
        clearLearnedBesideCard()
        pending = scope.launch { applyOutcome(binder.rebind(goal, plan).toCommandOutcome()) }
    }

    /** «Отмена» on a 2b pick: nothing starts. */
    fun dismissTargetPick() {
        _targetPick.value = null
    }

    /** «Отмена» on the card — at any pick or at Run: nothing runs, nothing is learned. */
    fun dismissInterpretation() {
        _interpretation.value = null
        picks.clear()
        clearLearnedBesideCard()
        appLaunch.rememberLearningToken(null)
    }

    /** The card's lesson and mark go with the card (B-8): nothing is learned from a card that did not run. */
    private fun clearLearnedBesideCard() {
        learnable = null
        learned = null
    }

    /**
     * Records what a started fresh reading teaches (B-8), fire-and-forget on [applicationScope] — exactly as
     * `LauncherAppLaunch.recordChoiceIfPending` records a choice: the write survives the user leaving the screen,
     * and a failed or aborted record never surfaces (the session already started).
     */
    private fun recordLearned(shape: LearnedPlanShape) {
        applicationScope.launch {
            try {
                recordLearnedPlan.record(shape)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Non-critical — the session already started; the lesson is lost, nothing else is.
            }
        }
    }

    /** The command form the dev console shows for the web chip's search (display-safe). */
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

    /** One pick made on the card: the step it bound, that pick's own learning token (if any), the package. */
    private data class CardPick(val step: Int, val learning: ResolutionLearningToken?, val packageName: String)

    private companion object {
        // The WEB chip's de-duplication: a buffer already led by the search verb is not searched for it.
        const val SEARCH_VERB_PREFIX = "search "
        // SavedStateHandle key for the typed command text (H3 process-death restoration).
        const val KEY_COMMAND_INPUT = "command_input"
        // A4′ phase 3a (plan §0.2 B-10): how long a command may take before the wait is shown.
        const val THINKING_DELAY_MS = 300L
    }
}
