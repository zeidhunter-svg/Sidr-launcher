package com.sidr.launcher.data.repository.ai

import com.sidr.launcher.data.repository.preferences.createTestDataStore
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
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
class AiProviderConfigRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun file(): File = File(tmpFolder.root, "ai_provider.preferences_pb")

    private val config = AiProviderConfig(
        providerId = AiProviderId("openai-compatible"),
        baseUrl = "https://openrouter.ai/api/v1",
        modelId = AiModelId("openai/gpt-4o-mini"),
        displayName = "OpenRouter",
    )

    @Test
    fun `active config is null until configured`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = AiProviderConfigRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertNull(repo.activeConfig().first())
        scope.cancel()
    }

    @Test
    fun `set then read round-trips and survives a process restart`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()

        val writeScope = CoroutineScope(dispatcher + Job())
        val result = AiProviderConfigRepositoryImpl(createTestDataStore(target, writeScope), dispatcher)
            .setActiveConfig(config)
        assertTrue(result is OperationResult.Success)
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = AiProviderConfigRepositoryImpl(createTestDataStore(target, readScope), dispatcher)
        assertEquals(config, reopened.activeConfig().first())
        readScope.cancel()
    }

    @Test
    fun `config without display name round-trips with null display name`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = AiProviderConfigRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        repo.setActiveConfig(config.copy(displayName = null))
        assertEquals(config.copy(displayName = null), repo.activeConfig().first())
        scope.cancel()
    }

    @Test
    fun `clear resets active config to null`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = AiProviderConfigRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        repo.setActiveConfig(config)
        assertEquals(config, repo.activeConfig().first())

        val cleared = repo.clearActiveConfig()
        assertTrue(cleared is OperationResult.Success)
        assertNull(repo.activeConfig().first())
        scope.cancel()
    }
}
