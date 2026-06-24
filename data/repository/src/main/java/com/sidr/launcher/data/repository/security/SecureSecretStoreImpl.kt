package com.sidr.launcher.data.repository.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKey
import com.sidr.launcher.domain.security.SecureSecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Base64
import javax.inject.Inject

/**
 * Keystore-backed [SecureSecretStore] (Block J, BYOK, per-provider). Orchestrates a [SecretCipher]
 * (crypto) and the dedicated [SecretsDataStore] `DataStore<Preferences>` (persistence) — the split
 * keeps this class JVM-testable with a fake cipher while the only Keystore code lives in
 * [KeystoreSecretCipher].
 *
 * Each [SecretKey] maps to one DataStore entry holding the Base64-encoded `iv:ciphertext` blob, so two
 * providers' secrets are isolated and `remove(one)` never touches another. The persisted file is
 * `sidr_secrets` — separate from Block E's `sidr_preferences`, so these credential-named keys never
 * enter the Phase-4 privacy-guarded key set.
 *
 * Never throws to the caller (`CancellationException` excepted, which is re-thrown):
 * - [get]: a decrypt failure / key-invalidation / corrupt or unreadable blob → `Success(null)` (no
 *   usable secret — "re-enter") and the corrupt entry is cleared so the next [put] regenerates cleanly.
 * - [put] / [remove]: an encrypt or I/O failure → `Failure(UnknownError)`.
 */
class SecureSecretStoreImpl @Inject constructor(
    @SecretsDataStore private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SecureSecretStore {

    override suspend fun get(key: SecretKey): OperationResult<String?> = withContext(ioDispatcher) {
        val prefKey = stringPreferencesKey(key.value)
        try {
            val encoded = dataStore.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .map { it[prefKey] }
                .first()
                ?: return@withContext OperationResult.Success(null)

            val blob = decodeBlob(encoded)
            val plaintext = blob?.let { cipher.decrypt(it) }
            if (plaintext == null) {
                // Corrupt blob or unusable key → drop the entry, signal "re-enter".
                clearEntry(prefKey)
                OperationResult.Success(null)
            } else {
                OperationResult.Success(plaintext)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No usable secret rather than a crash; the corrupt entry (if any) is cleared.
            clearEntry(prefKey)
            OperationResult.Success(null)
        }
    }

    override suspend fun put(key: SecretKey, value: String): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                val encoded = encodeBlob(cipher.encrypt(value))
                dataStore.edit { it[stringPreferencesKey(key.value)] = encoded }
                OperationResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Failure(OperationError.UnknownError(reason = "secret_put_failed"))
            }
        }

    override suspend fun remove(key: SecretKey): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { it.remove(stringPreferencesKey(key.value)) }
                OperationResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Failure(OperationError.UnknownError(reason = "secret_remove_failed"))
            }
        }

    /** Best-effort delete of a corrupt/unusable entry; failures here must not surface from [get]. */
    private suspend fun clearEntry(prefKey: Preferences.Key<String>) {
        try {
            dataStore.edit { it.remove(prefKey) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Swallow — the read already decided "no usable secret".
        }
    }

    private fun encodeBlob(blob: EncryptedBlob): String {
        val encoder = Base64.getEncoder()
        return encoder.encodeToString(blob.iv) + SEPARATOR + encoder.encodeToString(blob.ciphertext)
    }

    private fun decodeBlob(encoded: String): EncryptedBlob? {
        val parts = encoded.split(SEPARATOR)
        if (parts.size != 2) return null
        return try {
            val decoder = Base64.getDecoder()
            EncryptedBlob(iv = decoder.decode(parts[0]), ciphertext = decoder.decode(parts[1]))
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private companion object {
        const val SEPARATOR = ":"
    }
}
