package com.sidr.launcher.data.repository.suggestions

import android.util.Log
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.history.SuggestionRankingRecord
import com.sidr.launcher.domain.history.SuggestionRankingRepository
import com.sidr.launcher.domain.preferences.CachedSuggestion
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.SuggestionsCacheRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionActionTargetResolver
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionEngine
import com.sidr.launcher.domain.suggestions.SuggestionProvider
import com.sidr.launcher.domain.suggestions.SuggestionRanker
import com.sidr.launcher.domain.suggestions.TimeOfDay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Aggregates [providers], ranks via [ranker], and persists the result (Phase 7, Block U3).
 *
 * Gated end-to-end by [FeatureFlagRepository.aiSuggestionsEnabled]: when disabled, [refresh] is a no-op
 * that returns an empty list without touching any provider or persistence target — suggestions are
 * strictly an opt-in, off-the-cold-start-path concern (no work on the `<10ms` rule path or app start).
 *
 * Each provider runs concurrently and is individually fault-isolated: a throwing provider degrades to
 * an empty contribution rather than failing the whole aggregate (mirrors [SuggestionProvider]'s
 * never-throw contract at the boundary, in case an implementation slips). Persistence is two-fold and
 * both targets only ever receive the already-ranked, already display-safe [Suggestion]/[CachedSuggestion]
 * shape — never a raw provider signal:
 *  - [rankingRepository] — per-item history, feeds future ranking (learning).
 *  - [cacheRepository] — the bounded `label`/`actionId` projection, for cold-start repaint (Block W2).
 *
 * [suggestions] exposes the latest ranked result as a hot, observable stream; it does not itself read
 * [cacheRepository] for an initial value — seeding the first paint from the cache before a fresh
 * [refresh] completes is the host `LauncherViewModel`'s job (Fork F7-8, Block W2).
 *
 * [persist]'s writes are fire-and-forget by design (a failure there never turns [refresh] itself into
 * a [OperationResult.Failure]) — unlike this module's other repositories, which return
 * [OperationError] to their own caller instead of logging directly, there is no caller here to return
 * a `Failure` to, so this is the one best-effort path in the module and logs the failure directly
 * (payload-free) rather than following that return-`Failure` convention.
 */
class SuggestionEngineImpl(
    private val providers: List<SuggestionProvider>,
    private val ranker: SuggestionRanker,
    private val rankingRepository: SuggestionRankingRepository,
    private val cacheRepository: SuggestionsCacheRepository,
    private val featureFlagRepository: FeatureFlagRepository,
    private val actionTargetResolver: SuggestionActionTargetResolver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) : SuggestionEngine {

    private val state = MutableStateFlow<List<Suggestion>>(emptyList())

    override fun suggestions(): Flow<List<Suggestion>> = state.asStateFlow()

    override suspend fun refresh(): OperationResult<List<Suggestion>> {
        val flags = featureFlagRepository.getFlags().first()
        if (!flags.aiSuggestionsEnabled) {
            return OperationResult.Success(emptyList())
        }
        return try {
            val ranked = withContext(ioDispatcher) {
                val context = buildContext()
                val candidates = coroutineScope {
                    providers
                        .map { provider -> async { safeProvide(provider, context) } }
                        .map { it.await() }
                        .flatten()
                }
                ranker.rank(filterSupported(candidates), context)
            }
            persist(ranked)
            state.value = ranked
            OperationResult.Success(ranked)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "suggestion_refresh_failed"))
        }
    }

    private suspend fun safeProvide(provider: SuggestionProvider, context: SuggestionContext): List<Suggestion> =
        try {
            provider.provide(context)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList()
        }

    private suspend fun filterSupported(candidates: List<Suggestion>): List<Suggestion> {
        if (candidates.isEmpty()) return emptyList()
        val supportByAction = mutableMapOf<String, Boolean>()
        val supported = mutableListOf<Suggestion>()
        candidates.forEach { suggestion ->
            val cached = supportByAction[suggestion.actionId]
            val isSupported = if (cached != null) {
                cached
            } else {
                actionTargetResolver.isSupportedAction(suggestion.actionId)
                    .also { supportByAction[suggestion.actionId] = it }
            }
            if (isSupported) supported += suggestion
        }
        return supported
    }

    private suspend fun persist(ranked: List<Suggestion>) {
        val now = nowEpochMs()
        ranked.forEach { suggestion ->
            val result = rankingRepository.upsertRanking(
                SuggestionRankingRecord(
                    actionId = suggestion.actionId,
                    label = suggestion.label,
                    score = suggestion.score,
                    lastUpdatedEpochMs = now,
                ),
            )
            if (result is OperationResult.Failure) {
                // Fire-and-forget (no caller to return Failure to, unlike the rest of this module) —
                // logged directly so a missed ranking write isn't invisible. No actionId/label logged.
                Log.w(TAG, "Suggestion ranking upsert failed; ranking history may be stale.")
            }
        }
        val cacheResult = cacheRepository.updateCachedSuggestions(
            ranked.map { CachedSuggestion(label = it.label, actionId = it.actionId) },
        )
        if (cacheResult is OperationResult.Failure) {
            // Fire-and-forget (no caller to return Failure to, unlike the rest of this module) —
            // logged directly so a missed cache write isn't invisible.
            Log.w(TAG, "Suggestion cache write failed; cold-start repaint may show stale data.")
        }
    }

    private fun buildContext(): SuggestionContext {
        val now = nowEpochMs()
        return SuggestionContext(timeOfDay = timeOfDayFor(now), nowEpochMs = now)
    }

    private fun timeOfDayFor(epochMs: Long): TimeOfDay {
        val hour = Calendar.getInstance().apply { timeInMillis = epochMs }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..9 -> TimeOfDay.MORNING
            in 10..17 -> TimeOfDay.WORK
            in 18..21 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    private companion object {
        const val TAG = "SuggestionEngine"
    }
}
