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
    // I18N-1 (spec §3.5): PermissionDenied/DeviceNotCapable are now routed through the feature-local,
    // typed AppDrawerError (see its kdoc in LauncherPresentation.kt) instead of hand-assembling the
    // sentence inline. core.common.UiError has no typed-argument slot and sidrString only resolves
    // inside a @Composable, so toEnglishFallback() below still produces the final UiError.Message text
    // here — byte-identical to the English resource values, so on-device behaviour is unchanged.
    private fun OperationError.toUiError(): UiError = when (this) {
        is OperationError.NetworkError     -> UiError.Network
        is OperationError.AiUnavailable    -> UiError.Unknown
        is OperationError.PermissionDenied ->
            UiError.Message(AppDrawerError.PermissionDenied(permission).toEnglishFallback())
        is OperationError.DeviceNotCapable ->
            UiError.Message(AppDrawerError.DeviceNotCapable(feature).toEnglishFallback())
        is OperationError.UnknownError     -> UiError.Unknown
    }

    /**
     * English-only fallback for [AppDrawerError], byte-identical to `launcher_drawer_permission_denied`
     * / `launcher_drawer_not_supported` (see [appDrawerErrorText]'s kdoc for why this can't yet route
     * through the resource system at this layer).
     */
    private fun AppDrawerError.toEnglishFallback(): String = when (this) {
        is AppDrawerError.PermissionDenied -> "Permission denied: $permission"
        is AppDrawerError.DeviceNotCapable -> "Not supported: $feature"
    }

    private fun OperationError.isRetryable(): Boolean = when (this) {
        is OperationError.NetworkError     -> true
        is OperationError.UnknownError     -> true
        is OperationError.AiUnavailable    -> false
        is OperationError.PermissionDenied -> false
        is OperationError.DeviceNotCapable -> false
    }
}
