package com.sidr.launcher.data.repository.ai

import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeGenerativeAiEngine
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.domain.ai.AiChunk
import com.sidr.launcher.domain.ai.AiMessage
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.AiRequest
import com.sidr.launcher.domain.ai.AiRole
import com.sidr.launcher.domain.ai.AiStopReason
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.security.SecretKeys
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultGenerativeRouterTest {

    private val providerId = AiProviderId("test-provider")
    private val config = AiProviderConfig(
        providerId = providerId,
        baseUrl = "https://api.test.com/v1",
        modelId = AiModelId("test-model"),
    )
    private val request = AiRequest(messages = listOf(AiMessage(AiRole.USER, "hello")), maxOutputTokens = 512)

    private val cloudChunks = listOf(
        AiChunk.Text("cloud reply"),
        AiChunk.Completed(AiStopReason.COMPLETE),
    )
    private val fallback = StaticFallbackEngine()

    private fun router(
        cloud: FakeGenerativeAiEngine = FakeGenerativeAiEngine(cloudChunks),
        connectivity: FakeConnectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
        secretStore: FakeSecureSecretStore = FakeSecureSecretStore(),
        configRepo: FakeAiProviderConfigRepository = FakeAiProviderConfigRepository(config),
    ) = DefaultGenerativeRouter(
        cloud = cloud,
        fallback = fallback,
        connectivity = connectivity,
        secretStore = secretStore,
        configRepo = configRepo,
    )

    // ── Offline → static ────────────────────────────────────────────────────

    @Test
    fun `offline routes to static fallback`() = runTest {
        val cloud = FakeGenerativeAiEngine(cloudChunks)
        val store = FakeSecureSecretStore().also { it.put(SecretKeys.apiKey(providerId), "key123") }
        val r = router(
            cloud = cloud,
            connectivity = FakeConnectivityChecker(initiallyOnline = false),
            secretStore = store,
        )

        val chunks = r.generate(request).toList()

        assertNull("cloud must not be invoked when offline", cloud.lastRequest)
        assertTrue(chunks.contains(AiChunk.Text(StaticFallbackEngine.STATIC_REPLY)))
        assertTrue(chunks.last() is AiChunk.Completed)
    }

    // ── Online + config + key → cloud ────────────────────────────────────────

    @Test
    fun `online with config and key routes to cloud`() = runTest {
        val cloud = FakeGenerativeAiEngine(cloudChunks)
        val store = FakeSecureSecretStore().also { it.put(SecretKeys.apiKey(providerId), "key123") }
        val r = router(cloud = cloud, secretStore = store)

        val chunks = r.generate(request).toList()

        assertNotNull("cloud must be invoked", cloud.lastRequest)
        assertEquals(cloudChunks, chunks)
    }

    // ── Online + config + no key → static ────────────────────────────────────

    @Test
    fun `online with config but no key routes to static fallback`() = runTest {
        val cloud = FakeGenerativeAiEngine(cloudChunks)
        val r = router(cloud = cloud) // store is empty by default

        val chunks = r.generate(request).toList()

        assertNull("cloud must not be invoked without a key", cloud.lastRequest)
        assertTrue(chunks.contains(AiChunk.Text(StaticFallbackEngine.STATIC_REPLY)))
    }

    // ── Online + no config → static ──────────────────────────────────────────

    @Test
    fun `online with no config routes to static fallback`() = runTest {
        val cloud = FakeGenerativeAiEngine(cloudChunks)
        val store = FakeSecureSecretStore().also { it.put(SecretKeys.apiKey(providerId), "key123") }
        val r = router(
            cloud = cloud,
            secretStore = store,
            configRepo = FakeAiProviderConfigRepository(initial = null),
        )

        val chunks = r.generate(request).toList()

        assertNull("cloud must not be invoked without config", cloud.lastRequest)
        assertTrue(chunks.contains(AiChunk.Text(StaticFallbackEngine.STATIC_REPLY)))
    }

    // ── Online + config + key-read Failure → static ──────────────────────────

    @Test
    fun `key-read failure routes to static fallback without throwing`() = runTest {
        val cloud = FakeGenerativeAiEngine(cloudChunks)
        val store = FakeSecureSecretStore().also {
            it.errorToReturn = OperationError.UnknownError("forced_failure")
        }
        val r = router(cloud = cloud, secretStore = store)

        val chunks = r.generate(request).toList()

        assertNull("cloud must not be invoked on key-read failure", cloud.lastRequest)
        assertTrue(chunks.contains(AiChunk.Text(StaticFallbackEngine.STATIC_REPLY)))
        assertTrue("stream must terminate", chunks.last() is AiChunk.Completed)
    }

    // ── Selection re-evaluated per collection (latest-wins) ──────────────────

    @Test
    fun `selection re-evaluated between collections`() = runTest {
        val cloud = FakeGenerativeAiEngine(cloudChunks)
        val connectivity = FakeConnectivityChecker(initiallyOnline = false)
        val store = FakeSecureSecretStore().also { it.put(SecretKeys.apiKey(providerId), "key123") }
        val r = router(cloud = cloud, connectivity = connectivity, secretStore = store)

        // First collect: offline → static
        val first = r.generate(request).toList()
        assertNull(cloud.lastRequest)
        assertTrue(first.contains(AiChunk.Text(StaticFallbackEngine.STATIC_REPLY)))

        // Flip online
        connectivity.online = true

        // Second collect: online + key → cloud
        val second = r.generate(request).toList()
        assertNotNull(cloud.lastRequest)
        assertEquals(cloudChunks, second)
    }

    // ── Cancellation ─────────────────────────────────────────────────────────

    @Test
    fun `cancelling collection does not throw`() = runTest {
        val cloud = FakeGenerativeAiEngine(cloudChunks)
        val store = FakeSecureSecretStore().also { it.put(SecretKeys.apiKey(providerId), "key123") }
        val r = router(cloud = cloud, secretStore = store)

        // Collecting with takeWhile causes early cancellation — no exception expected
        val chunks = mutableListOf<AiChunk>()
        r.generate(request).collect { chunk ->
            chunks += chunk
            // Stop after first text chunk — simulates mid-stream cancellation
            if (chunk is AiChunk.Text) return@collect
        }
        assertTrue("at least the first Text chunk collected", chunks.isNotEmpty())
    }

    // ── Static fallback always terminates cleanly ─────────────────────────────

    @Test
    fun `static fallback produces text then Completed`() = runTest {
        val r = router(connectivity = FakeConnectivityChecker(initiallyOnline = false))

        val chunks = r.generate(request).toList()

        assertEquals(2, chunks.size)
        assertTrue(chunks[0] is AiChunk.Text)
        assertTrue(chunks[1] is AiChunk.Completed)
        val completed = chunks[1] as AiChunk.Completed
        assertEquals(AiStopReason.COMPLETE, completed.stopReason)
    }
}
