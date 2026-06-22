package com.sidr.launcher.data.repository.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.preferences.UserPreferences
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserPreferencesRepository {

    override fun getPreferences(): Flow<UserPreferences> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { PreferencesMapper.toUserPreferences(it) }

    override suspend fun updatePreferences(preferences: UserPreferences): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { PreferencesMapper.writeUserPreferences(it, preferences) }
                OperationResult.Success(Unit)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "datastore_write_failed"))
            }
        }
}
