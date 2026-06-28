package com.sidr.launcher.data.repository.ailocal

import com.sidr.launcher.data.repository.preferences.createTestDataStore
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelId
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
class ModelAvailabilityRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val modelId = ModelId("intent-nlu-v1")
    private val other = ModelId("other-model")

    private fun file(): File = File(tmp.root, "avail.preferences_pb")

    @Test
    fun `availability defaults to Missing`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = ModelAvailabilityRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertEquals(ModelAvailability.Missing, repo.availability(modelId).first())
        scope.cancel()
    }

    @Test
    fun `markAvailable flips to Available and survives a process restart`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()

        val writeScope = CoroutineScope(dispatcher + Job())
        val result = ModelAvailabilityRepositoryImpl(createTestDataStore(target, writeScope), dispatcher)
            .markAvailable(modelId)
        assertTrue(result is OperationResult.Success)
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = ModelAvailabilityRepositoryImpl(createTestDataStore(target, readScope), dispatcher)
        assertEquals(ModelAvailability.Available, reopened.availability(modelId).first())
        readScope.cancel()
    }

    @Test
    fun `markMissing removes only the targeted model`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = ModelAvailabilityRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        repo.markAvailable(modelId)
        repo.markAvailable(other)
        repo.markMissing(modelId)

        assertEquals(ModelAvailability.Missing, repo.availability(modelId).first())
        assertEquals(ModelAvailability.Available, repo.availability(other).first())
        scope.cancel()
    }
}
