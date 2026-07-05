package com.sidr.launcher.feature.launcher

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.input.InputIntent
import com.sidr.launcher.domain.input.UniversalInputRouter
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.SuggestionsCacheRepository
import com.sidr.launcher.domain.preferences.UserPreferences
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionEngine
import com.sidr.launcher.domain.suggestions.SuggestionSource
import com.sidr.launcher.domain.voice.SpeechInputSource
import com.sidr.launcher.domain.voice.SpeechRecognitionError
import com.sidr.launcher.domain.voice.SpeechRecognitionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LauncherViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val handleUserCommand: HandleUserCommandUseCase,
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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
    // Raw app-list load result; null = the full launcher app inventory is still loading in the
    // background. Home must still paint from cheap cached state while this is null.
    private val _rawAppsResult = MutableStateFlow<OperationResult<List<InstalledApp>>?>(null)
    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())

    // Block X6: deferred UI preferences (favorites row size, mic toggle, first-run nudge flag).
    // Held as a hot StateFlow so both the derived [uiState] and the imperative voice/nudge paths
    // read a consistent snapshot. A read failure degrades to defaults (mic on, 8 favorites).
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

    // Derived state: combines the loaded app list with live usage records so the grid
    // re-sorts automatically whenever a launch is recorded (F6 demo slice), surfaces the
    // launcher-owned suggestion row state (Phase 7, Block W-lite), and carries the Block X6
    // favorites cap + first-run nudge flag through user preferences.
    val uiState: StateFlow<UiState<LauncherUiState>> = combine(
        _rawAppsResult,
        usageHistoryRepository.getUsageRecords().catch { emit(emptyList()) },
        _suggestions,
        userPreferences,
    ) { appsResult, usageRecords, suggestions, prefs ->
            when (appsResult) {
                // Full app inventory is not first-frame-critical: paint the home shell + cached
                // suggestions immediately, then fill apps/favorites once PackageManager returns.
                null -> UiState.Success(
                    LauncherUiState(
                        suggestions = resolveSuggestionLabels(suggestions, emptyList()),
                        setupHintDismissed = prefs.setupHintDismissed,
                    ),
                )
                is OperationResult.Failure ->
                    UiState.Error(appsResult.error.toUiError(), retryable = appsResult.error.isRetryable())
                is OperationResult.Success -> {
                    val sorted = sortByUsage(appsResult.value, usageRecords)
                    if (sorted.isEmpty()) UiState.Empty
                    else UiState.Success(
                        LauncherUiState(
                            apps = sorted,
                            suggestions = resolveSuggestionLabels(suggestions, sorted),
                            favorites = deriveFavorites(usageRecords, sorted, prefs.favoritesCount),
                            setupHintDismissed = prefs.setupHintDismissed,
                        ),
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Success(LauncherUiState()),
        )

    // ── Command input — independent of app-list loading ────────────────────
    // Backed by SavedStateHandle so the typed text survives process death (H3). Every writer
    // goes through setCommandInput(...) so the handle stays the single source of truth.
    val commandInput: StateFlow<String> = savedStateHandle.getStateFlow(KEY_COMMAND_INPUT, "")

    private fun setCommandInput(text: String) {
        savedStateHandle[KEY_COMMAND_INPUT] = text
    }

    // ── Universal-input live results (AIL-3) ───────────────────────────────
    // Derived purely from the buffer + the loaded app list. Enter still routes through the unchanged
    // command pipeline; this only decides what the "search overtakes" panel shows.
    val inputResults: StateFlow<HomeInputResults> = combine(
        commandInput,
        _rawAppsResult,
    ) { buffer, appsResult ->
        when (val intent = UniversalInputRouter.classify(buffer)) {
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
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeInputResults(),
    )

    // ── Command feedback — transient result of the last submitted command ──
    private val _commandFeedback = MutableStateFlow<CommandFeedback>(CommandFeedback.None)
    val commandFeedback: StateFlow<CommandFeedback> = _commandFeedback

    // ── Developer Command console (AIL-3 / DF-1) — session-only, in-memory. No persisted key, so the
    // privacy denylist guard is untouched; both flags reset on process death. Two-factor unlock:
    // arm via 7 wordmark taps (screen), then submit the "//dev-mode" sentinel to toggle.
    private val _devArmed = MutableStateFlow(false)
    private val _devConsoleOn = MutableStateFlow(false)
    val devConsoleOn: StateFlow<Boolean> = _devConsoleOn
    private val _consoleLines = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val consoleLines: StateFlow<List<ConsoleLine>> = _consoleLines

    // Tracks the in-flight app load so a new load (init or retry) cancels the previous one.
    private var loadJob: Job? = null

    // Tracks an in-flight voice recognition so a second mic tap restarts cleanly (cancelling the
    // previous collection calls destroy() on the recognizer via the impl's awaitClose).
    private var voiceJob: Job? = null

    // Tracks the live suggestion-stream collector so flag toggles can cancel/restart it cleanly.
    private var suggestionsJob: Job? = null

    /** Whether a speech recognizer is usable. The UI shows the mic affordance only when true. */
    val isVoiceInputAvailable: Boolean
        get() = speechInputSource.isAvailable()

    init {
        loadApps()
        observeSuggestionFlag()
    }

    private fun loadApps() {
        // Cancel any in-flight load first: rapid retries (double-tap) must not run parallel reloads.
        // Only the latest attempt's result is ever applied; a redundant not-yet-started load never
        // reaches the repository, and one already suspended mid-call is cancelled with its result
        // discarded — so the screen sees a single clean Loading → Success/Error, no flicker.
        loadJob?.cancel()
        loadJob = viewModelScope.launch(ioDispatcher) {
            _rawAppsResult.value = installedAppsRepository.getInstalledApps()
        }
    }

    /**
     * Re-attempt the app-list load after a recoverable [UiState.Error] — no process restart.
     * Resetting to null keeps the cache-first home shell visible before the reload emits its result.
     */
    fun retry() {
        _rawAppsResult.value = null
        loadApps()
    }

    // ── UI actions ─────────────────────────────────────────────────────────

    fun onCommandChanged(text: String) {
        setCommandInput(text)
        // Editing a new command clears stale feedback.
        _commandFeedback.value = CommandFeedback.None
    }

    /** Arm the hidden developer console (called by the screen after 7 rapid wordmark taps). */
    fun armDevMode() {
        _devArmed.value = true
        _commandFeedback.value = CommandFeedback.Message("dev mode armed — submit //dev-mode")
    }

    fun onCommandSubmitted(text: String) {
        // Additive AIL-3 pre-check: an ARMED "//dev-mode" toggles the console and is consumed here so it
        // never reaches HandleUserCommandUseCase. Un-armed, it falls through unchanged (Unknown), so the
        // command pipeline stays byte-for-byte for every real input.
        if (_devArmed.value && UniversalInputRouter.classify(text) is InputIntent.DevSentinel) {
            _devConsoleOn.value = !_devConsoleOn.value
            setCommandInput("")
            _commandFeedback.value = CommandFeedback.Message(
                if (_devConsoleOn.value) "dev console on" else "dev console off",
            )
            return
        }
        viewModelScope.launch {
            val outcome = handleUserCommand.handle(text)
            applyOutcome(outcome)
            if (_devConsoleOn.value) {
                _consoleLines.value = _consoleLines.value + ConsoleLine(text, outcomeSummary(outcome))
            }
        }
    }

    /** One-line console summary of a [CommandOutcome] (dev console only; display-safe). */
    private fun outcomeSummary(outcome: CommandOutcome): String = when (outcome) {
        CommandOutcome.Empty -> "empty"
        CommandOutcome.Executed -> "✓ executed"
        CommandOutcome.NoOp -> "no-op"
        is CommandOutcome.Message -> outcome.text
        is CommandOutcome.NeedsConfirmation -> "? ${outcome.candidates.size} candidates"
        is CommandOutcome.Suggest -> "? suggest"
        CommandOutcome.LowConfidence -> "low confidence"
        is CommandOutcome.Unknown -> "unknown"
        is CommandOutcome.Failed -> "✗ ${outcome.message}"
        CommandOutcome.OpenAssistant -> "→ assistant"
        CommandOutcome.OpenSettings -> "→ settings"
        CommandOutcome.ShowApps -> "→ apps"
        CommandOutcome.ClearInput -> "cleared"
    }

    /**
     * Web-search route chip (AIL-3): prefix the buffer with the `search` verb and route it through the
     * UNCHANGED command pipeline (AIL-2 web search, already history-redacted). No new executor path.
     */
    fun submitWebSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        onCommandSubmitted("search $q")
    }

    /**
     * Open-site route chip (AIL-3): the buffer is already a safe URL (the chip is offered only then), so
     * submitting it as-is routes to AIL-2's OpenUrl through the UNCHANGED pipeline. One-tap "submit".
     */
    fun submitSite(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        onCommandSubmitted(q)
    }

    fun onSuggestionClicked(suggestion: Suggestion) {
        val app = (uiState.value as? UiState.Success)
            ?.data
            ?.apps
            ?.firstOrNull { it.packageName == suggestion.actionId }
        when {
            app != null -> onAppClicked(app)
            suggestion.actionId.isKnownRoute() -> {
                _commandFeedback.value = CommandFeedback.None
                navigateTo(suggestion.actionId)
            }
            else -> launchApp(
                packageName = suggestion.actionId,
                activityName = null,
            )
        }
    }

    /** Tap-to-launch from the grid (or from an ambiguity suggestion): the app is already known, */
    /** so launch it directly through the executor — no matching needed. Does not touch input. */
    fun onAppClicked(app: InstalledApp) {
        launchApp(
            packageName = app.packageName,
            activityName = app.activityName,
        )
    }

    fun dismissFeedback() {
        _commandFeedback.value = CommandFeedback.None
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
            _commandFeedback.value = CommandFeedback.Message(VOICE_UNAVAILABLE)
            return
        }
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            speechInputSource.listen(languageTag).collect { state ->
                when (state) {
                    SpeechRecognitionState.Ready -> Unit
                    is SpeechRecognitionState.Partial -> setCommandInput(state.text)
                    is SpeechRecognitionState.Final -> {
                        setCommandInput(state.text)
                        // Same entry point as the keyboard's IME "Done" / submit.
                        onCommandSubmitted(state.text)
                    }
                    is SpeechRecognitionState.Error ->
                        _commandFeedback.value = CommandFeedback.Message(voiceErrorMessage(state.error))
                    SpeechRecognitionState.Ended -> Unit
                }
            }
        }
    }

    private fun voiceErrorMessage(error: SpeechRecognitionError): String = when (error) {
        SpeechRecognitionError.PERMISSION_DENIED -> "Microphone permission is needed for voice input."
        SpeechRecognitionError.UNAVAILABLE -> VOICE_UNAVAILABLE
        SpeechRecognitionError.NO_MATCH -> "Didn't catch that — try again."
        SpeechRecognitionError.BUSY -> "Voice input is busy — try again in a moment."
        SpeechRecognitionError.NETWORK -> "Voice input needs a network connection right now."
        SpeechRecognitionError.TIMEOUT -> "No speech detected — try again."
        SpeechRecognitionError.UNKNOWN -> "Voice input failed — try again."
    }

    // ── CommandOutcome → UI — exhaustive when, no else branch ──────────────
    // Add a new branch here whenever CommandOutcome gains a new variant.
    private fun applyOutcome(outcome: CommandOutcome) {
        when (outcome) {
            CommandOutcome.Empty ->
                _commandFeedback.value = CommandFeedback.Message("Type a command, e.g. \"open telegram\"")

            CommandOutcome.Executed -> {
                setCommandInput("")
                _commandFeedback.value = CommandFeedback.None
            }

            CommandOutcome.NoOp ->
                _commandFeedback.value = CommandFeedback.None

            is CommandOutcome.Message ->
                _commandFeedback.value = CommandFeedback.Message(outcome.text)

            is CommandOutcome.NeedsConfirmation ->
                _commandFeedback.value = CommandFeedback.Ambiguous(outcome.candidates)

            is CommandOutcome.Suggest ->
                _commandFeedback.value = CommandFeedback.Suggestion(describe(outcome.intent))

            CommandOutcome.LowConfidence ->
                _commandFeedback.value =
                    CommandFeedback.Message("Didn't catch that — try being more specific")

            is CommandOutcome.Unknown ->
                _commandFeedback.value =
                    CommandFeedback.Message("Unknown command. Try: open <app>, search <query>")

            is CommandOutcome.Failed ->
                _commandFeedback.value = CommandFeedback.Message(outcome.message)

            CommandOutcome.OpenAssistant -> {
                setCommandInput("")
                _commandFeedback.value = CommandFeedback.None
                // Route string belongs to the UI layer — the domain only said "OpenAssistant".
                navigateTo(Routes.Assistant.ROUTE)
            }

            CommandOutcome.OpenSettings -> {
                setCommandInput("")
                _commandFeedback.value = CommandFeedback.None
                navigateTo(Routes.Settings.ROUTE)
            }

            CommandOutcome.ShowApps -> {
                setCommandInput("")
                _commandFeedback.value = CommandFeedback.None
            }

            CommandOutcome.ClearInput -> {
                setCommandInput("")
                _commandFeedback.value = CommandFeedback.None
            }
        }
    }

    private fun observeSuggestionFlag() {
        viewModelScope.launch(ioDispatcher) {
            try {
                featureFlagRepository.getFlags()
                    .map { it.aiSuggestionsEnabled }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        if (enabled) {
                            restoreAndRefreshSuggestions()
                        } else {
                            clearSuggestions()
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                clearSuggestions()
            }
        }
    }

    private suspend fun restoreAndRefreshSuggestions() {
        suggestionsJob?.cancelAndJoin()
        suggestionsJob = null
        try {
            _suggestions.value = suggestionsCacheRepository.getCachedSuggestions().first().map { cached ->
                // The cache stores only the display-safe repaint fields; the synthetic source/score are
                // placeholders until the fresh engine result supersedes this first paint.
                Suggestion(
                    label = cached.label,
                    actionId = cached.actionId,
                    source = SuggestionSource.RECENT_USAGE,
                    score = 0.0,
                )
            }

            suggestionsJob = viewModelScope.launch(ioDispatcher, start = CoroutineStart.UNDISPATCHED) {
                suggestionEngine
                    .suggestions()
                    .drop(1)
                    .collect { fresh ->
                        _suggestions.value = fresh
                    }
            }

            suggestionEngine.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            clearSuggestions()
        }
    }

    private suspend fun clearSuggestions() {
        suggestionsJob?.cancelAndJoin()
        suggestionsJob = null
        _suggestions.value = emptyList()
    }

    // ── Usage-aware grid sort ───────────────────────────────────────────────
    // Apps with usage history rise to the top (by launchCount then lastUsedEpochMs).
    // Apps without history keep their original relative order as the fallback.
    private fun sortByUsage(
        apps: List<InstalledApp>,
        usageRecords: List<AppUsageRecord>,
    ): List<InstalledApp> {
        if (usageRecords.isEmpty()) return apps
        val byPackage = usageRecords.associateBy { it.packageName }
        val (withHistory, withoutHistory) = apps.partition { byPackage.containsKey(it.packageName) }
        val sorted = withHistory.sortedWith(
            compareByDescending<InstalledApp> { byPackage[it.packageName]!!.launchCount }
                .thenByDescending { byPackage[it.packageName]!!.lastUsedEpochMs }
        )
        return sorted + withoutHistory
    }

    // ── Favorites (Block X2) ───────────────────────────────────────────────
    // The decluttered home shows a small top-N most-used row instead of the full grid.
    // [usageRecords] arrives most-used-first (UsageHistoryRepository contract); we map each to its
    // currently-installed app (dropping records for apps that are gone) and cap at [favoritesCount]
    // (Block X6 — user-configurable via Settings). Empty history (fresh install) → empty favorites
    // (an alphabetical fallback is a later block).
    private fun deriveFavorites(
        usageRecords: List<AppUsageRecord>,
        installed: List<InstalledApp>,
        favoritesCount: Int,
    ): List<InstalledApp> {
        if (usageRecords.isEmpty() || favoritesCount <= 0) return emptyList()
        val byPackage = installed.associateBy { it.packageName }
        return usageRecords
            .mapNotNull { byPackage[it.packageName] }
            .take(favoritesCount)
    }

    /**
     * Dismiss the one-shot first-run "set as default launcher" nudge (Block X6). Persisted so the
     * hint never resurfaces. Idempotent; a write failure leaves the flag unset (the nudge may show
     * again — acceptable for a purely advisory hint).
     */
    fun dismissSetupHint() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val current = userPreferencesRepository.getPreferences().first()
                if (!current.setupHintDismissed) {
                    userPreferencesRepository.updatePreferences(current.copy(setupHintDismissed = true))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Advisory hint — a failed dismiss is non-critical.
            }
        }
    }

    private fun resolveSuggestionLabels(
        suggestions: List<Suggestion>,
        apps: List<InstalledApp>,
    ): List<Suggestion> {
        if (suggestions.isEmpty()) return emptyList()
        val appsByPackage = apps.associateBy { it.packageName }
        return suggestions.mapNotNull { suggestion ->
            val app = appsByPackage[suggestion.actionId]
            when {
                app != null -> suggestion.copy(label = app.label)
                suggestion.actionId.isKnownRoute() -> suggestion
                else -> null
            }
        }
    }

    private fun launchApp(
        packageName: String,
        activityName: String?,
    ) {
        viewModelScope.launch {
            val action = ExecutableAction.LaunchAppAction(
                packageName = packageName,
                activityName = activityName,
            )
            val result = actionExecutor.execute(action)
            _commandFeedback.value = when (result) {
                is ActionExecutionResult.Success -> CommandFeedback.None
                is ActionExecutionResult.Failure -> CommandFeedback.Message(result.safeMessage)
                is ActionExecutionResult.Unsupported -> CommandFeedback.Message(GENERIC_ERROR)
            }
            // Record usage only on a successful launch — soft-wrapped, never blocks the launch.
            // Command-executed launches (CommandOutcome.Executed) are not tracked here because
            // the package name is not available at the ViewModel boundary; a future slice can
            // extend HandleUserCommandUseCase to carry it in the outcome.
            if (result is ActionExecutionResult.Success) {
                recordUsage(packageName)
            }
        }
    }

    private suspend fun recordUsage(packageName: String) {
        try {
            // Gate: skip the write when the user has not enabled usage-history tracking.
            if (!featureFlagRepository.getFlags().first().usageHistoryEnabled) return
            usageHistoryRepository.recordLaunch(packageName, System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Non-critical — launch already completed successfully.
        }
    }

    private fun describe(intent: LauncherIntent): String = when (intent) {
        is LauncherIntent.LaunchAppIntent -> "Did you mean to open \"${intent.displayNameQuery}\"?"
        is LauncherIntent.SearchIntent -> "Search the web for \"${intent.query}\"?"
        is LauncherIntent.OpenSettingsIntent -> "Open settings?"
        is LauncherIntent.SimpleCommandIntent -> "Run that command?"
        is LauncherIntent.OpenUrlIntent -> "Open \"${intent.url}\"?"
        is LauncherIntent.PlayStoreSearchIntent -> "Find \"${intent.query}\" in the Play Store?"
        is LauncherIntent.UnknownIntent -> "Try a different command"
    }

    // ── OperationError → UiError — exhaustive when, no else branch ─────────
    // Add a new branch here whenever OperationError gains a new subtype.
    private fun OperationError.toUiError(): UiError = when (this) {
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
    private fun OperationError.isRetryable(): Boolean = when (this) {
        is OperationError.NetworkError     -> true
        is OperationError.UnknownError     -> true
        is OperationError.AiUnavailable    -> false
        is OperationError.PermissionDenied -> false
        is OperationError.DeviceNotCapable -> false
    }

    private fun String.isKnownRoute(): Boolean =
        this == Routes.Launcher.ROUTE ||
            this == Routes.Assistant.ROUTE ||
            this == Routes.Settings.ROUTE ||
            this == Routes.PermissionEducation.ROUTE ||
            this.startsWith("${Routes.PermissionEducation.ROUTE}?")

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
        // SavedStateHandle key for the typed command text (H3 process-death restoration).
        const val KEY_COMMAND_INPUT = "command_input"
        const val VOICE_UNAVAILABLE = "Voice input isn't available on this device."
    }
}
