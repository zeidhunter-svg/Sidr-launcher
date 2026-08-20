package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.SuggestionsCacheRepository
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionEngine
import com.sidr.launcher.domain.suggestions.SuggestionSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Этап 4 / A0, preparatory split. The home suggestions row (Phase 7, Block W-lite) extracted
 * unchanged from `LauncherViewModel`: same flag-gated collection, same cache-first-then-fresh
 * restore sequence, same [suggestionsJob] cancel/restart semantics. This class is a move, not a
 * redesign, and `LauncherViewModelTest` must not need one character of edit.
 *
 * `resolveLabels(raw, apps)` is deliberately a plain function with no hidden dependency on
 * ViewModel-only state (per the Task 3 ruling: `LauncherAppList` will take this instance as a
 * constructor parameter and call it directly once the app-list `combine` moves there).
 *
 * [installedAppsRepository] is not read by any member here today — it is carried in the
 * constructor per the Task 2 interface contract ahead of later blocks; do not remove it as
 * "unused" without checking with the plan.
 */
internal class LauncherSuggestions(
    private val suggestionEngine: SuggestionEngine,
    private val suggestionsCacheRepository: SuggestionsCacheRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) {

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions

    // Tracks the live suggestion-stream collector so flag toggles can cancel/restart it cleanly.
    private var suggestionsJob: Job? = null

    fun observeFlag() {
        scope.launch(ioDispatcher) {
            try {
                featureFlagRepository.getFlags()
                    .map { it.aiSuggestionsEnabled }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        if (enabled) {
                            restoreAndRefresh()
                        } else {
                            clear()
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                clear()
            }
        }
    }

    suspend fun restoreAndRefresh() {
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

            suggestionsJob = scope.launch(ioDispatcher, start = CoroutineStart.UNDISPATCHED) {
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
            clear()
        }
    }

    suspend fun clear() {
        suggestionsJob?.cancelAndJoin()
        suggestionsJob = null
        _suggestions.value = emptyList()
    }

    fun resolveLabels(
        raw: List<Suggestion>,
        apps: List<InstalledApp>,
    ): List<Suggestion> {
        if (raw.isEmpty()) return emptyList()
        val appsByPackage = apps.associateBy { it.packageName }
        return raw.mapNotNull { suggestion ->
            val app = appsByPackage[suggestion.actionId]
            when {
                app != null -> suggestion.copy(label = app.label)
                suggestion.actionId.isKnownRoute() -> suggestion
                else -> null
            }
        }
    }
}
