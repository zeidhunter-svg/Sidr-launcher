package com.sidr.launcher.data.repository.suggestions

import com.sidr.launcher.core.testing.FakeUsageHistoryRepository
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionSource
import com.sidr.launcher.domain.suggestions.TimeOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Block U5/U6 — proves the REAL [UsageSuggestionProvider] (not a stand-in) actually emits a non-empty
 * result from real usage history, covering both the recency and frequency candidate paths. Mirrors
 * [TimeOfDaySuggestionProviderTest] — the engine-level degrade test only proves forwarding, this proves
 * the zero-permission provider has real output to forward.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UsageSuggestionProviderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val now = 1_700_000_000_000L

    @Test
    fun `recently used app within the horizon produces a RECENT_USAGE suggestion`() = runTest {
        val usageRepo = FakeUsageHistoryRepository().apply {
            setRecords(listOf(AppUsageRecord(packageName = "com.example.recent", lastUsedEpochMs = now, launchCount = 1)))
        }
        val sut = UsageSuggestionProvider(usageRepo, dispatcher)

        val result = sut.provide(SuggestionContext(timeOfDay = TimeOfDay.WORK, nowEpochMs = now))

        assertFalse(result.isEmpty())
        assertTrue(result.any { it.source == SuggestionSource.RECENT_USAGE && it.actionId == "com.example.recent" })
    }

    @Test
    fun `frequently launched app produces a FREQUENT_USAGE suggestion`() = runTest {
        val usageRepo = FakeUsageHistoryRepository().apply {
            setRecords(
                listOf(
                    // lastUsedEpochMs far outside the 7-day recency horizon, so only the frequency
                    // path can produce a candidate for this record — isolates the two code paths.
                    AppUsageRecord(packageName = "com.example.frequent", lastUsedEpochMs = 0L, launchCount = 20),
                    AppUsageRecord(packageName = "com.example.other", lastUsedEpochMs = 0L, launchCount = 2),
                ),
            )
        }
        val sut = UsageSuggestionProvider(usageRepo, dispatcher)

        val result = sut.provide(SuggestionContext(timeOfDay = TimeOfDay.WORK, nowEpochMs = now))

        assertFalse(result.isEmpty())
        assertTrue(result.none { it.source == SuggestionSource.RECENT_USAGE })
        assertTrue(result.any { it.source == SuggestionSource.FREQUENT_USAGE && it.actionId == "com.example.frequent" })
    }

    @Test
    fun `no usage history yields an empty result, not a thrown exception`() = runTest {
        val sut = UsageSuggestionProvider(FakeUsageHistoryRepository(), dispatcher)
        assertTrue(sut.provide(SuggestionContext(timeOfDay = TimeOfDay.WORK, nowEpochMs = now)).isEmpty())
    }
}
