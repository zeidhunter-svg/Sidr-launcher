package com.sidr.launcher.data.repository.suggestions

import com.sidr.launcher.core.testing.FakeSuggestionActionTargetResolver
import com.sidr.launcher.domain.suggestions.SuggestionActionAnchor
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionSource
import com.sidr.launcher.domain.suggestions.TimeOfDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 9 Y3 — proves the REAL [TimeOfDaySuggestionProvider] no longer emits hardcoded AOSP package
 * IDs. It contributes only anchors resolved by [com.sidr.launcher.domain.suggestions.SuggestionActionTargetResolver].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimeOfDaySuggestionProviderTest {

    @Test
    fun `resolved anchors emit the device package, not hardcoded AOSP package ids`() = runTest {
        val resolver = FakeSuggestionActionTargetResolver(defaultSupported = false).apply {
            resolveAnchor(
                anchor = SuggestionActionAnchor.ALARMS,
                label = "Samsung Clock",
                actionId = "com.sec.android.app.clockpackage",
            )
            resolveAnchor(
                anchor = SuggestionActionAnchor.CAMERA,
                label = "Samsung Camera",
                actionId = "com.sec.android.app.camera",
            )
        }
        val sut = TimeOfDaySuggestionProvider(resolver)

        val morning = sut.provide(SuggestionContext(timeOfDay = TimeOfDay.MORNING, nowEpochMs = 0L))
        val evening = sut.provide(SuggestionContext(timeOfDay = TimeOfDay.EVENING, nowEpochMs = 0L))

        assertEquals(listOf("com.sec.android.app.clockpackage"), morning.map { it.actionId })
        assertEquals(listOf("Samsung Clock"), morning.map { it.label })
        assertEquals(listOf("com.sec.android.app.camera"), evening.map { it.actionId })
        assertEquals(listOf("Samsung Camera"), evening.map { it.label })
        assertTrue((morning + evening).all { it.source == SuggestionSource.TIME_OF_DAY })
        assertTrue((morning + evening).none { it.actionId.startsWith("com.android.") })
    }

    @Test
    fun `unresolved anchors produce no fallback chip`() = runTest {
        val sut = TimeOfDaySuggestionProvider(FakeSuggestionActionTargetResolver(defaultSupported = false))

        TimeOfDay.entries.forEach { bucket ->
            val result = sut.provide(SuggestionContext(timeOfDay = bucket, nowEpochMs = 0L))
            assertTrue("expected unresolved $bucket anchors to be empty", result.isEmpty())
        }
    }
}
