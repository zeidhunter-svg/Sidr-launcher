package com.sidr.launcher.feature.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
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
    // Raw load result; null = loading not yet complete.
    private val _rawAppsResult = MutableStateFlow<OperationResult<List<InstalledApp>>?>(null)

    // Derived state: combines the loaded app list with live usage records so the grid
    // re-sorts automatically whenever a launch is recorded (F6 demo slice).
    val uiState: StateFlow<UiState<LauncherUiState>> = _rawAppsResult
        .filterNotNull()
        .combine(
            usageHistoryRepository.getUsageRecords().catch { emit(emptyList()) }
        ) { appsResult, usageRecords ->
            when (appsResult) {
                is OperationResult.Failure -> UiState.Error(appsResult.error.toUiError())
                is OperationResult.Success -> {
                    val sorted = sortByUsage(appsResult.value, usageRecords)
                    if (sorted.isEmpty()) UiState.Empty
                    else UiState.Success(LauncherUiState(apps = sorted))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading,
        )

    // ── Command input — independent of app-list loading ────────────────────
    private val _commandInput = MutableStateFlow("")
    val commandInput: StateFlow<String> = _commandInput

    // ── Command feedback — transient result of the last submitted command ──
    private val _commandFeedback = MutableStateFlow<CommandFeedback>(CommandFeedback.None)
    val commandFeedback: StateFlow<CommandFeedback> = _commandFeedback

    init {
        loadApps()
    }

    private fun loadApps() {
        viewModelScope.launch(ioDispatcher) {
            _rawAppsResult.value = installedAppsRepository.getInstalledApps()
        }
    }

    // ── UI actions ─────────────────────────────────────────────────────────

    fun onCommandChanged(text: String) {
        _commandInput.value = text
        // Editing a new command clears stale feedback.
        _commandFeedback.value = CommandFeedback.None
    }

    fun onCommandSubmitted(text: String) {
        viewModelScope.launch {
            applyOutcome(handleUserCommand.handle(text))
        }
    }

    /** Tap-to-launch from the grid (or from an ambiguity suggestion): the app is already known, */
    /** so launch it directly through the executor — no matching needed. Does not touch input. */
    fun onAppClicked(app: InstalledApp) {
        viewModelScope.launch {
            val action = ExecutableAction.LaunchAppAction(
                packageName = app.packageName,
                activityName = app.activityName,
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
                recordUsage(app.packageName)
            }
        }
    }

    fun dismissFeedback() {
        _commandFeedback.value = CommandFeedback.None
    }

    // ── CommandOutcome → UI — exhaustive when, no else branch ──────────────
    // Add a new branch here whenever CommandOutcome gains a new variant.
    private fun applyOutcome(outcome: CommandOutcome) {
        when (outcome) {
            CommandOutcome.Empty ->
                _commandFeedback.value = CommandFeedback.Message("Type a command, e.g. \"open telegram\"")

            CommandOutcome.Executed -> {
                _commandInput.value = ""
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
                _commandInput.value = ""
                _commandFeedback.value = CommandFeedback.None
                // Route string belongs to the UI layer — the domain only said "OpenAssistant".
                navigateTo(Routes.Assistant.ROUTE)
            }

            CommandOutcome.ShowApps -> {
                _commandInput.value = ""
                _commandFeedback.value = CommandFeedback.None
            }

            CommandOutcome.ClearInput -> {
                _commandInput.value = ""
                _commandFeedback.value = CommandFeedback.None
            }
        }
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

    private companion object {
        const val GENERIC_ERROR = "Something went wrong. Please try again."
    }
}
