package com.sidr.launcher.data.repository.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class FeatureFlagRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FeatureFlagRepository {

    override fun getFlags(): Flow<FeatureFlags> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { PreferencesMapper.toFeatureFlags(it) }

    override suspend fun updateFlags(flags: FeatureFlags): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { PreferencesMapper.writeFeatureFlags(it, flags) }
                OperationResult.Success(Unit)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "datastore_write_failed"))
            }
        }
}
