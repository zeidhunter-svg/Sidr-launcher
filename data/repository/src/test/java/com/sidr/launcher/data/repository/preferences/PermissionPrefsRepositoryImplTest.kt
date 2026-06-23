package com.sidr.launcher.data.repository.preferences

import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionPrefsRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun file(): File = File(tmpFolder.root, "perm.preferences_pb")

    @Test
    fun `defaults to not dismissed on empty store`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = PermissionPrefsRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertFalse(repo.isDismissed(PermissionFeature.WALLPAPER).first())
        scope.cancel()
    }

    @Test
    fun `set then read round-trips for a requestable feature`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = PermissionPrefsRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        val result = repo.setDismissed(PermissionFeature.WALLPAPER, true)

        assertTrue(result is OperationResult.Success)
        assertTrue(repo.isDismissed(PermissionFeature.WALLPAPER).first())
        scope.cancel()
    }

    @Test
    fun `dismissed flag survives a process restart`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()

        val writeScope = CoroutineScope(dispatcher + Job())
        PermissionPrefsRepositoryImpl(createTestDataStore(target, writeScope), dispatcher)
            .setDismissed(PermissionFeature.WALLPAPER, true)
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = PermissionPrefsRepositoryImpl(createTestDataStore(target, readScope), dispatcher)
        assertTrue(reopened.isDismissed(PermissionFeature.WALLPAPER).first())
        readScope.cancel()
    }

    @Test
    fun `dormant feature is always not-dismissed and its setter is a no-op success`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = PermissionPrefsRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        // A dormant feature has no request flow → no key → setter persists nothing.
        val result = repo.setDismissed(PermissionFeature.VOICE_INPUT, true)

        assertTrue(result is OperationResult.Success)
        assertFalse(repo.isDismissed(PermissionFeature.VOICE_INPUT).first())
        // And it does not bleed into the requestable feature's flag.
        assertFalse(repo.isDismissed(PermissionFeature.WALLPAPER).first())
        scope.cancel()
    }
}
