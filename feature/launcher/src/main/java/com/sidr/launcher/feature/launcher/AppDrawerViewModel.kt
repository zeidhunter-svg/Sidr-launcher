package com.sidr.launcher.feature.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the App Drawer (Block X3): loads the full installed-app list and exposes it grouped
 * alphabetically for the drawer's sectioned list. Tapping an app launches it through the existing
 * [ActionExecutor] path and records usage exactly like the home screen, so drawer launches still
 * feed Favorites / Suggestions on home.
 *
 * Deliberately narrow: it does NOT parse commands (no [com.sidr.launcher.domain.intent.HandleUserCommandUseCase]
 * / IntentMatcher). The launch + usage logic is a small verbatim copy of [LauncherViewModel]'s (per
 * the Block X3 ADR — a shared use-case was judged premature for a two-caller path).
 */
@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val actionExecutor: ActionExecutor,
    private val usageHistoryRepository: UsageHistoryRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    // ── Navigation events (same Channel pattern as LauncherViewModel) ───────
    private val _navigationEvents = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents: Flow<NavigationEvent> = _navigationEvents.receiveAsFlow()

    fun navigateBack() {
        _navigationEvents.trySend(NavigationEvent.NavigateBack)
    }

    /**
     * Navigate to a prebuilt route (Block X6-C: the drawer "Ask assistant" affordance passes the
     * assistant route with the typed query prefilled). The VM stays free of Android/route-encoding —
     * the screen builds the encoded route, the VM only emits the [NavigationEvent].
     */
    fun navigateTo(route: String) {
        _navigationEvents.trySend(NavigationEvent.NavigateTo(route))
    }

    // ── App-list state ──────────────────────────────────────────────────────
    // Raw load result; null = load not yet complete → Loading.
    private val _rawAppsResult = MutableStateFlow<OperationResult<List<InstalledApp>>?>(null)

    // ── Live search query (Block X4) ─────────────────────────────────────────
    // Empty = no filter → full list. The screen binds this into SidrSearchField.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Live-filter the drawer as the user types (no command dispatch — that stays on home). */
    fun onQueryChanged(text: String) {
        _query.value = text
    }

    val uiState: StateFlow<UiState<AppDrawerUiState>> =
        combine(_rawAppsResult, _query) { result, query ->
            when (result) {
                null -> UiState.Loading
                is OperationResult.Failure ->
                    UiState.Error(result.error.toUiError(), retryable = result.error.isRetryable())
                is OperationResult.Success -> {
                    val sections = groupIntoSections(filterApps(result.value, query))
                    // Empty covers both "no apps installed" and "query matched nothing"; the screen
                    // picks the message from the current query.
                    if (sections.isEmpty()) UiState.Empty
                    else UiState.Success(AppDrawerUiState(sections = sections))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Loading,
        )

    /**
     * I18N-1 Fix round 1: the typed detail behind a [OperationError.PermissionDenied]/
     * [OperationError.DeviceNotCapable] load failure, exposed **alongside** [uiState] rather than
     * through it — `core.common.UiState.Error` is fixed to `UiError` (no typed-argument slot) and
     * `core/common` is out of scope for this task, so [uiState]'s own `UiError.Message` text below
     * stays the byte-identical English fallback it always was. This is the live seam Task 13's
     * `AppDrawerScreen.kt` resolves via `appDrawerErrorText(e)` + `sidrString(...)` — mirrors how
     * [LauncherViewModel] exposes `commandFeedback` alongside its own `uiState`. Null whenever the
     * last result isn't one of these two argument-carrying failures (no failure yet, `Success`, or a
     * `NetworkError`/`AiUnavailable`/`UnknownError` failure, none of which `appDrawerErrorText` needs).
     */
    val loadErrorDetail: StateFlow<AppDrawerError?> = _rawAppsResult
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

    private var loadJob: Job? = null

    init {
        loadApps()
    }

    private fun loadApps() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(ioDispatcher) {
            _rawAppsResult.value = installedAppsRepository.getInstalledApps()
        }
    }

    /** Re-attempt the load after a recoverable [UiState.Error] — no process restart. */
    fun retry() {
        _rawAppsResult.value = null
        loadApps()
    }

    /** Tap-to-launch from the drawer: launch directly through the executor, then record usage. */
    fun onAppClicked(app: InstalledApp) {
        launchApp(packageName = app.packageName, activityName = app.activityName)
    }

    /**
     * IME "search" action inside the drawer (Block X4): launch the top current filter match, so a
     * quick "type → enter" finds and opens an app without a tap. No-op when nothing matches. The
     * drawer stays command-free — command dispatch lives only on the home screen.
     */
    fun onQuerySubmitted() {
        val apps = (_rawAppsResult.value as? OperationResult.Success)?.value ?: return
        // Match what the user sees: the first app of the first (alphabetical) section.
        val top = groupIntoSections(filterApps(apps, _query.value))
            .firstOrNull()?.apps?.firstOrNull() ?: return
        launchApp(packageName = top.packageName, activityName = top.activityName)
    }

    // Verbatim copy of LauncherViewModel.launchApp/recordUsage (Block X3 ADR): same executor path,
    // same usageHistoryEnabled gate, CancellationException re-thrown, non-critical errors swallowed.
    private fun launchApp(packageName: String, activityName: String?) {
        viewModelScope.launch {
            val action = ExecutableAction.LaunchAppAction(
                packageName = packageName,
                activityName = activityName,
            )
            val result = actionExecutor.execute(action)
            if (result is ActionExecutionResult.Success) {
                recordUsage(packageName)
            }
        }
    }

    private suspend fun recordUsage(packageName: String) {
        try {
            if (!featureFlagRepository.getFlags().first().usageHistoryEnabled) return
            usageHistoryRepository.recordLaunch(packageName, System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Non-critical — the launch already completed successfully.
        }
    }

    // ── OperationError → UiError — exhaustive, mirrors LauncherViewModel ─────
    // I18N-1 Fix round 1: left byte-identical to its pre-Task-12 shape on purpose (per the coordinator's
    // "leave UiState.Error exactly as it is") — the real typed, live seam is [loadErrorDetail] above,
    // not a construct-then-discard AppDrawerError built only to be flattened back into English here.
    private fun OperationError.toUiError(): UiError = when (this) {
        is OperationError.NetworkError     -> UiError.Network
        is OperationError.AiUnavailable    -> UiError.Unknown
        is OperationError.PermissionDenied -> UiError.Message("Permission denied: $permission")
        is OperationError.DeviceNotCapable -> UiError.Message("Not supported: $feature")
        is OperationError.UnknownError     -> UiError.Unknown
    }

    private fun OperationError.isRetryable(): Boolean = when (this) {
        is OperationError.NetworkError     -> true
        is OperationError.UnknownError     -> true
        is OperationError.AiUnavailable    -> false
        is OperationError.PermissionDenied -> false
        is OperationError.DeviceNotCapable -> false
    }
}
