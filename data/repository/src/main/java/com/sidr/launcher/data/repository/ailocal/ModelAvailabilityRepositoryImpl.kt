package com.sidr.launcher.data.repository.ailocal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.preferences.PreferencesKeys
import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelFilePresence
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
 * pattern), **cross-checked against on-disk presence** ([ModelFilePresence], P1-2).
 *
 * The persisted marker (`model_available_ids`) records that the worker verified + promoted a model;
 * [ModelFilePresence] reports whether the verified file is still physically present. The availability
 * surface is the **conjunction**, so all three Block-O states are reachable and the
 * "no unverified model ever loaded" invariant holds even if the marker and disk drift:
 *
 *  - marker set **and** file present → [ModelAvailability.Available]
 *  - file present **but** unmarked   → [ModelAvailability.Unverified] (e.g. a crash between
 *    `ModelStore.promote`'s atomic-rename and `markAvailable`; the next provision run re-marks it)
 *  - otherwise (file gone with a stale marker, or never present) → [ModelAvailability.Missing]
 *
 * A marker-set-but-file-missing thus reads as **not** Available — the gate degrades to the rule path
 * rather than trusting a stale flag. Reads expose a [Flow] (falling back to defaults on [IOException]);
 * writes return [OperationResult] and never throw.
 */
class ModelAvailabilityRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val filePresence: ModelFilePresence,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ModelAvailabilityRepository {

    override fun availability(modelId: ModelId): Flow<ModelAvailability> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                val marked = modelId.value in (prefs[PreferencesKeys.MODEL_AVAILABLE_IDS] ?: emptySet())
                val present = filePresence.isModelPresent(modelId)
                when {
                    marked && present -> ModelAvailability.Available
                    present -> ModelAvailability.Unverified
                    else -> ModelAvailability.Missing
                }
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
