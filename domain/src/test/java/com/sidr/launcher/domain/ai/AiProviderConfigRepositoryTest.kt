package com.sidr.launcher.domain.ai

import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderConfigRepositoryTest {

    private val config = AiProviderConfig(
        providerId = AiProviderId("cloud-compatible"),
        baseUrl = "https://example.test/v1",
        modelId = AiModelId("light-default"),
        displayName = "Example",
    )

    @Test
    fun `active config defaults to null`() = runTest {
        val repo = FakeAiProviderConfigRepository()
        assertNull(repo.activeConfig().first())
    }

    @Test
    fun `setActiveConfig is observed by activeConfig and recorded`() = runTest {
        val repo = FakeAiProviderConfigRepository()

        val result = repo.setActiveConfig(config)

        assertTrue(result is OperationResult.Success)
        assertEquals(config, repo.activeConfig().first())
        assertEquals(listOf(config), repo.setCalls)
    }

    @Test
    fun `clearActiveConfig returns to null`() = runTest {
        val repo = FakeAiProviderConfigRepository(initial = config)

        val result = repo.clearActiveConfig()

        assertTrue(result is OperationResult.Success)
        assertNull(repo.activeConfig().first())
    }
}
