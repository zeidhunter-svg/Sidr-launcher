package com.sidr.launcher.data.repository.ailocal

import com.sidr.launcher.data.repository.preferences.createTestDataStore
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelFilePresence
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
import org.junit.Assert.assertNotEquals
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

    /** Settable on-disk presence fake (per [ModelId]). */
    private class FakePresence(val present: MutableSet<String> = mutableSetOf()) : ModelFilePresence {
        override fun isModelPresent(modelId: ModelId): Boolean = modelId.value in present
    }

    private fun file(): File = File(tmp.root, "avail.preferences_pb")

    @Test
    fun `availability defaults to Missing`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = ModelAvailabilityRepositoryImpl(createTestDataStore(file(), scope), FakePresence(), dispatcher)

        assertEquals(ModelAvailability.Missing, repo.availability(modelId).first())
        scope.cancel()
    }

    @Test
    fun `marker set AND file present is Available and survives a process restart`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()
        val presence = FakePresence(mutableSetOf(modelId.value))

        val writeScope = CoroutineScope(dispatcher + Job())
        val result = ModelAvailabilityRepositoryImpl(createTestDataStore(target, writeScope), presence, dispatcher)
            .markAvailable(modelId)
        assertTrue(result is OperationResult.Success)
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = ModelAvailabilityRepositoryImpl(createTestDataStore(target, readScope), presence, dispatcher)
        assertEquals(ModelAvailability.Available, reopened.availability(modelId).first())
        readScope.cancel()
    }

    @Test
    fun `marker set but file MISSING reads as not-Available (P1-2)`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        // Marker present, but the on-disk file is gone (FakePresence empty).
        val repo = ModelAvailabilityRepositoryImpl(createTestDataStore(file(), scope), FakePresence(), dispatcher)

        repo.markAvailable(modelId)

        val state = repo.availability(modelId).first()
        assertNotEquals(ModelAvailability.Available, state)
        assertEquals(ModelAvailability.Missing, state)
        scope.cancel()
    }

    @Test
    fun `file present but unmarked reads as Unverified`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        // File on disk (e.g. crash between atomic-rename and markAvailable) but no marker.
        val repo = ModelAvailabilityRepositoryImpl(
            createTestDataStore(file(), scope), FakePresence(mutableSetOf(modelId.value)), dispatcher,
        )

        assertEquals(ModelAvailability.Unverified, repo.availability(modelId).first())
        scope.cancel()
    }

    @Test
    fun `markMissing removes only the targeted model`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val presence = FakePresence(mutableSetOf(modelId.value, other.value))
        val repo = ModelAvailabilityRepositoryImpl(createTestDataStore(file(), scope), presence, dispatcher)

        repo.markAvailable(modelId)
        repo.markAvailable(other)
        repo.markMissing(modelId)

        assertEquals(ModelAvailability.Unverified, repo.availability(modelId).first()) // file still present, unmarked
        assertEquals(ModelAvailability.Available, repo.availability(other).first())
        scope.cancel()
    }
}
