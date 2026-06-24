package com.sidr.launcher.domain.security

import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.result.OperationResult

/** Opaque identifier for a stored secret slot. */
@JvmInline
value class SecretKey(val value: String)

/**
 * Generic, app-wide secret store. Not AI-specific — any secret is keyed by a [SecretKey].
 *
 * Returns [OperationResult] and **never throws** to the caller (I/O / crypto / key-invalidation
 * failures become [OperationResult.Failure]). The implementation (Block J) is Keystore-backed and
 * keeps ciphertext out of `:domain`; the port stays reversible (a backend-proxy impl can replace it
 * behind this same interface with no domain/UI change).
 */
interface SecureSecretStore {
    /** The stored secret for [key], or `null` if none is set. */
    suspend fun get(key: SecretKey): OperationResult<String?>

    /** Stores [value] under [key], replacing any existing secret. */
    suspend fun put(key: SecretKey, value: String): OperationResult<Unit>

    /** Removes [key]; succeeds even if no secret was stored. */
    suspend fun remove(key: SecretKey): OperationResult<Unit>
}

/**
 * Stable [SecretKey] conventions.
 *
 * Keys are **per-provider** so each provider's API key occupies its own slot and they never collide.
 * The slot name names a credential ("api key") by design — that is the secret store's own keyspace,
 * and the secret *value* lives encrypted in the Keystore-backed impl, never in a plaintext store.
 * (Contrast the DataStore privacy guard, which forbids content terms in *DataStore* key names.)
 */
object SecretKeys {
    fun apiKey(provider: AiProviderId): SecretKey = SecretKey("ai_api_key_${provider.value}")
}
