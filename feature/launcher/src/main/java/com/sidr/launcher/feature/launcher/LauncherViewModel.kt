package com.sidr.launcher.feature.launcher

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.di.ApplicationScope
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.feature.launcher.agent.TIER0_SHOW_ALARMS
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.alarm.NextAlarm
import com.sidr.launcher.domain.alarm.NextAlarmSource
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepUndo
import com.sidr.launcher.domain.agent.UndoAgentSessionUseCase
import com.sidr.launcher.domain.agent.UnresolvedTarget
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.ForgetLearnedPlanUseCase
import com.sidr.launcher.domain.memory.plan.RecordLearnedPlanUseCase
import com.sidr.launcher.domain.memory.resolution.RecordResolutionChoiceUseCase
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.input.UniversalInputRouter
import com.sidr.launcher.domain.preferences.FavoriteEdits
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.SuggestionsCacheRepository
import com.sidr.launcher.domain.preferences.UserPreferences
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.domain.prayer.PrayerContext
import com.sidr.launcher.domain.prayer.UnavailableReason
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionEngine
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.voice.SpeechInputSource
import com.sidr.launcher.domain.voice.SpeechRecognitionError
import com.sidr.launcher.feature.launcher.agent.LauncherAgentSession
import com.sidr.launcher.feature.launcher.agent.DynamicToolLabel
import com.sidr.launcher.feature.launcher.agent.DynamicToolLabels
import com.sidr.launcher.feature.launcher.agent.StepProvenance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    // S2-2 Task 8: alias resolution wraps S2-1's learned-resolution decorator. It only fills Unknown,
    // so no-alias and every non-Unknown outcome stay byte-for-byte on the existing rule/model path.
    private val resolveCommand: ResolveCommandWithAliasUseCase,
    // S2-1 Task 11: records an explicit candidate choice from the app-ambiguity flow, fire-and-forget
    // on [applicationScope]. Never consulted for decision-making in the VM — only invoked after a
    // successful launch of a candidate the pending token actually offered.
    private val recordResolutionChoice: RecordResolutionChoiceUseCase,
    // A4′ phase 4, Task 7 (plan §0.2 B-8, B-10): «Выполнить» on a fresh model card records what it teaches —
    // once its session started, on [applicationScope] like a learned choice — and «Забыть» on the card of a
    // learned command's session forgets its form. Domain use cases over the `LearnedPlanStore` port.
    private val recordLearnedPlan: RecordLearnedPlanUseCase,
    private val forgetLearnedPlan: ForgetLearnedPlanUseCase,
    // The web chip's act (resolve → execute) via the same proven resolver/executor path. Since A4′ phase
    // 3a deleted the router's confirm card, the chip is its only caller — never the model's path.
    private val executeAction: ExecuteActionUseCase,
    private val actionExecutor: ActionExecutor,
    // Domain interfaces — injected from :app via Hilt. No feature→data edge.
    private val usageHistoryRepository: UsageHistoryRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val suggestionEngine: SuggestionEngine,
    private val suggestionsCacheRepository: SuggestionsCacheRepository,
    // Voice input modality (Block T). Produces the same text the keyboard does; rides the existing
    // command path. The launcher core never depends on it — when unavailable the mic is hidden.
    private val speechInputSource: SpeechInputSource,
    // AIL-6: drives the home status line (● online / ○ offline). Domain port — the VM stays
    // Android-free; reachability signals whether the cloud model/assistant is available.
    private val connectivityChecker: ConnectivityChecker,
    // DS-6B Task 9: the sanctioned production addition — the opt-in prayer context for the Home
    // strip. Its own [prayerContext] StateFlow is cold + WhileSubscribed, so nothing calculates on
    // the construction/startup path before the UI actually subscribes.
    private val getPrayerContext: GetPrayerContextUseCase,
    // Task 12 / A0: the agent runtime's four ports. All four are lazy `@Singleton`s in the Hilt
    // graph, so injecting them here costs a reference and no work; the only startup touch is the one
    // `store.active()` read [LauncherAgentSession.restoreOnStart] makes off the main thread.
    private val runAgentSession: RunAgentSessionUseCase,
    private val resolveAgentConsent: ResolveConsentUseCase,
    private val cancelAgentSession: CancelAgentSessionUseCase,
    private val resumeAgentSession: ResumeAgentSessionUseCase,
    private val undoAgentSession: UndoAgentSessionUseCase,
    private val agentSessionStore: AgentSessionStore,
    // A4′ phase 3a: the interpretation card's «Выполнить» starts the model's plan through the engine.
    private val startAgentSession: StartAgentSessionUseCase,
    // A4′ phase 3b, Task 11 (fork 3b-A): the remainder of a model plan that acted is bound again on the
    // device before its card. A domain port — `:app` binds `DeviceMentionBinder`; no feature -> data edge.
    private val mentionBinder: MentionBinder,
    // Task 10 / A1': read-only lookup of a registered tool's descriptor (the shape the deleted router's
    // `ActionCatalog` lookup had here). The surface must be able to say where an EXTERNAL tool's effect
    // goes (`DOC-ILM-2`), and provenance lives on the descriptor — a plan step carries the tool's id
    // and its risk, not its level or effect. The registry is a domain port, so the VM stays
    // Android-free and no feature -> data edge appears.
    private val toolRegistry: ToolRegistry,
    // A1″ Task 8: the names of tools whose names are DATA — one per app shortcut another installed app
    // publishes. A separate port from [toolRegistry] because a `ToolDescriptor` carries no user-facing
    // copy by contract, and because the strings are third-party: they must not cross into `:domain`.
    // `:app` projects it from `:data:repository`'s `DynamicToolNames` — there is no feature -> data edge.
    private val dynamicToolLabels: DynamicToolLabels,
    // A4′ acceptance tail, task 6 (controller ruling K9): «the model is being asked», raised by the route right before
    // it asks — the one thing the wait is shown on. A `@Singleton` of this module; `:app` binds the domain port the
    // route is built with to this same instance (`BridgeProvidesModule.provideModelAskedSignal`).
    private val modelAskedEvents: ModelAskedEvents,
    private val nextAlarmSource: NextAlarmSource,
    // A4′ acceptance tail, task 12: the time favourites' freshness (14 days) is judged by. `:app` binds
    // `Clock.systemUTC()` (`ClockModule`); the default serves a Kotlin caller only — Hilt injects the binding.
    private val clock: Clock = Clock.systemUTC(),
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    // S2-1 Task 11: fire-and-forget scope for recording a learned choice — survives the launch's own
    // viewModelScope coroutine (Block-F recordUsage precedent) so a quick nav-away never drops it.
    @ApplicationScope private val applicationScope: CoroutineScope,
    // Survives process death — the user's typed command text is restored on relaunch (H3).
    // Hilt auto-provides this for @HiltViewModel; tests pass a SavedStateHandle() directly.
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // ── Navigation events (Channel pattern from 3.1.x — unchanged) ────────
    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<NavigationEvent> = _navigationEvents.receiveAsFlow()

    fun navigateTo(route: String) {
        _navigationEvents.trySend(NavigationEvent.NavigateTo(route))
    }

    /**
     * A user gesture on Home that opens another screen — the ASK chip, the prayer strip's details
     * (A4′ phase 3a, Task 11 fix round 1, review m2). The user has gone somewhere else, so a command still
     * waiting on the model is abandoned first; left running, its slow answer would queue a navigation of
     * its own (`OpenAssistant`) that fires the next time Home collects events, or put a card up behind the
     * screen the user chose. Deliberately NOT [navigateTo]: that is also the path the command session's
     * `onNavigate` takes for an outcome's OWN navigation, from inside the command's coroutine, and the
     * session must never cancel the command it is applying.
     */
    fun navigateByUser(route: String) {
        commandSession.supersedeWaitingCommand()
        navigateTo(route)
    }

    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }

    /**
     * The next alarm the CLOCK set, for the date line's chip (A4′ acceptance tail, task 8; owner ruling 5: а), or `null`
     * — no alarm, or one another app set (the port filters it). Cold and `WhileSubscribed`, like [prayerContext]: the
     * source registers its receiver only while Home is on screen, and nothing reads the alarm on the startup path. A
     * failing source is no chip, never a crash.
     */
    val nextAlarm: StateFlow<NextAlarm?> = nextAlarmSource.observe()
        .catch { emit(null) }
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    // ── App-list state ─────────────────────────────────────────────────────
    // Этап 4 / A0: extracted to LauncherSuggestions — see observeSuggestionFlag()'s old call site
    // (now suggestionsSection.observeFlag()) below, wired from init.
    private val suggestionsSection = LauncherSuggestions(
        suggestionEngine = suggestionEngine,
        suggestionsCacheRepository = suggestionsCacheRepository,
        featureFlagRepository = featureFlagRepository,
        ioDispatcher = ioDispatcher,
        scope = viewModelScope,
    )

    // Task 3 / A0: extracted to LauncherAppList — the app-inventory load, the usage-aware grid sort,
    // favorites derivation and the combine()/stateIn() that used to build [uiState] here directly (see
    // that class's kdoc). Takes [suggestionsSection] per the controller's Ruling R1 so label
    // resolution against the loaded apps also moves out of this class.
    private val appList = LauncherAppList(
        installedAppsRepository = installedAppsRepository,
        usageHistoryRepository = usageHistoryRepository,
        userPreferencesRepository = userPreferencesRepository,
        ioDispatcher = ioDispatcher,
        scope = viewModelScope,
        suggestions = suggestionsSection,
        nowMs = { clock.millis() },
    )

    // Block X6: deferred UI preferences (favorites row size, mic toggle, first-run nudge flag).
    // Held as a hot StateFlow so both [showMic] and startVoiceInput()'s mic-enabled gate read a
    // consistent snapshot. Task 3 moved [uiState]'s own copy of this collection into LauncherAppList,
    // so this instance no longer feeds uiState — only the two voice-input read sites below do.
    // A read failure degrades to defaults (mic on, 12 favorites).
    private val userPreferences: StateFlow<UserPreferences> =
        userPreferencesRepository.getPreferences()
            .catch { emit(UserPreferences()) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = UserPreferences(),
            )

    // Whether the mic affordance is shown: the recognizer must be usable AND the user pref on.
    // Read outside the app-list UiState (the search field renders during Loading too).
    val showMic: StateFlow<Boolean> = userPreferences
        .map { it.micInputEnabled && speechInputSource.isAvailable() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = speechInputSource.isAvailable(),
        )

    // AIL-6 home status line: live network reachability (● online / ○ offline). The initial value is
    // the synchronous snapshot so the status is correct on first frame without waiting for collection.
    val isOnline: StateFlow<Boolean> = connectivityChecker.connectivity
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = connectivityChecker.isOnline(),
        )

    // DS-6B Task 9: Home's opt-in prayer strip. [GetPrayerContextUseCase.get] is a COLD flow, and
    // WhileSubscribed(5_000) means the sharing coroutine — and therefore any adhan2 calculation —
    // never starts until the UI actually collects this; construction/startup does zero calculation.
    // flowOn(ioDispatcher) keeps that recompute off the main thread once it does run. The use case
    // itself emits cache-first (CACHED_FRESH) then a fresh recompute (VERIFIED_CURRENT) — Home never
    // shows a spinner for the gap, only a quiet "Updating" label inside the strip.
    val prayerContext: StateFlow<PrayerContext> = getPrayerContext.get()
        .flowOn(ioDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PrayerContext.Unavailable(UnavailableReason.NOT_CONFIGURED),
        )

    // Task 3 / A0: extracted to LauncherAppList — the combine()/stateIn() that built this moved
    // whole (see that class's `state`); same value, same construction, delegated here under the
    // identical public name so every existing call site (VM-internal and screen-facing) is unchanged.
    val uiState: StateFlow<UiState<LauncherUiState>> get() = appList.state

    /**
     * I18N-1 Fix round 1: the typed detail behind a [OperationError.PermissionDenied]/
     * [OperationError.DeviceNotCapable] app-list load failure, exposed **alongside** [uiState] — same
     * pattern, same reason, and the same shared [AppDrawerError] type as `AppDrawerViewModel`'s
     * `loadErrorDetail` (see that property's kdoc, and [AppDrawerError]'s kdoc in
     * `LauncherPresentation.kt`): `core.common.UiState.Error` is fixed to `UiError` (no
     * typed-argument slot), so [uiState]'s own `UiError.Message` text stays the byte-identical English
     * fallback it always was. Null whenever the last result isn't one of these two argument-carrying
     * failures.
     */
    val appListErrorDetail: StateFlow<AppDrawerError?> = appList.rawAppsResult
        .map { result ->
            (result as? OperationResult.Failure)?.error?.let { error ->
                when (error) {
                    is OperationError.PermissionDenied -> AppDrawerError.PermissionDenied(error.permission)
                    is OperationError.DeviceNotCapable -> AppDrawerError.DeviceNotCapable(error.feature)
                    else -> null
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    // ── Developer Command console (AIL-3 / DF-1) — session-only, in-memory. No persisted key, so the
    // privacy denylist guard is untouched; both flags reset on process death. Two-factor unlock:
    // arm via 7 wordmark taps (screen), then submit the "//dev-mode" sentinel to toggle.
    // Этап 4 / A0: extracted to LauncherDevConsole — this delegates under the identical public names.
    private val devConsole = LauncherDevConsole()
    val devConsoleOn: StateFlow<Boolean> get() = devConsole.consoleOn
    val consoleLines: StateFlow<List<ConsoleLine>> get() = devConsole.lines

    // Task 3 / A0: extracted to LauncherAppLaunch (see that class's kdoc). Shared by the app grid and
    // the command pipeline. onFeedback forward-references [commandSession], declared below — legal
    // because the lambda only runs after construction finishes (same pattern as [voiceInput]'s onFinal).
    // The explicit `: LauncherAppLaunch`/`: LauncherCommandSession` types on this pair are required, not
    // stylistic — the mutual forward reference otherwise trips a Kotlin compiler recursive-inference bug.
    private val appLaunch: LauncherAppLaunch = LauncherAppLaunch(
        actionExecutor = actionExecutor,
        recordResolutionChoice = recordResolutionChoice,
        applicationScope = applicationScope,
        scope = viewModelScope,
        onFeedback = { feedback -> commandSession.showFeedback(feedback) },
    )

    // Task 12 / A0: the seventh collaborator — the agent runtime. Declared BEFORE [commandSession]
    // because that class takes it: `applyOutcome`'s AgentSessionStarted branch hands it the id of the
    // session Task 11's cut already started and persisted. The dependency is one-way, CommandSession
    // -> AgentSession; nothing here reads the command pipeline.
    private val agentSession: LauncherAgentSession = LauncherAgentSession(
        runSession = runAgentSession,
        resolveConsent = resolveAgentConsent,
        cancelSession = cancelAgentSession,
        store = agentSessionStore,
        scope = viewModelScope,
        resumeSession = resumeAgentSession,
        undoSession = undoAgentSession,
        // A4′ phase 3a (condition 4): forward-references [commandSession], declared below — legal for the
        // same reason as [appLaunch]'s onFeedback: the lambda runs only after construction.
        onReroute = { text -> commandSession.submit(text) },
        // A4′ phase 3b, Task 11 (fork 3b-A): the remainder of a model plan that acted goes to the
        // interpretation card, bound again on the device — the same forward reference as [onReroute].
        onRemainder = { goal, plan -> commandSession.showRemainder(goal, plan) },
        forgetLearnedPlan = forgetLearnedPlan,
        // A4′ acceptance tail, task 4 (owner answer I12): a simple session took its card down — the launcher goes back
        // to rest exactly as «Закрыть» puts it there ([leaveAgentSurface]), but only while the buffer still holds the
        // command that started it: a newer command typed meanwhile is the user's, and is not cancelled. The same
        // forward reference as [onReroute].
        onClosedItself = { goal -> if (commandSession.input.value.trim() == goal.text.trim()) leaveAgentSurface() },
    )

    /** The agent runtime's current session, or null when none is on screen. */
    val agentSessionState: StateFlow<AgentSession?> get() = agentSession.session

    /** Whether a consent decision is in flight — bound to the gate's `confirming`. */
    val agentConfirming: StateFlow<Boolean> get() = agentSession.confirming

    /** What «Отменить» would undo on the agent card now on screen (A4′ phase 2). */
    val agentUndoReview: StateFlow<List<StepUndo>> get() = agentSession.undoReview

    /** A4′ phase 3b, Task 11: «Продолжить» could not be carried out — the store failed (see [LauncherAgentSession.resumeFailed]). */
    val agentResumeFailed: StateFlow<Boolean> get() = agentSession.resumeFailed

    /** A4′ phase 3b, Task 11: whether the finished model-plan card on screen offers «Повторить остаток». */
    val agentRemainderOffered: StateFlow<Boolean> get() = agentSession.remainderOffered

    /** A4′ phase 4, Task 7 (B-10): the form of the learned command whose session is on screen, or null. */
    val agentLearnedCommand: StateFlow<CommandForm?> get() = agentSession.learned

    /** A4′ acceptance tail, task 4 (K4): the session on screen was started by a fresh model reading without the card. */
    val agentProposedByModel: StateFlow<Boolean> get() = agentSession.proposedByModel

    /**
     * Task 10 / A1'. The registry's provenance, projected to the two fields the surface may see, so the
     * agent surface renders `DOC-ILM-2`'s disclosure without ever holding a [ToolRegistry] itself. The
     * VM resolves typed values; the feature layer picks the string — the hard rule's split, unchanged.
     *
     * A plain immutable value rather than a second `StateFlow`: [ToolRegistry] is documented read-only
     * and side-effect free, and a separate flow could momentarily disagree with the session snapshot
     * rendered beside it, which would blank a provenance line for one frame.
     *
     * **`get()` since A1″, and the change is a correctness fix rather than a style choice.** This was
     * `by lazy`, justified by "a federation composed once at graph construction, so there is nothing to
     * observe". That premise died with adapter #3: the `app_shortcut` source's tool set moves while the
     * process lives (it is empty until `ShortcutRefreshTrigger` has run at all, and changes whenever an
     * app is installed or removed), and `ToolFederation` stopped deriving its snapshot in the
     * constructor for exactly that reason. Held once, this map would have been the registry as of the
     * first agent session ever shown, and every shortcut tool discovered after it would have rendered
     * with no provenance line at all — a silent `DOC-ILM-2` miss on the one level whose effect goes to
     * another app.
     *
     * Nothing walks the federation on the startup path either way: this is read only while an agent
     * session is on screen.
     */
    internal val agentToolProvenance: Map<ToolId, StepProvenance>
        get() = toolRegistry.all().associate { it.id to StepProvenance(it.level, it.effect) }

    /**
     * A1″ Task 8. The other half of a step's line, for a tool the surface has no sentence for.
     *
     * Read on access for the same reason as [agentToolProvenance], and from a port of its own because
     * a name that came from data is not a `ToolDescriptor` field: the descriptor carries no user-facing
     * copy by contract, and these strings belong to the apps that wrote them.
     *
     * This ViewModel deliberately does **not** join the two halves into one string. That join is copy,
     * and the hard rule puts copy in the feature layer's resources, not in a ViewModel — see
     * `AgentSessionPresentation.line`, which passes both halves to `launcher_agent_step_shortcut`.
     */
    internal val agentDynamicToolLabels: Map<ToolId, DynamicToolLabel>
        get() = dynamicToolLabels.current()

    // Task 4 / A0: extracted to LauncherCommandSession — the whole command pipeline (see that class's
    // kdoc). [onNavigate] forwards to this class's own nav Channel, its one piece of retained state.
    private val commandSession: LauncherCommandSession = LauncherCommandSession(
        resolveCommand = resolveCommand,
        executeAction = executeAction,
        universalInputRouter = UniversalInputRouter,
        savedStateHandle = savedStateHandle,
        appLaunch = appLaunch,
        appList = appList,
        devConsole = devConsole,
        agentSession = agentSession,
        startAgentSession = startAgentSession,
        binder = mentionBinder,
        recordLearnedPlan = recordLearnedPlan,
        modelAsked = modelAskedEvents.events,
        applicationScope = applicationScope,
        scope = viewModelScope,
        onNavigate = { route -> navigateTo(route) },
    )

    val commandInput: StateFlow<String> get() = commandSession.input
    val inputResults: StateFlow<HomeInputResults> get() = commandSession.liveResults
    val commandFeedback: StateFlow<CommandFeedback> get() = commandSession.feedback

    /** A4′ phase 3a: the model's reading, shown before anything runs (spec §7.12.3 condition 1). */
    val interpretation: StateFlow<Interpretation?> get() = commandSession.interpretation

    /**
     * The model is being asked about the command now pending (A4′ phase 3a; since the acceptance tail's task 6, K9, from
     * the moment the route asks it — no 300 ms timer: a slow answer on the device shows nothing).
     */
    val thinking: StateFlow<Boolean> get() = commandSession.thinking

    /** A4′ phase 3b (B-7): a target step 2b claimed, waiting on the user's pick — not an interpretation card. */
    val targetPick: StateFlow<UnresolvedTarget?> get() = commandSession.targetPick

    // Этап 4 / A0: extracted to LauncherVoiceInput — see startVoiceInput() below for the wiring. The
    // callbacks forward-reference [commandSession] the same way [appLaunch]'s onFeedback does above.
    private val voiceInput = LauncherVoiceInput(
        speechInputSource = speechInputSource,
        scope = viewModelScope,
        // Task 9 review m1: a partial is the user dictating a new command, so it rides the same edit
        // path as typing — it dismisses a standing interpretation card and abandons a command still
        // waiting on its answer. (A partial cannot follow the Final it belongs to: the recognizer's
        // flow closes on Final, so this never cancels the command onFinal submits.)
        onPartial = { text -> commandSession.onChanged(text) },
        onFinal = { text ->
            // Final review M1: a recognizer may deliver a Final with no Partial before it, so the Final
            // rides the edit path too — the card for the previous command goes now, not when this one's
            // answer lands. The edit runs BEFORE the submit, so it cancels only what was already waiting.
            commandSession.onChanged(text)
            // Same entry point as the keyboard's IME "Done" / submit.
            commandSession.submit(text)
        },
        onError = { error -> commandSession.showFeedback(CommandFeedback.VoiceError(error)) },
    )

    /** Whether a speech recognizer is usable. The UI shows the mic affordance only when true. */
    val isVoiceInputAvailable: Boolean
        get() = speechInputSource.isAvailable()

    init {
        appList.load()
        suggestionsSection.observeFlag()
        // Task 12 / A0: anything that outlived the last process is presented as Paused with an
        // offer — never resumed silently. A store with nothing in it is a no-op, which is every
        // launch that did not end mid-plan.
        agentSession.restoreOnStart()
    }

    /**
     * Re-attempt the app-list load after a recoverable [UiState.Error] — no process restart.
     * Resetting to null keeps the cache-first home shell visible before the reload emits its result.
     */
    fun retry() {
        appList.retry()
    }

    // ── UI actions ─────────────────────────────────────────────────────────

    fun onCommandChanged(text: String) {
        commandSession.onChanged(text)
    }

    /** Arm the hidden developer console (called by the screen after 7 rapid wordmark taps). */
    fun armDevMode() {
        devConsole.arm()
        commandSession.showFeedback(CommandFeedback.Message("dev mode armed — submit //dev-mode"))
    }

    fun onCommandSubmitted(text: String) {
        commandSession.submit(text)
    }

    /** Web-search route chip (AIL-3) — see [LauncherCommandSession.submitWebSearch]. */
    fun submitWebSearch(query: String) {
        commandSession.submitWebSearch(query)
    }

    /** Open-site route chip (AIL-3) — see [LauncherCommandSession.submitSite]. */
    fun submitSite(query: String) {
        commandSession.submitSite(query)
    }

    fun onSuggestionClicked(suggestion: Suggestion) {
        // A4′ phase 3a (Task 11, hole H8): a suggestion is the user going somewhere else — a screen, an
        // app — so a command still waiting on the model is abandoned here, for every branch below. The app
        // branch calls it again through onAppClicked; a second call finds nothing pending.
        commandSession.supersedeWaitingCommand()
        val app = (uiState.value as? UiState.Success)
            ?.data
            ?.apps
            ?.firstOrNull { it.packageName == suggestion.actionId }
        when {
            app != null -> onAppClicked(app)
            suggestion.actionId.isKnownRoute() -> {
                commandSession.dismissFeedback()
                navigateTo(suggestion.actionId)
            }
            else -> appLaunch.launch(
                packageName = suggestion.actionId,
                activityName = null,
            )
        }
    }

    /** Tap-to-launch from the grid (or from an ambiguity suggestion): the app is already known, */
    /** so launch it directly through the executor — no matching needed. Does not touch input. */
    fun onAppClicked(app: InstalledApp) {
        // A4′ phase 3a (T9 m2): the user opened an app — a command still waiting on the model is abandoned,
        // or its late answer would navigate away from the app or stand a card over it.
        commandSession.supersedeWaitingCommand()
        appLaunch.launch(
            packageName = app.packageName,
            activityName = app.activityName,
            onResult = { success -> if (success) appLaunch.recordChoiceIfPending(app.packageName) },
        )
    }

    fun dismissFeedback() {
        commandSession.dismissFeedback()
    }

    /**
     * A tap on the date line's next-alarm chip (A4′ acceptance tail, task 8; controller ruling K7): the clock's alarm
     * list opens as a session of ONE `show_alarms` step through the engine — [LauncherCommandSession.startAtOnce], the
     * `startPlanned` path a 2b pick takes — never a direct intent from here: `ToolExecutor` stays the only path to the
     * world (`ToolExecutorCallSiteGuardTest`), and the web chip stays the launcher's one direct `LauncherAction`
     * (`OneModelPathGuardTest`). The step is `SAFE` and acts with nothing to undo, so the session closes itself (owner
     * answer I12) — Home is left as it was. The goal has no words: the user typed none, so none is stored.
     */
    fun onAlarmChipTapped() {
        commandSession.startAtOnce(SHOW_ALARMS_GOAL, showAlarmsPlan())
    }

    // ── Voice input (Block T) ──────────────────────────────────────────────
    // The mic affordance calls this once the RECORD_AUDIO permission is held (the screen gates it
    // via checkSelfPermission, routing to permission education otherwise). Partial hypotheses stream
    // into the command input; the Final result is submitted through the UNCHANGED command path —
    // voice produces byte-identical text to the keyboard (HandleUserCommandUseCase is untouched).
    fun startVoiceInput(languageTag: String? = null) {
        // Block X6: the user can disable voice input in Settings. A stale mic tap (or a caller that
        // bypasses the showMic gate) becomes a no-op rather than starting the recognizer.
        if (!userPreferences.value.micInputEnabled) return
        if (!speechInputSource.isAvailable()) {
            commandSession.showFeedback(CommandFeedback.VoiceError(SpeechRecognitionError.UNAVAILABLE))
            return
        }
        voiceInput.start(languageTag)
    }

    fun runInterpretation() = commandSession.runInterpretation()

    fun dismissInterpretation() = commandSession.dismissInterpretation()

    fun chooseInterpretationApp(packageName: String) = commandSession.chooseApp(packageName)

    fun chooseTarget(packageName: String) = commandSession.chooseTarget(packageName)

    fun dismissTargetPick() = commandSession.dismissTargetPick()

    fun cancelThinking() = commandSession.cancelThinking()

    // ── Agent runtime (Task 12 / A0) — thin delegations, exactly like the pairs above ──────

    /** The user granted the consent checkpoint standing at [stepIndex]. */
    fun confirmAgentStep(stepIndex: Int) {
        agentSession.confirm(stepIndex)
    }

    /**
     * The user refused it. The engine cancels the session; nothing further runs — and because
     * `AgentSessionSurface` draws **nothing at all** for a plan cancelled before any step acted, the
     * surface leaves the screen on this path exactly as it does on [dismissAgentSession]. So it owes the
     * same [leaveAgentSurface].
     *
     * A plan refused AFTER a step acted (A4′ phase 3b, Task 12 — phase 2's N2, plan §0.2 B-11) keeps its
     * finished card on screen instead: the session stays in [agentSessionState] (`Cancelled`), and the card
     * says what acted and offers «Отменить» when it can be undone. The buffer is cleared all the same, so
     * the home body is back under that card; its «Закрыть» is [dismissAgentSession] and its «Отменить»
     * [undoAgentSession], as on every finished card.
     */
    fun denyAgentStep(stepIndex: Int) {
        agentSession.deny(stepIndex)
        leaveAgentSurface()
    }

    /**
     * Pick a paused plan back up. The engine re-evaluates, so a pending checkpoint returns.
     *
     * No [leaveAgentSurface] here, and none in [confirmAgentStep] either. Every state the engine can
     * reach from them still *draws* a surface, and each of those carries its own «закрыть» — `Cancelled`,
     * the one state drawn invisible (unless a step acted before it — N2, A4′ phase 3b Task 12, and then it
     * carries its own «закрыть» too), is reachable only by an explicit cancel or a refusal. The exception is
     * «Продолжить» on a model-written plan (A4′ phase 3a, `Resumption.NeedsRoute`): the session is
     * closed and the surface goes, and its goal is submitted through the command route afresh — what
     * the user sees next is that command's own outcome (a wait, a card, a feedback line). Calling
     * [leaveAgentSurface] there would be wrong, not merely unneeded: its edit would cancel that command.
     * The same holds for «Продолжить» on a model-written plan that ACTED (A4′ phase 3b, Task 11,
     * `Resumption.NeedsCard`): the session is closed and its remainder comes back as the interpretation
     * card, bound again on the device — an edit here would take that card down. A resume the store fails
     * takes the session's surface down and says so in its place (`LauncherAgentSession.continueSession`'s
     * `null` branch, [agentResumeFailed]); its «Закрыть» is [dismissAgentSession].
     */
    fun continueAgentSession() {
        agentSession.continueSession()
    }

    /**
     * «Повторить остаток» on a finished `Failed`/`Blocked` model-plan card (A4′ phase 3b, Task 11): its
     * remainder comes back as the interpretation card — see [LauncherAgentSession.retryRemainder]. No
     * [leaveAgentSurface], for the reason [continueAgentSession] gives.
     */
    fun retryAgentRemainder() {
        agentSession.retryRemainder()
    }

    /**
     * «Забыть» on a learned command's card (A4′ phase 4, B-10): its form is forgotten — see
     * [LauncherAgentSession.forgetLearned]. The card stays; only its mark goes. No [leaveAgentSurface].
     */
    fun onForgetLearnedCommand() {
        agentSession.forgetLearned()
    }

    /** Undo what the finished plan on screen did, as far as it can be undone — see [LauncherAgentSession.undo]. */
    fun undoAgentSession() {
        agentSession.undo()
    }

    /** Clear the agent surface and delete the session. */
    fun dismissAgentSession() {
        agentSession.cancel()
        leaveAgentSurface()
    }

    /**
     * Put the launcher back at rest once the agent surface has gone away.
     *
     * **Why this is not the same thing as deleting the session** (owner device acceptance,
     * 2026-09-10). "Search overtakes" is a pure function of the command buffer —
     * `LauncherCommandSession.liveResults` classifies the typed text and reports `active`, and the
     * screen hides the Shahada, the date line and the prayer strip while that is true. Deleting the
     * session removed the surface and left the buffer holding the command, so the home body never came
     * back and only a force-stop restored it. The buffer is the whole of the search-active state:
     * there is no second flag to reset, which is why clearing it is sufficient and why the fix is the
     * same call the input's own ✕ makes (`onCommandChanged("")`).
     *
     * It rides the ordinary edit path on purpose: that also drops stale feedback, any interpretation
     * card, a command still waiting on the model, and the ambiguity learning token, so leaving the agent
     * surface is byte-for-byte the state a user reaches by clearing the field by hand.
     */
    private fun leaveAgentSurface() {
        commandSession.onChanged("")
    }

    /**
     * Dismiss the one-shot first-run "set as default launcher" nudge (Block X6). Persisted so the
     * hint never resurfaces. Idempotent; a write failure leaves the flag unset (the nudge may show
     * again — acceptable for a purely advisory hint).
     */
    fun dismissSetupHint() {
        appList.dismissSetupHint()
    }

    /**
     * Pin [packageName] to Home's favourites, after the apps already pinned; a hidden app is unhidden (A4′ acceptance
     * tail, task 12; owner ruling 15). The rule is [FavoriteEdits]'s; this reads, applies and writes through the
     * preferences repository, like the Settings count. A failed write changes nothing — the picker's switches read
     * the stored preferences, so they simply do not move.
     */
    fun pinFavorite(packageName: String) = editFavorites { FavoriteEdits.pin(it, packageName) }

    /** Unpin [packageName]: it competes for an automatic place again. */
    fun unpinFavorite(packageName: String) = editFavorites { FavoriteEdits.unpin(it, packageName) }

    /** «Убрать с главного»: take [packageName] out of the automatic places (and unpin it); its usage history stays. */
    fun hideFavorite(packageName: String) = editFavorites { FavoriteEdits.hide(it, packageName) }

    // One favourites edit at a time: each is read → edit → write of the WHOLE preferences object, so two quick toggles in
    // the picker would both read the same snapshot and the second write would drop the first (lost update). The mutex
    // serialises that read-edit-write across edits; the repository interface stays as it is (no transform method).
    private val favoritesEditLock = Mutex()

    private fun editFavorites(edit: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch(ioDispatcher) {
            try {
                favoritesEditLock.withLock {
                    val current = userPreferencesRepository.getPreferences().first()
                    val edited = edit(current)
                    if (edited != current) userPreferencesRepository.updatePreferences(edited)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // A purely presentational preference: a failed read or write leaves Home as it was.
            }
        }
    }
}

// Positional, not `text = ""`: `HardcodedUiTextGuardTest` reads `"", shape = GoalShape.Free("` on such a line as one
// literal with letters (a false positive — the goal's text is empty and never shown).
/** The chip's goal (task 8): no words — the user typed none — and no recognised shape; the plan is built, not planned. */
private val SHOW_ALARMS_GOAL = AgentGoal("", GoalShape.Free(""))

/**
 * The chip's one step (task 8, K7): `show_alarms`, no argument, `SAFE` — what `Tier0IntentToolSource` declares; the
 * engine's gate takes the higher of this and the registry's risk (`AgentExecutor.effectiveRisk`). The id is
 * [TIER0_SHOW_ALARMS], the surface's spelling `DoctrineGuardTest` holds against the registry.
 */
private fun showAlarmsPlan() = ExecutionPlan(
    listOf(
        PlanStep(
            index = 0,
            invocation = ToolInvocation(ToolId(TIER0_SHOW_ALARMS), emptyMap()),
            risk = ActionRiskLevel.SAFE,
            precondition = StepPrecondition.None,
            rationale = StepRationale.GOAL_DIRECT,
        ),
    ),
)

// Task 2 / A0: moved out of LauncherViewModel's body (and widened from private to internal) so
// LauncherSuggestions.resolveLabels() — extracted out of this class — can call it too. A member
// extension can only be called with an instance of its dispatch receiver in scope, which a plain
// collaborator class does not have; a package-level extension has no such restriction. Same file,
// same logic, still package-internal — not part of any public API.
internal fun String.isKnownRoute(): Boolean =
    this == Routes.Launcher.ROUTE ||
        this == Routes.Assistant.ROUTE ||
        this == Routes.Settings.ROUTE ||
        this == Routes.PermissionEducation.ROUTE ||
        this.startsWith("${Routes.PermissionEducation.ROUTE}?")

// Task 3 / A0: widened from private-member-extension to package-level internal for the same reason
// as isKnownRoute() above — LauncherAppList's `state` combine (extracted out of this class) needs to
// call these too, and a member extension is only callable with an instance of its dispatch receiver
// in scope. Same logic, byte-for-byte, still package-internal.
// ── OperationError → UiError — exhaustive when, no else branch ─────────
// Add a new branch here whenever OperationError gains a new subtype.
// I18N-1 Fix round 1: left byte-identical to its pre-Task-12 shape on purpose (structurally
// analogous to AppDrawerViewModel's twin — see that file's toUiError() comment) — the real typed,
// live seam is [LauncherViewModel.appListErrorDetail], not a construct-then-discard AppDrawerError
// built only to be flattened back into English here.
internal fun OperationError.toUiError(): UiError = when (this) {
    is OperationError.NetworkError     -> UiError.Network
    is OperationError.AiUnavailable    -> UiError.Unknown
    is OperationError.PermissionDenied -> UiError.Message("Permission denied: $permission")
    is OperationError.DeviceNotCapable -> UiError.Message("Not supported: $feature")
    is OperationError.UnknownError     -> UiError.Unknown
}

// Whether re-running the load could plausibly succeed (per architecture.md error categories).
// Network/Unknown are offered a retry ("generic recovery"); PermissionDenied/DeviceNotCapable/
// AiUnavailable are not button-fixable — granting/capability/fallback are handled elsewhere.
// Exhaustive when, no else — add a branch when OperationError gains a subtype.
internal fun OperationError.isRetryable(): Boolean = when (this) {
    is OperationError.NetworkError     -> true
    is OperationError.UnknownError     -> true
    is OperationError.AiUnavailable    -> false
    is OperationError.PermissionDenied -> false
    is OperationError.DeviceNotCapable -> false
}
