package com.sidr.launcher.data.repository.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.preferences.DeviceProfileCacheEntry
import com.sidr.launcher.domain.preferences.DeviceProfileCacheRepository
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
 * Caches the *computed* device profile (E5). The detection logic stays in :core:android;
 * this repository only persists/retrieves the projected primitives. Emits null until the
 * first write, so callers can distinguish "no cache yet" from default values.
 */
class DeviceProfileCacheRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DeviceProfileCacheRepository {

    override fun getCachedProfile(): Flow<DeviceProfileCacheEntry?> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { PreferencesMapper.toDeviceProfileCacheEntry(it) }

    override suspend fun updateCache(entry: DeviceProfileCacheEntry): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { PreferencesMapper.writeDeviceProfileCacheEntry(it, entry) }
                OperationResult.Success(Unit)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "datastore_write_failed"))
            }
        }

    override suspend fun clearCache(): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { PreferencesMapper.clearDeviceProfileCache(it) }
                OperationResult.Success(Unit)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "datastore_write_failed"))
            }
        }
}
