package com.sidr.launcher.data.repository.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionPrefsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * DataStore-backed [PermissionPrefsRepository] (Block G, Fork 5).
 *
 * Only [PermissionFeature.requestable] features map to a persisted key (today: WALLPAPER). A
 * dormant feature has no key: [isDismissed] emits `false` and [setDismissed] is a no-op Success,
 * so callers can treat all features uniformly without leaking the "only wallpaper is live" fact.
 *
 * Reads fall back to defaults on [IOException]; writes catch [IOException] → [OperationError],
 * never throwing to the caller (mirrors the other preference repositories).
 */
class PermissionPrefsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PermissionPrefsRepository {

    override fun isDismissed(feature: PermissionFeature): Flow<Boolean> {
        val key = keyFor(feature) ?: return flowOf(false)
        return dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[key] ?: false }
    }

    override suspend fun setDismissed(
        feature: PermissionFeature,
        dismissed: Boolean,
    ): OperationResult<Unit> {
        val key = keyFor(feature) ?: return OperationResult.Success(Unit)
        return withContext(ioDispatcher) {
            try {
                dataStore.edit { it[key] = dismissed }
                OperationResult.Success(Unit)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "datastore_write_failed"))
            }
        }
    }

    /** DataStore key for a feature, or null for dormant features (no request flow → nothing to dismiss). */
    private fun keyFor(feature: PermissionFeature): Preferences.Key<Boolean>? = when (feature) {
        PermissionFeature.WALLPAPER -> PreferencesKeys.PERM_DISMISSED_WALLPAPER
        PermissionFeature.VOICE_INPUT,
        PermissionFeature.CALENDAR_SUGGESTIONS,
        PermissionFeature.LOCATION_SUGGESTIONS,
        -> null
    }
}
