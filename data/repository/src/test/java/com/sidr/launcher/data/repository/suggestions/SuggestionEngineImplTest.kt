package com.sidr.launcher.data.repository.suggestions

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakePermissionChecker
import com.sidr.launcher.core.testing.FakeSuggestionProvider
import com.sidr.launcher.core.testing.FakeSuggestionRankingRepository
import com.sidr.launcher.core.testing.FakeSuggestionsCacheRepository
import com.sidr.launcher.domain.preferences.CachedSuggestion
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.HeuristicSuggestionRanker
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionProvider
import com.sidr.launcher.domain.suggestions.SuggestionSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Block U5 privacy + degrade guard for [SuggestionEngineImpl].
 *
 * Two contracts are proven here as executable tests, not prose:
 *  1. [persistence stores only the display-safe projection] — the ranking-history write carries
 *     exactly [com.sidr.launcher.domain.history.SuggestionRankingRecord]'s four fields
 *     (actionId/label/score/timestamp — the same shape `RoomColumnNames.SUGGESTION_RANKING` already
 *     guards) and the cache write carries exactly [CachedSuggestion]'s two fields (label/actionId).
 *     Block U introduces no new field on either path. Combined with
 *     [SuggestionProviderPrivacyGuardTest] (which proves raw event/location data never becomes part of
 *     a [Suggestion] in the first place), this closes the loop: raw signal -> Suggestion -> persistence.
 *  2. [the offline degrade guarantee] — with every optional permission denied, the **real**
 *     [CalendarSuggestionProvider]/[LocationSuggestionProvider] (not stand-ins) contribute nothing, and
 *     the engine still returns the zero-permission time-of-day/usage suggestions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SuggestionEngineImplTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun engine(
        providers: List<SuggestionProvider>,
        rankingRepository: FakeSuggestionRankingRepository = FakeSuggestionRankingRepository(),
        cacheRepository: FakeSuggestionsCacheRepository = FakeSuggestionsCacheRepository(),
    ): SuggestionEngineImpl = SuggestionEngineImpl(
        providers = providers,
        ranker = HeuristicSuggestionRanker(),
        rankingRepository = rankingRepository,
        cacheRepository = cacheRepository,
        featureFlagRepository = FakeFeatureFlagRepository(initial = FeatureFlags(aiSuggestionsEnabled = true)),
        ioDispatcher = dispatcher,
    )

    @Test
    fun `refresh persists only the display-safe label and actionId, nothing else`() = runTest {
        val candidate = Suggestion(
            label = "Upcoming event",
            actionId = "com.google.android.calendar",
            source = SuggestionSource.CALENDAR,
            score = 0.90,
        )
        val rankingRepo = FakeSuggestionRankingRepository()
        val cacheRepo = FakeSuggestionsCacheRepository()
        val sut = engine(
            providers = listOf(FakeSuggestionProvider(suggestions = listOf(candidate))),
            rankingRepository = rankingRepo,
            cacheRepository = cacheRepo,
        )

        val result = sut.refresh()
        assertTrue(result is OperationResult.Success)

        // Ranking history: actionId/label/score/timestamp only — no new field introduced by Block U.
        assertEquals(1, rankingRepo.recordedUpserts.size)
        val recorded = rankingRepo.recordedUpserts.single()
        assertEquals(candidate.actionId, recorded.actionId)
        assertEquals(candidate.label, recorded.label)
        assertEquals(candidate.score, recorded.score, 0.0)

        // Cache: label + actionId ONLY. CachedSuggestion structurally cannot carry more, but this
        // proves the engine actually maps into that narrow type — not, say, serialising the whole
        // Suggestion (which would smuggle `source`/`score` into the cold-start cache).
        val cached = cacheRepo.getCachedSuggestions().first()
        assertEquals(
            listOf(CachedSuggestion(label = candidate.label, actionId = candidate.actionId)),
            cached,
        )
    }

    @Test
    fun `every optional permission denied still returns time-of-day and usage suggestions`() = runTest {
        // FakePermissionChecker defaults to DENIED for every feature it hasn't been told otherwise
        // about — these are the REAL production providers wired against that denial, not stand-ins, so
        // this proves the gate itself degrades safely, not just that an engine-level branch skips
        // empty lists.
        val permissionChecker = FakePermissionChecker()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val calendarProvider = CalendarSuggestionProvider(context, permissionChecker, dispatcher)
        val locationProvider = LocationSuggestionProvider(context, permissionChecker, dispatcher)

        val timeOfDaySuggestion = Suggestion(
            label = "Open camera",
            actionId = "com.android.camera",
            source = SuggestionSource.TIME_OF_DAY,
            score = 1.0,
        )
        val usageSuggestion = Suggestion(
            label = "com.example.app",
            actionId = "com.example.app",
            source = SuggestionSource.RECENT_USAGE,
            score = 0.8,
        )
        val sut = engine(
            providers = listOf(
                FakeSuggestionProvider(suggestions = listOf(timeOfDaySuggestion)),
                FakeSuggestionProvider(suggestions = listOf(usageSuggestion)),
                calendarProvider,
                locationProvider,
            ),
        )

        val result = sut.refresh()

        assertTrue(result is OperationResult.Success)
        val ranked = (result as OperationResult.Success).value
        assertFalse(
            "offline sources must still produce suggestions when every optional permission is denied",
            ranked.isEmpty(),
        )
        assertTrue(ranked.any { it.source == SuggestionSource.TIME_OF_DAY })
        assertTrue(ranked.any { it.source == SuggestionSource.RECENT_USAGE })
        assertTrue(
            "a denied opt-in provider must contribute nothing",
            ranked.none { it.source == SuggestionSource.CALENDAR || it.source == SuggestionSource.LOCATION },
        )
    }

    @Test
    fun `refresh mixes candidates across providers and dedups by actionId, keeping the higher-weighted one`() = runTest {
        // Same actionId from two providers/sources. FREQUENT_USAGE actually has a LOWER source
        // multiplier than RECENT_USAGE (0.95x vs 1.00x) — the gap between the raw scores (0.9 vs 0.5)
        // is large enough to dominate anyway (weighted: 0.9*0.95=0.855 vs 0.5*1.00=0.5), so this also
        // proves dedup compares the final weighted score, not just the raw provider score or the source.
        val recent = Suggestion(
            label = "com.example.dup",
            actionId = "com.example.dup",
            source = SuggestionSource.RECENT_USAGE,
            score = 0.5,
        )
        val frequent = Suggestion(
            label = "com.example.dup",
            actionId = "com.example.dup",
            source = SuggestionSource.FREQUENT_USAGE,
            score = 0.9,
        )
        val unique = Suggestion(
            label = "Open camera",
            actionId = "com.android.camera",
            source = SuggestionSource.TIME_OF_DAY,
            score = 1.0,
        )
        val sut = engine(
            providers = listOf(
                FakeSuggestionProvider(suggestions = listOf(recent)),
                FakeSuggestionProvider(suggestions = listOf(frequent)),
                FakeSuggestionProvider(suggestions = listOf(unique)),
            ),
        )

        val result = sut.refresh()

        assertTrue(result is OperationResult.Success)
        val ranked = (result as OperationResult.Success).value
        val duplicates = ranked.filter { it.actionId == "com.example.dup" }
        assertEquals("dedup by actionId must keep exactly one candidate per target", 1, duplicates.size)
        assertEquals(
            "the higher-weighted candidate (FREQUENT_USAGE, score 0.9) must win over the lower one " +
                "(RECENT_USAGE, score 0.5)",
            SuggestionSource.FREQUENT_USAGE,
            duplicates.single().source,
        )
        assertTrue(
            "a non-duplicate candidate from a third provider must still be present (mix across providers)",
            ranked.any { it.actionId == "com.android.camera" },
        )
    }

    @Test
    fun `refresh bounds the result to the ranker's max and persists exactly that bounded set`() = runTest {
        val manyCandidates = (1..7).map { i ->
            Suggestion(
                label = "com.example.app$i",
                actionId = "com.example.app$i",
                source = SuggestionSource.RECENT_USAGE,
                score = 1.0 - (i * 0.01),
            )
        }
        val rankingRepo = FakeSuggestionRankingRepository()
        val cacheRepo = FakeSuggestionsCacheRepository()
        val sut = engine(
            providers = listOf(FakeSuggestionProvider(suggestions = manyCandidates)),
            rankingRepository = rankingRepo,
            cacheRepository = cacheRepo,
        )

        val result = sut.refresh()

        assertTrue(result is OperationResult.Success)
        val ranked = (result as OperationResult.Success).value
        assertEquals(HeuristicSuggestionRanker.DEFAULT_MAX_SUGGESTIONS, ranked.size)

        // Both persistence targets must reflect the bounded set, not the unbounded 7 candidates.
        assertEquals(HeuristicSuggestionRanker.DEFAULT_MAX_SUGGESTIONS, rankingRepo.recordedUpserts.size)
        assertEquals(ranked.map { it.actionId }.toSet(), rankingRepo.recordedUpserts.map { it.actionId }.toSet())
        val cached = cacheRepo.getCachedSuggestions().first()
        assertEquals(HeuristicSuggestionRanker.DEFAULT_MAX_SUGGESTIONS, cached.size)
        assertEquals(
            ranked.map { CachedSuggestion(label = it.label, actionId = it.actionId) },
            cached,
        )
    }
}
