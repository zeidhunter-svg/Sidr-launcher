package com.sidr.launcher.data.repository.preferences

import com.sidr.launcher.domain.preferences.UserPreferences
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
class UserPreferencesRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun file(): File = File(tmpFolder.root, "user.preferences_pb")

    @Test
    fun `returns defaults on empty store`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = UserPreferencesRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertEquals(UserPreferences(), repo.getPreferences().first())
        scope.cancel()
    }

    @Test
    fun `write then read round-trips within the same instance`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = UserPreferencesRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        val updated = UserPreferences(
            themeName = "dark",
            accentColor = "amber",
            commandInputEnabled = false,
            favoritesCount = 4,
            micInputEnabled = false,
            setupHintDismissed = true,
            // Deliberately the NON-default value (DS-11 A1 made `false` the default): a round-trip
            // assertion only proves persistence when the written value differs from the default.
            autoHideNavBar = true,
        )
        val result = repo.updatePreferences(updated)

        assertTrue(result is OperationResult.Success)
        assertEquals(updated, repo.getPreferences().first())
        scope.cancel()
    }

    @Test
    fun `written preferences survive a process restart`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()
        val updated = UserPreferences(
            themeName = "light",
            accentColor = "amber",
            commandInputEnabled = false,
            favoritesCount = 10,
            micInputEnabled = false,
            setupHintDismissed = true,
            // Non-default on purpose — see the round-trip test above.
            autoHideNavBar = true,
        )

        // First "process": write, then release the file lock.
        val writeScope = CoroutineScope(dispatcher + Job())
        UserPreferencesRepositoryImpl(createTestDataStore(target, writeScope), dispatcher)
            .updatePreferences(updated)
        writeScope.cancel()

        // Second "process": a fresh DataStore on the same file must read the persisted value.
        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = UserPreferencesRepositoryImpl(createTestDataStore(target, readScope), dispatcher)
        assertEquals(updated, reopened.getPreferences().first())
        readScope.cancel()
    }
    @Test
    fun `pinned apps round-trip in the order of pinning and hidden apps as a set`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = UserPreferencesRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        val updated = UserPreferences(
            pinnedFavorites = listOf("com.zeta", "com.alpha", "org.mid.app"),
            hiddenFavorites = setOf("com.hidden.one", "com.hidden.two"),
        )
        assertTrue(repo.updatePreferences(updated) is OperationResult.Success)

        val read = repo.getPreferences().first()
        assertEquals(listOf("com.zeta", "com.alpha", "org.mid.app"), read.pinnedFavorites)
        assertEquals(setOf("com.hidden.one", "com.hidden.two"), read.hiddenFavorites)
        scope.cancel()
    }

    @Test
    fun `an empty pinned list reads back empty and a fresh store has none`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = UserPreferencesRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertEquals(emptyList<String>(), repo.getPreferences().first().pinnedFavorites)
        repo.updatePreferences(UserPreferences(pinnedFavorites = listOf("com.a")))
        repo.updatePreferences(UserPreferences(pinnedFavorites = emptyList()))
        assertEquals(emptyList<String>(), repo.getPreferences().first().pinnedFavorites)
        scope.cancel()
    }

    @Test
    fun `an unset favorites count reads 12 and a stored one is read back`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = UserPreferencesRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertEquals(12, repo.getPreferences().first().favoritesCount)
        repo.updatePreferences(UserPreferences(favoritesCount = 8))
        assertEquals(8, repo.getPreferences().first().favoritesCount)
        scope.cancel()
    }
}
