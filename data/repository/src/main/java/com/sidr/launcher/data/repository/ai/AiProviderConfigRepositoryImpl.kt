package com.sidr.launcher.data.repository.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.preferences.PreferencesKeys
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * DataStore-backed [AiProviderConfigRepository] over the shared `sidr_preferences` store (Block E
 * pattern) — the config is the **non-secret** part of a provider (opaque id + base URL + free-text
 * model + optional display name). The API key is NOT here; it lives in the Keystore-backed
 * `SecureSecretStore` (separate `sidr_secrets` store).
 *
 * Reads expose a [Flow] (falling back to defaults on [IOException]); writes return [OperationResult]
 * and never throw. [activeConfig] emits `null` until the three required keys (id, base URL, model)
 * are all present — a half-written config reads as "not configured".
 */
class AiProviderConfigRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AiProviderConfigRepository {

    override fun activeConfig(): Flow<AiProviderConfig?> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                val id = prefs[PreferencesKeys.AI_PROVIDER_ID]
                val baseUrl = prefs[PreferencesKeys.AI_PROVIDER_BASE_URL]
                val model = prefs[PreferencesKeys.AI_PROVIDER_MODEL]
                if (id.isNullOrEmpty() || baseUrl.isNullOrEmpty() || model.isNullOrEmpty()) {
                    null
                } else {
                    AiProviderConfig(
                        providerId = AiProviderId(id),
                        baseUrl = baseUrl,
                        modelId = AiModelId(model),
                        displayName = prefs[PreferencesKeys.AI_PROVIDER_DISPLAY_NAME],
                    )
                }
            }

    override suspend fun setActiveConfig(config: AiProviderConfig): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { prefs ->
                    prefs[PreferencesKeys.AI_PROVIDER_ID] = config.providerId.value
                    prefs[PreferencesKeys.AI_PROVIDER_BASE_URL] = config.baseUrl
                    prefs[PreferencesKeys.AI_PROVIDER_MODEL] = config.modelId.value
                    val displayName = config.displayName
                    if (displayName != null) {
                        prefs[PreferencesKeys.AI_PROVIDER_DISPLAY_NAME] = displayName
                    } else {
                        prefs.remove(PreferencesKeys.AI_PROVIDER_DISPLAY_NAME)
                    }
                }
                OperationResult.Success(Unit)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "datastore_write_failed"))
            }
        }

    override suspend fun clearActiveConfig(): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { prefs ->
                    prefs.remove(PreferencesKeys.AI_PROVIDER_ID)
                    prefs.remove(PreferencesKeys.AI_PROVIDER_BASE_URL)
                    prefs.remove(PreferencesKeys.AI_PROVIDER_MODEL)
                    prefs.remove(PreferencesKeys.AI_PROVIDER_DISPLAY_NAME)
                }
                OperationResult.Success(Unit)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "datastore_write_failed"))
            }
        }
}
