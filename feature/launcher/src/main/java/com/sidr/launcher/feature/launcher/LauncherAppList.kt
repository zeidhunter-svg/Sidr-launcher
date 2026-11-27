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
 *
 * Task 4 / A0, Ruling R7: the separate `favorites: StateFlow<List<InstalledApp>>` this class also
 * carried had no consumer — `LauncherUiState.favorites` (read by `LauncherScreen` and the ViewModel
 * test suite) is a different, already-correct value computed inside [state]'s own `combine`. This
 * second copy was backed by its own eager `stateIn` collector parked on `scope` for the lifetime of
 * every `LauncherViewModel` instance, for a value nothing ever read — confirmed by grepping `feature/`
 * and `app/` for a use before removing it.
 */
internal class LauncherAppList(
    private val installedAppsRepository: InstalledAppsRepository,
    private val usageHistoryRepository: UsageHistoryRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
    private val suggestions: LauncherSuggestions,
    // A4′ acceptance tail, task 12: the clock freshness is judged by (epoch ms). `LauncherViewModel` passes its own
    // injected [java.time.Clock]; never `System.currentTimeMillis()` inside [deriveFavorites].
    private val nowMs: () -> Long,
) {

    // Raw app-list load result; null = the full launcher app inventory is still loading in the
    // background. Home must still paint from cheap cached state while this is null.
    private val _rawAppsResult = MutableStateFlow<OperationResult<List<InstalledApp>>?>(null)
    val rawAppsResult: StateFlow<OperationResult<List<InstalledApp>>?> = _rawAppsResult

    // Block X6: deferred UI preferences (favorites row size, first-run nudge flag) read here purely
    // for the [state] combine below. A read failure degrades to defaults (12 favorites, hint shown).
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
                            favorites = deriveFavorites(usageRecords, sorted, prefs, nowMs()),
                            pinnedPackages = prefs.pinnedFavorites,
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

/** An automatically placed favourite not opened within this long leaves its place (A4′ acceptance tail, task 12; K8). */
internal const val FAVORITES_FRESH_MS: Long = 14L * 24 * 60 * 60 * 1000

/**
 * Home's favourites (Block X2, reshaped by A4′ acceptance tail task 12 / owner ruling 15): first the apps the user
 * **pinned**, in the order they were pinned (only the installed ones — a pinned app that is gone is skipped, and it is
 * never pushed out by the automatic ones); then the usage records in their given order — [usageRecords] arrive
 * most-used-first (the `UsageHistoryRepository` contract) — **except** the hidden ones, the already pinned ones and
 * those not opened for 14 days ([FAVORITES_FRESH_MS], judged by `lastUsedEpochMs` against [nowMs]); all of it capped at
 * [UserPreferences.favoritesCount]. No history and nothing pinned — empty (Home still draws the header, so there is
 * something to tap and pin from). Pure: [nowMs] comes in from the caller's clock.
 */
internal fun deriveFavorites(
    usageRecords: List<AppUsageRecord>,
    installed: List<InstalledApp>,
    prefs: UserPreferences,
    nowMs: Long,
): List<InstalledApp> {
    if (prefs.favoritesCount <= 0) return emptyList()
    val byPackage = installed.associateBy { it.packageName }
    val pinned = prefs.pinnedFavorites.distinct().mapNotNull { byPackage[it] }
    val pinnedPackages = pinned.mapTo(HashSet()) { it.packageName }
    val automatic = usageRecords
        .asSequence()
        .filter { it.packageName !in pinnedPackages && it.packageName !in prefs.hiddenFavorites }
        .filter { it.lastUsedEpochMs >= nowMs - FAVORITES_FRESH_MS }
        .mapNotNull { byPackage[it.packageName] }
        .distinctBy { it.packageName }
        .toList()
    return (pinned + automatic).take(prefs.favoritesCount)
}
