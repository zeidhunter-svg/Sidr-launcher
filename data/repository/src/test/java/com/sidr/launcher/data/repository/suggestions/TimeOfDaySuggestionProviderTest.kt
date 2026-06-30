package com.sidr.launcher.data.repository.suggestions

import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionSource
import com.sidr.launcher.domain.suggestions.TimeOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Block U5/U6 — proves the REAL [TimeOfDaySuggestionProvider] (not a stand-in) actually emits a
 * non-empty, [SuggestionSource.TIME_OF_DAY]-tagged result for every bucket. The engine-level degrade
 * test ([SuggestionEngineImplTest]) only proves the engine forwards whatever a provider returns; this
 * is the other half — the zero-permission provider itself always has something to forward.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeOfDaySuggestionProviderTest {

    private val sut = TimeOfDaySuggestionProvider()

    @Test
    fun `every TimeOfDay bucket produces a non-empty, correctly-sourced result`() = runTest {
        TimeOfDay.entries.forEach { bucket ->
            val context = SuggestionContext(timeOfDay = bucket, nowEpochMs = 0L)
            val result = sut.provide(context)
            assertFalse("expected a non-empty result for $bucket", result.isEmpty())
            assertTrue(
                "every suggestion for $bucket must be sourced TIME_OF_DAY",
                result.all { it.source == SuggestionSource.TIME_OF_DAY },
            )
        }
    }
}
