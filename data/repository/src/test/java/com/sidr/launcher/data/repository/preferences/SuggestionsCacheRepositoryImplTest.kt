package com.sidr.launcher.data.repository.preferences

import com.sidr.launcher.domain.preferences.CachedSuggestion
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsCacheRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun file(): File = File(tmpFolder.root, "suggestions.preferences_pb")

    @Test
    fun `returns empty list on empty store`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = SuggestionsCacheRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertEquals(emptyList<CachedSuggestion>(), repo.getCachedSuggestions().first())
        scope.cancel()
    }

    @Test
    fun `JSON round-trip survives a process restart and preserves special characters`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()
        // Labels with pipes, quotes, newlines — would break a delimiter scheme; JSON is safe.
        val suggestions = listOf(
            CachedSuggestion(label = "Maps ||| \"home\"", actionId = "com.google.maps"),
            CachedSuggestion(label = "Notes\nline2", actionId = "route:notes"),
        )

        val writeScope = CoroutineScope(dispatcher + Job())
        val result = SuggestionsCacheRepositoryImpl(createTestDataStore(target, writeScope), dispatcher)
            .updateCachedSuggestions(suggestions)
        assertTrue(result is OperationResult.Success)
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = SuggestionsCacheRepositoryImpl(createTestDataStore(target, readScope), dispatcher)
        assertEquals(suggestions, reopened.getCachedSuggestions().first())
        readScope.cancel()
    }

    @Test
    fun `list is bounded to MAX_CACHED_SUGGESTIONS on write`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = SuggestionsCacheRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        val overflow = (1..PreferencesKeys.MAX_CACHED_SUGGESTIONS + 3).map {
            CachedSuggestion(label = "app$it", actionId = "pkg$it")
        }
        repo.updateCachedSuggestions(overflow)

        val stored = repo.getCachedSuggestions().first()
        assertEquals(PreferencesKeys.MAX_CACHED_SUGGESTIONS, stored.size)
        assertEquals(overflow.take(PreferencesKeys.MAX_CACHED_SUGGESTIONS), stored)
        scope.cancel()
    }
}
