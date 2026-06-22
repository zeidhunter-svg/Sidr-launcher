package com.sidr.launcher.data.repository.preferences

import com.sidr.launcher.domain.preferences.DeviceProfileCacheEntry
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceProfileCacheRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun file(): File = File(tmpFolder.root, "device.preferences_pb")

    @Test
    fun `emits null when no profile cached`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = DeviceProfileCacheRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertNull(repo.getCachedProfile().first())
        scope.cancel()
    }

    @Test
    fun `cached profile survives a process restart`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()
        val entry = DeviceProfileCacheEntry(isLowEndDevice = true, cachedAtEpochMs = 1_234_567L)

        val writeScope = CoroutineScope(dispatcher + Job())
        val result = DeviceProfileCacheRepositoryImpl(createTestDataStore(target, writeScope), dispatcher)
            .updateCache(entry)
        assertTrue(result is OperationResult.Success)
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = DeviceProfileCacheRepositoryImpl(createTestDataStore(target, readScope), dispatcher)
        assertEquals(entry, reopened.getCachedProfile().first())
        readScope.cancel()
    }

    @Test
    fun `clearCache resets to null`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = DeviceProfileCacheRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        repo.updateCache(DeviceProfileCacheEntry(isLowEndDevice = true, cachedAtEpochMs = 99L))
        assertEquals(
            DeviceProfileCacheEntry(isLowEndDevice = true, cachedAtEpochMs = 99L),
            repo.getCachedProfile().first(),
        )

        val cleared = repo.clearCache()
        assertTrue(cleared is OperationResult.Success)
        assertNull(repo.getCachedProfile().first())
        scope.cancel()
    }
}
