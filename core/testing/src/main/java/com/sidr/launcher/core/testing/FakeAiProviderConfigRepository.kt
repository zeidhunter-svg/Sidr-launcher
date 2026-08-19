package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake for [AiProviderConfigRepository]. Backed by a [MutableStateFlow] so reads observe
 * writes; records [setActiveConfig] calls. When [errorToReturn] is non-null, writes return
 * [OperationResult.Failure] without mutating state. Not wired into any Hilt graph — use directly in
 * unit tests.
 */
class FakeAiProviderConfigRepository(
    initial: AiProviderConfig? = null,
) : AiProviderConfigRepository {

    private val state = MutableStateFlow(initial)

    /** When non-null, write operations return [OperationResult.Failure] and do not mutate state. */
    var errorToReturn: OperationError? = null

    val setCalls = mutableListOf<AiProviderConfig>()

    override fun activeConfig(): Flow<AiProviderConfig?> = state.asStateFlow()

    override suspend fun setActiveConfig(config: AiProviderConfig): OperationResult<Unit> {
        setCalls += config
        errorToReturn?.let { return OperationResult.Failure(it) }
        state.value = config
        return OperationResult.Success(Unit)
    }

    override suspend fun clearActiveConfig(): OperationResult<Unit> {
        errorToReturn?.let { return OperationResult.Failure(it) }
        state.value = null
        return OperationResult.Success(Unit)
    }
}

/**
 * A repository holding one syntactically valid, configured provider — the shape every caller of
 * `RouteCommandUseCase` needs since Этап 4.0, where "a provider is configured" became a gate
 * condition rather than an implementation detail of the planner (ADR 1/4, fork F4).
 *
 * Shared rather than re-typed per test on purpose: a fixture that differs subtly between suites
 * (an `http://` base URL here, a blank model there) makes two tests disagree about what "configured"
 * means, and the gate's behaviour is exactly what those tests are pinning down. Nothing here is a
 * real endpoint — no test may reach the network.
 */
fun configuredProvider(): FakeAiProviderConfigRepository = FakeAiProviderConfigRepository(
    initial = AiProviderConfig(
        providerId = AiProviderId("test"),
        baseUrl = "https://api.test/v1",
        modelId = AiModelId("test-model"),
    ),
)
