package com.sidr.launcher.data.repository.ailocal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.preferences.PreferencesKeys
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelId
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
 * DataStore-backed [ModelAvailabilityRepository] over the shared `sidr_preferences` store (Block E
 * pattern). Availability is persisted as a set of verified [ModelId] values under
 * [PreferencesKeys.MODEL_AVAILABLE_IDS]; the download worker calls [markAvailable] only **after** the
 * artifact's SHA-256 has been verified and atomically promoted by `ModelStore`.
 *
 * Two states are persisted — [ModelAvailability.Available] (id present) and [ModelAvailability.Missing]
 * (absent). [ModelAvailability.Unverified] is never written: the on-disk gate is the real load-time
 * guard (`LocalModelFiles.modelFile()` returns null if the verified file is gone), so this flag is the
 * reactive *projection* of availability, not a second integrity source. The gate treats Missing and
 * Unverified identically, so a stale-but-present flag with a deleted file still degrades safely.
 *
 * Reads expose a [Flow] (falling back to defaults on [IOException]); writes return [OperationResult]
 * and never throw — codebase-wide convention.
 */
class ModelAvailabilityRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelAvailabilityRepository {

    override fun availability(modelId: ModelId): Flow<ModelAvailability> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                val ids = prefs[PreferencesKeys.MODEL_AVAILABLE_IDS] ?: emptySet()
                if (modelId.value in ids) ModelAvailability.Available else ModelAvailability.Missing
            }

    override suspend fun markAvailable(modelId: ModelId): OperationResult<Unit> =
        edit { current -> current + modelId.value }

    override suspend fun markMissing(modelId: ModelId): OperationResult<Unit> =
        edit { current -> current - modelId.value }

    private suspend fun edit(transform: (Set<String>) -> Set<String>): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { prefs ->
                    val current = prefs[PreferencesKeys.MODEL_AVAILABLE_IDS] ?: emptySet()
                    prefs[PreferencesKeys.MODEL_AVAILABLE_IDS] = transform(current)
                }
                OperationResult.Success(Unit)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "datastore_write_failed"))
            }
        }
}
