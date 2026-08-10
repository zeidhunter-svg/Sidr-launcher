package com.sidr.launcher.feature.assistant

import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeGenerativeAiEngine
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.domain.ai.AiChunk
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.AiRequest
import com.sidr.launcher.domain.ai.AiStopReason
import com.sidr.launcher.domain.ai.GenerateReplyUseCase
import com.sidr.launcher.domain.ai.PromptContextBuilder
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.domain.security.SecureSecretStore
import com.sidr.launcher.domain.security.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var configRepo: FakeAiProviderConfigRepository
    private lateinit var secretStore: FakeSecureSecretStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        configRepo = FakeAiProviderConfigRepository()
        secretStore = FakeSecureSecretStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildVm(
        chunks: List<AiChunk> = emptyList(),
        script: ((AiRequest) -> List<AiChunk>)? = null,
    ): AssistantViewModel {
        val engine = FakeGenerativeAiEngine(chunks = chunks, script = script)
        val useCase = GenerateReplyUseCase(engine, PromptContextBuilder())
        return AssistantViewModel(useCase, configRepo, secretStore)
    }

    // ── send / stream ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `send accumulates Text deltas in order and ends Done on Completed COMPLETE`() = runTest {
        val vm = buildVm(
            chunks = listOf(
                AiChunk.Text("Hello "),
                AiChunk.Text("world"),
                AiChunk.Completed(AiStopReason.COMPLETE),
            ),
        )
        vm.send("hi")
        val state = vm.uiState.value
        assertEquals("Hello world", state.reply)
        assertEquals(AssistantStatus.Done(refused = false), state.status)
    }

    @Test
    fun `send with no terminal chunk leaves stream in Streaming status`() = runTest {
        val vm = buildVm(
            chunks = listOf(AiChunk.Text("partial")),
        )
        vm.send("hi")
        val state = vm.uiState.value
        assertEquals("partial", state.reply)
        assertEquals(AssistantStatus.Streaming, state.status)
    }

    // ── refusal ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `refusal emits Done(refused=true), NOT Error`() = runTest {
        val vm = buildVm(
            chunks = listOf(
                AiChunk.Text("I cannot help with that."),
                AiChunk.Completed(AiStopReason.REFUSAL),
            ),
        )
        vm.send("harmful request")
        val status = vm.uiState.value.status
        assertEquals(AssistantStatus.Done(refused = true), status)
        assertFalse("refusal must not be Error", status is AssistantStatus.Error)
    }

    // ── failure mapping ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `failure Unauthorized emits Error retryable=false showProviderCta=true`() = runTest {
        val vm = buildVm(chunks = listOf(AiChunk.Failed(AiError.Unauthorized)))
        vm.send("hi")
        val status = vm.uiState.value.status as AssistantStatus.Error
        assertFalse("Unauthorized must not be button-retryable", status.retryable)
        assertTrue("Unauthorized must surface provider CTA", status.showProviderCta)
    }

    @Test
    fun `failure MissingCredentials emits Error retryable=false showProviderCta=true`() = runTest {
        val vm = buildVm(chunks = listOf(AiChunk.Failed(AiError.MissingCredentials)))
        vm.send("hi")
        val status = vm.uiState.value.status as AssistantStatus.Error
        assertFalse(status.retryable)
        assertTrue(status.showProviderCta)
    }

    @Test
    fun `failure Network emits Error retryable=true showProviderCta=false`() = runTest {
        val vm = buildVm(chunks = listOf(AiChunk.Failed(AiError.Network("connection refused"))))
        vm.send("hi")
        val status = vm.uiState.value.status as AssistantStatus.Error
        assertTrue("Network must be button-retryable", status.retryable)
        assertFalse(status.showProviderCta)
    }

    @Test
    fun `failure InvalidRequest emits Error retryable=false showProviderCta=false`() = runTest {
        val vm = buildVm(chunks = listOf(AiChunk.Failed(AiError.InvalidRequest("bad model"))))
        vm.send("hi")
        val status = vm.uiState.value.status as AssistantStatus.Error
        assertFalse(status.retryable)
        assertFalse(status.showProviderCta)
    }

    @Test
    fun `AiError retry and provider CTA classification covers every variant`() {
        val cases = listOf(
            AiError.Offline to (true to false),
            AiError.MissingCredentials to (false to true),
            AiError.Unauthorized to (false to true),
            AiError.RateLimited(retryAfterMs = 1_000L) to (true to false),
            AiError.Timeout to (true to false),
            AiError.Network("connection reset") to (true to false),
            AiError.ServerError(statusCode = 500) to (true to false),
            AiError.InvalidRequest("bad model") to (false to false),
            AiError.Unknown("unmapped") to (true to false),
        )

        cases.forEach { (error, expected) ->
            val (retryable, showProviderCta) = expected
            assertEquals("${error::class.simpleName} retryable", retryable, error.isButtonRetryable())
            assertEquals("${error::class.simpleName} provider CTA", showProviderCta, error.needsProviderSetup())
        }
    }

    @Test
    fun `RateLimited message says retry`() {
        val error = AiError.RateLimited(retryAfterMs = 1_000L).toUiError() as UiError.Message

        assertEquals("Rate limited. Please wait and retry.", error.text)
        assertFalse("RateLimited message must not contain old typo", error.text.contains("retray"))
    }

    // ── retry latest-wins ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `retry re-sends with same prompt and resets reply`() = runTest {
        var callCount = 0
        val vm = buildVm(script = { _ ->
            callCount++
            listOf(
                AiChunk.Text("call$callCount"),
                AiChunk.Completed(AiStopReason.COMPLETE),
            )
        })
        vm.send("question")
        assertEquals("call1", vm.uiState.value.reply)

        vm.retry()
        assertEquals("call2", vm.uiState.value.reply)
        assertEquals(AssistantStatus.Done(refused = false), vm.uiState.value.status)
    }

    @Test
    fun `retry when no prior send does nothing`() = runTest {
        val vm = buildVm()
        vm.retry() // no lastPrompt — must not crash
        assertEquals(AssistantStatus.Idle, vm.uiState.value.status)
    }

    // ── saveProvider ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `saveProvider persists config and key and key value never appears in uiState`() = runTest {
        val vm = buildVm()
        vm.saveProvider("https://openrouter.ai/api/v1", "mistralai/mistral-7b-instruct", "secret-key")
        advanceUntilIdle()

        assertEquals(1, configRepo.setCalls.size)
        val saved = configRepo.setCalls.first()
        assertEquals("openrouter.ai", saved.providerId.value)
        assertEquals("https://openrouter.ai/api/v1", saved.baseUrl)
        assertEquals("mistralai/mistral-7b-instruct", saved.modelId.value)

        assertEquals(1, secretStore.putCalls.size)
        assertEquals(SecretKeys.apiKey(saved.providerId), secretStore.putCalls.first().first)

        // Key value must never appear in state
        assertFalse(
            "key value must not leak into uiState",
            vm.uiState.value.toString().contains("secret-key"),
        )
    }

    @Test
    fun `saveProvider with new key immediately marks keySet true`() = runTest {
        val vm = buildVm()
        vm.saveProvider("https://openrouter.ai/api/v1", "mistralai/mistral-7b-instruct", "secret-key")
        advanceUntilIdle()

        assertTrue("saved key must be reflected in the form immediately", vm.uiState.value.form.keySet)
    }

    @Test
    fun `saveProvider with blank key skips secretStore put`() = runTest {
        val vm = buildVm()
        vm.saveProvider("https://example.com", "gpt-4o-mini", "")
        advanceUntilIdle()

        assertEquals(1, configRepo.setCalls.size)
        assertEquals(0, secretStore.putCalls.size)
    }

    @Test
    fun `saveProvider rejects non-https URL with inline error, nothing persisted`() = runTest {
        val vm = buildVm()
        vm.saveProvider("http://evil.com", "model", "key")
        advanceUntilIdle()

        assertEquals(0, configRepo.setCalls.size)
        assertEquals(0, secretStore.putCalls.size)
        val saveError = vm.uiState.value.form.saveError
        assertNotNull(saveError)
        assertTrue("error must mention https", saveError!!.contains("https", ignoreCase = true))
    }

    @Test
    fun `saveProvider derives providerId from host only (lowercase, path stripped)`() = runTest {
        val vm = buildVm()
        vm.saveProvider("https://OpenRouter.ai/api/v1", "model", "")
        advanceUntilIdle()

        val saved = configRepo.setCalls.first()
        assertEquals("openrouter.ai", saved.providerId.value)
    }

    /**
     * Regression, found on-device during DS-10 (2026-08-10). The provider form and the chat live on two
     * nav destinations, so they hold two ViewModel instances over the same repositories, and `keySet` is
     * only recomputed when `activeConfig()` emits. Writing the config *before* the key therefore leaves a
     * window in which the other instance reads the secret store, finds nothing, and caches "no key set"
     * until the next config change — which is exactly what the device showed.
     *
     * The interleaving itself is not reproducible against in-memory fakes (whether the collector resumes
     * inside or after the gap is a scheduling detail), so this pins the invariant that removes the window
     * instead: **the key is persisted before the config that announces it.**
     */
    @Test
    fun `saveProvider writes the key before it announces the config`() = runTest {
        val order = mutableListOf<String>()
        val recordingSecrets = object : SecureSecretStore {
            override suspend fun get(key: SecretKey) = secretStore.get(key)
            override suspend fun put(key: SecretKey, value: String): OperationResult<Unit> {
                order += "key"
                return secretStore.put(key, value)
            }

            override suspend fun remove(key: SecretKey) = secretStore.remove(key)
        }
        val recordingConfig = object : AiProviderConfigRepository {
            override fun activeConfig() = configRepo.activeConfig()
            override suspend fun setActiveConfig(config: AiProviderConfig): OperationResult<Unit> {
                order += "config"
                return configRepo.setActiveConfig(config)
            }

            override suspend fun clearActiveConfig() = configRepo.clearActiveConfig()
        }
        val vm = AssistantViewModel(
            GenerateReplyUseCase(FakeGenerativeAiEngine(chunks = emptyList()), PromptContextBuilder()),
            recordingConfig,
            recordingSecrets,
        )

        vm.saveProvider("https://openrouter.ai/api/v1", "gpt-4o-mini", "secret-key")
        advanceUntilIdle()

        assertEquals(listOf("key", "config"), order)
        assertTrue("the saving screen still reflects the key", vm.uiState.value.form.keySet)
    }

    // ── config presence / form state ──────────────────────────────────────────────────────────────

    @Test
    fun `null config — form baseUrl is blank (first-run, show form prominently)`() = runTest {
        val vm = buildVm()
        assertTrue("no config → form.baseUrl must be blank", vm.uiState.value.form.baseUrl.isBlank())
    }

    @Test
    fun `config update reflects in form state`() = runTest {
        val vm = buildVm()
        configRepo.setActiveConfig(
            AiProviderConfig(
                providerId = AiProviderId("openrouter.ai"),
                baseUrl = "https://openrouter.ai/api/v1",
                modelId = AiModelId("test-model"),
            ),
        )
        advanceUntilIdle()

        assertEquals("https://openrouter.ai/api/v1", vm.uiState.value.form.baseUrl)
        assertEquals("test-model", vm.uiState.value.form.modelId)
    }

    @Test
    fun `keySet is true when key is stored for active provider`() = runTest {
        val provider = AiProviderConfig(
            providerId = AiProviderId("example.com"),
            baseUrl = "https://example.com",
            modelId = AiModelId("m"),
        )
        secretStore.put(SecretKeys.apiKey(provider.providerId), "stored-key")
        configRepo.setActiveConfig(provider)

        val vm = buildVm()
        advanceUntilIdle()

        assertTrue("keySet must be true when key is stored", vm.uiState.value.form.keySet)
    }

    @Test
    fun `keySet is false when no key stored`() = runTest {
        val provider = AiProviderConfig(
            providerId = AiProviderId("example.com"),
            baseUrl = "https://example.com",
            modelId = AiModelId("m"),
        )
        configRepo.setActiveConfig(provider)

        val vm = buildVm()
        advanceUntilIdle()

        assertFalse("keySet must be false when no key stored", vm.uiState.value.form.keySet)
    }

    // ── form error is cleared on config change ─────────────────────────────────────────────────────

    @Test
    fun `save error is cleared after successful saveProvider`() = runTest {
        val vm = buildVm()
        // First attempt fails validation
        vm.saveProvider("http://bad.com", "model", "key")
        assertNotNull(vm.uiState.value.form.saveError)

        // Correct attempt clears the error
        vm.saveProvider("https://good.com", "model", "key")
        advanceUntilIdle()
        assertNull(vm.uiState.value.form.saveError)
    }
}
