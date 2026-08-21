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
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.resolution.RecordResolutionChoiceUseCase
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.input.UniversalInputRouter
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
import com.sidr.launcher.domain.voice.SpeechInputSource
import com.sidr.launcher.domain.voice.SpeechRecognitionError
import com.sidr.launcher.feature.launcher.agent.LauncherAgentSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    // S2-2 Task 8: alias resolution wraps S2-1's learned-resolution decorator. It only fills Unknown,
    // so no-alias and every non-Unknown outcome stay byte-for-byte on the existing rule/router path.
    private val resolveCommand: ResolveCommandWithAliasUseCase,
    // S2-1 Task 11: records an explicit candidate choice from the app-ambiguity flow, fire-and-forget
    // on [applicationScope]. Never consulted for decision-making in the VM — only invoked after a
    // successful launch of a candidate the pending token actually offered.
    private val recordResolutionChoice: RecordResolutionChoiceUseCase,
    // AIL-5: executes a *confirmed* router-proposed LauncherAction (resolve → execute) via the same
    // proven resolver/executor path. Only reached from confirmRoutedAction() — never on submit.
    private val executeAction: ExecuteActionUseCase,
    private val actionExecutor: ActionExecutor,
    // AIL-5: read-only lookup of a proposed action's descriptor (its risk drives needsConfirmation in
    // RouteCommandUseCase; here it supplies the optional permissionGate for the education flow).
    private val actionCatalog: ActionCatalog,
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
    // Android-free; reachability signals whether the cloud router/assistant is available.
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
    private val agentSessionStore: AgentSessionStore,
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

    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }

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
    )

    // Block X6: deferred UI preferences (favorites row size, mic toggle, first-run nudge flag).
    // Held as a hot StateFlow so both [showMic] and startVoiceInput()'s mic-enabled gate read a
    // consistent snapshot. Task 3 moved [uiState]'s own copy of this collection into LauncherAppList,
    // so this instance no longer feeds uiState — only the two voice-input read sites below do.
    // A read failure degrades to defaults (mic on, 8 favorites).
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
        usageHistoryRepository = usageHistoryRepository,
        featureFlagRepository = featureFlagRepository,
        recordResolutionChoice = recordResolutionChoice,
        applicationScope = applicationScope,
        scope = viewModelScope,
        onFeedback = { feedback -> commandSession.showFeedback(feedback) },
    )

    // Task 12 / A0: the seventh collaborator — the agent runtime. Declared BEFORE [commandSession]
    // because that class takes it: `applyOutcome`'s AgentSessionStarted branch hands it the id of the
    // session Task 11's cut already started and persisted. The dependency is one-way, CommandSession
    // -> AgentSession; nothing here reads the command pipeline.
    private val agentSession = LauncherAgentSession(
        runSession = runAgentSession,
        resolveConsent = resolveAgentConsent,
        cancelSession = cancelAgentSession,
        store = agentSessionStore,
        scope = viewModelScope,
    )

    /** The agent runtime's current session, or null when none is on screen. */
    val agentSessionState: StateFlow<AgentSession?> get() = agentSession.session

    /** Whether a consent decision is in flight — bound to the gate's `confirming`. */
    val agentConfirming: StateFlow<Boolean> get() = agentSession.confirming

    // Task 4 / A0: extracted to LauncherCommandSession — the whole command pipeline (see that class's
    // kdoc). [onNavigate] forwards to this class's own nav Channel, its one piece of retained state.
    private val commandSession: LauncherCommandSession = LauncherCommandSession(
        resolveCommand = resolveCommand,
        executeAction = executeAction,
        actionCatalog = actionCatalog,
        universalInputRouter = UniversalInputRouter,
        savedStateHandle = savedStateHandle,
        appLaunch = appLaunch,
        appList = appList,
        devConsole = devConsole,
        agentSession = agentSession,
        scope = viewModelScope,
        onNavigate = { route -> navigateTo(route) },
    )

    val commandInput: StateFlow<String> get() = commandSession.input
    val inputResults: StateFlow<HomeInputResults> get() = commandSession.liveResults
    val commandFeedback: StateFlow<CommandFeedback> get() = commandSession.feedback
    val pendingRoutedAction: StateFlow<PendingRoutedAction?> get() = commandSession.pendingRoutedAction

    // Этап 4 / A0: extracted to LauncherVoiceInput — see startVoiceInput() below for the wiring. The
    // callbacks forward-reference [commandSession] the same way [appLaunch]'s onFeedback does above.
    private val voiceInput = LauncherVoiceInput(
        speechInputSource = speechInputSource,
        scope = viewModelScope,
        onPartial = { text -> commandSession.setCommandInput(text) },
        onFinal = { text ->
            commandSession.setCommandInput(text)
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
        appLaunch.launch(
            packageName = app.packageName,
            activityName = app.activityName,
            onResult = { success -> if (success) appLaunch.recordChoiceIfPending(app.packageName) },
        )
    }

    fun dismissFeedback() {
        commandSession.dismissFeedback()
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

    /** Execute the pending router proposal (AIL-5) — see [LauncherCommandSession.confirm]. */
    fun confirmRoutedAction() {
        commandSession.confirm()
    }

    /** Dismiss the pending router proposal without executing — see [LauncherCommandSession.cancel]. */
    fun cancelRoutedAction() {
        commandSession.cancel()
    }

    // ── Agent runtime (Task 12 / A0) — thin delegations, exactly like the pairs above ──────

    /** The user granted the consent checkpoint standing at [stepIndex]. */
    fun confirmAgentStep(stepIndex: Int) {
        agentSession.confirm(stepIndex)
    }

    /** The user refused it. The engine cancels the session; nothing further runs. */
    fun denyAgentStep(stepIndex: Int) {
        agentSession.deny(stepIndex)
    }

    /** Pick a paused plan back up. The engine re-evaluates, so a pending checkpoint returns. */
    fun continueAgentSession() {
        agentSession.continueSession()
    }

    /** Clear the agent surface and delete the session. */
    fun dismissAgentSession() {
        agentSession.cancel()
    }

    /**
     * Dismiss the one-shot first-run "set as default launcher" nudge (Block X6). Persisted so the
     * hint never resurfaces. Idempotent; a write failure leaves the flag unset (the nudge may show
     * again — acceptable for a purely advisory hint).
     */
    fun dismissSetupHint() {
        appList.dismissSetupHint()
    }
}

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
