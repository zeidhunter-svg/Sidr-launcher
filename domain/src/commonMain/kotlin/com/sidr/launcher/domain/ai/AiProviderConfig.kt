package com.sidr.launcher.domain.ai

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

/**
 * NON-SECRET provider configuration. The API key is NOT here — it lives in `SecureSecretStore`
 * (Keystore-backed), keyed by `SecretKeys.apiKey(providerId)`. [baseUrl] is the chat-completions base
 * (e.g. ".../v1") for a compatible HTTP API; it is validated/normalized in the adapter, not in the
 * domain. [modelId] is the user-set free-text model string — the domain never interprets it.
 *
 * Single active config for Phase 5 (multi-config is a later concern). The DataStore impl of
 * [AiProviderConfigRepository] lands in Block K, alongside the cloud adapter that consumes it.
 */
data class AiProviderConfig(
    val providerId: AiProviderId,
    val baseUrl: String,
    val modelId: AiModelId,
    val displayName: String? = null,
)

/**
 * Stores the single active [AiProviderConfig] (non-secret part). Reads expose a [Flow]; writes return
 * [OperationResult] and never throw to the caller — the [Flow]/`OperationResult` split mirrors the
 * other persistence ports. The API key is held separately in `SecureSecretStore`.
 */
interface AiProviderConfigRepository {
    /** The single active provider config, or `null` if none is configured yet. */
    fun activeConfig(): Flow<AiProviderConfig?>

    suspend fun setActiveConfig(config: AiProviderConfig): OperationResult<Unit>

    suspend fun clearActiveConfig(): OperationResult<Unit>
}
