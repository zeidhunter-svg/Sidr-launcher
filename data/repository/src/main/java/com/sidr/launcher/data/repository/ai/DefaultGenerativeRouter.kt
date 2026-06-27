package com.sidr.launcher.data.repository.ai

import com.sidr.launcher.domain.ai.AiChunk
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.AiRequest
import com.sidr.launcher.domain.ai.GenerativeAiEngine
import com.sidr.launcher.domain.ai.GenerativeRouter
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.domain.security.SecureSecretStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

/**
 * Ordered composite engine (Block M, Forks P5-4/6/7).
 *
 * Selection order at each collection:
 *   1. Ph6 ONNX slot (reserved — no impl yet; insert here with no edit to cloud/static branches)
 *   2. Cloud [cloud] — iff online **and** provider config present **and** non-blank key stored
 *   3. Static fallback [fallback] — always eligible, never fails
 *
 * Selection runs inside the cold [flow] so connectivity/key/config changes between calls are
 * respected (latest-wins). [canUseCloud] never throws: [SecureSecretStore.get] returns
 * [OperationResult] by contract; [firstOrNull] handles a non-emitting [activeConfig] flow
 * defensively. A [OperationResult.Failure] from the store is treated as "no usable key → static".
 *
 * Constructor takes plain [GenerativeAiEngine] params (no qualifier annotations); `:app`'s
 * [GenerationProvidesModule] supplies the qualified bindings positionally, keeping this class
 * `:domain`-port-only with no data→data edge (the cloud engine and future ONNX engine are injected
 * as the port type, never as concrete implementations).
 */
class DefaultGenerativeRouter(
    private val cloud: GenerativeAiEngine,
    private val fallback: GenerativeAiEngine,
    private val connectivity: ConnectivityChecker,
    private val secretStore: SecureSecretStore,
    private val configRepo: AiProviderConfigRepository,
) : GenerativeRouter {

    override fun generate(request: AiRequest): Flow<AiChunk> = flow {
        emitAll(selectEngine().generate(request))
    }

    private suspend fun selectEngine(): GenerativeAiEngine {
        // Ph6 ONNX slot — ahead of cloud so a local engine inserts here with no rewrite of
        // the cloud/static branches. Today there is no ONNX impl; this comment is the slot.
        return if (canUseCloud()) cloud else fallback
    }

    private suspend fun canUseCloud(): Boolean {
        if (!connectivity.isOnline()) return false
        val config = configRepo.activeConfig().firstOrNull() ?: return false
        val result = secretStore.get(SecretKeys.apiKey(config.providerId))
        val key = (result as? OperationResult.Success)?.value
        return !key.isNullOrBlank()
    }
}
