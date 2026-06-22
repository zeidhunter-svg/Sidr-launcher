package com.sidr.launcher.data.repository.preferences

import com.sidr.launcher.domain.preferences.FeatureFlags
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
class FeatureFlagRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun file(): File = File(tmpFolder.root, "flags.preferences_pb")

    @Test
    fun `returns default flags on empty store`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = FeatureFlagRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertEquals(FeatureFlags(), repo.getFlags().first())
        scope.cancel()
    }

    @Test
    fun `write then read round-trips`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = FeatureFlagRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        val updated = FeatureFlags(
            aiSuggestionsEnabled = true,
            usageHistoryEnabled = true,
            permissionEducationDismissed = true,
        )
        val result = repo.updateFlags(updated)

        assertTrue(result is OperationResult.Success)
        assertEquals(updated, repo.getFlags().first())
        scope.cancel()
    }

    @Test
    fun `written flags survive a process restart`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()
        val updated = FeatureFlags(aiSuggestionsEnabled = true)

        val writeScope = CoroutineScope(dispatcher + Job())
        FeatureFlagRepositoryImpl(createTestDataStore(target, writeScope), dispatcher)
            .updateFlags(updated)
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = FeatureFlagRepositoryImpl(createTestDataStore(target, readScope), dispatcher)
        assertEquals(updated, reopened.getFlags().first())
        readScope.cancel()
    }
}
