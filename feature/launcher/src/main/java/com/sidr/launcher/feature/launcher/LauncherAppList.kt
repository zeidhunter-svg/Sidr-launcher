package com.sidr.launcher.feature.launcher

import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.UserPreferences
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Task 3 / A0. The app-list load + usage-aware grid state extracted unchanged from
 * `LauncherViewModel`: same cancel-then-reload semantics on [load]/[retry], same
 * `combine(...)`/`stateIn(...)` shape for [state] — moved whole, not re-derived (Step 3 of the task).
 *
 * Ruling R1 (controller): takes the [LauncherSuggestions] instance built in `LauncherViewModel` as a
 * constructor parameter and calls [LauncherSuggestions.resolveLabels] directly inside the `combine`,
 * so the label-resolution logic that used to live in the ViewModel's `uiState` combine now lives
 * fully outside it.
 *
 * [rawAppsResult] is not part of the task brief's stated public surface, but two ViewModel members
 * that are *not* part of this move — `appListErrorDetail` and `inputResults` — read the raw
 * (pre-`UiState`-mapping) load result directly (to recover the typed [OperationError] for the former,
 * and the plain loaded app list for the latter's search-overtakes panel). Exposing the same backing
 * [MutableStateFlow] read-only keeps both on the exact same source of truth instead of a second
 * `combine`/`collect` that could drift from [state] or double-run the load.
 */
internal class LauncherAppList(
    private val installedAppsRepository: InstalledAppsRepository,
    private val usageHistoryRepository: UsageHistoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val suggestions: LauncherSuggestions,
) {

    // Raw app-list load result; null = the full launcher app inventory is still loading in the
    // background. Home must still paint from cheap cached state while this is null.
    private val _rawAppsResult = MutableStateFlow<OperationResult<List<InstalledApp>>?>(null)
    val rawAppsResult: StateFlow<OperationResult<List<InstalledApp>>?> = _rawAppsResult

    // Block X6: deferred UI preferences (favorites row size, first-run nudge flag) read here purely
    // for the [state] combine below. A read failure degrades to defaults (8 favorites, hint shown).
    private val userPreferences: StateFlow<UserPreferences> =
        userPreferencesRepository.getPreferences()
            .catch { emit(UserPreferences()) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = UserPreferences(),
            )

    // Tracks the in-flight app load so a new load (init or retry) cancels the previous one.
    private var loadJob: Job? = null

    // Derived state: combines the loaded app list with live usage records so the grid
    // re-sorts automatically whenever a launch is recorded (F6 demo slice), surfaces the
    // launcher-owned suggestion row state (Phase 7, Block W-lite), and carries the Block X6
    // favorites cap + first-run nudge flag through user preferences.
    val state: StateFlow<UiState<LauncherUiState>> = combine(
        _rawAppsResult,
        usageHistoryRepository.getUsageRecords().catch { emit(emptyList()) },
        suggestions.suggestions,
        userPreferences,
    ) { appsResult, usageRecords, suggestionsList, prefs ->
            when (appsResult) {
                // Full app inventory is not first-frame-critical: paint the home shell + cached
                // suggestions immediately, then fill apps/favorites once PackageManager returns.
                null -> UiState.Success(
                    LauncherUiState(
                        suggestions = suggestions.resolveLabels(suggestionsList, emptyList()),
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
                            suggestions = suggestions.resolveLabels(suggestionsList, sorted),
                            favorites = deriveFavorites(usageRecords, sorted, prefs.favoritesCount),
                            setupHintDismissed = prefs.setupHintDismissed,
                        ),
                    )
                }
            }
        }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = UiState.Success(LauncherUiState()),
        )

    val favorites: StateFlow<List<InstalledApp>> = state
        .map { (it as? UiState.Success)?.data?.favorites ?: emptyList() }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    fun load() {
        // Cancel any in-flight load first: rapid retries (double-tap) must not run parallel reloads.
        // Only the latest attempt's result is ever applied; a redundant not-yet-started load never
        // reaches the repository, and one already suspended mid-call is cancelled with its result
        // discarded — so the screen sees a single clean Loading → Success/Error, no flicker.
        loadJob?.cancel()
        loadJob = scope.launch(ioDispatcher) {
            _rawAppsResult.value = installedAppsRepository.getInstalledApps()
        }
    }

    /**
     * Re-attempt the app-list load after a recoverable [UiState.Error] — no process restart.
     * Resetting to null keeps the cache-first home shell visible before the reload emits its result.
     */
    fun retry() {
        _rawAppsResult.value = null
        load()
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
        scope.launch(ioDispatcher) {
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
}
