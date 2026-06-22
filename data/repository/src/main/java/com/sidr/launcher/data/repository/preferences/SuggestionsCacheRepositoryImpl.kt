package com.sidr.launcher.data.repository.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.preferences.CachedSuggestion
import com.sidr.launcher.domain.preferences.SuggestionsCacheRepository
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
 * Last-known suggestions as a UI-repaint cache (display projection only — see
 * [CachedSuggestion] and the PreferencesKeys privacy inventory). The list is bounded
 * to MAX_CACHED_SUGGESTIONS in the mapper on write.
 */
class SuggestionsCacheRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SuggestionsCacheRepository {

    override fun getCachedSuggestions(): Flow<List<CachedSuggestion>> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { PreferencesMapper.toCachedSuggestions(it) }

    override suspend fun updateCachedSuggestions(
        suggestions: List<CachedSuggestion>,
    ): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { PreferencesMapper.writeCachedSuggestions(it, suggestions) }
                OperationResult.Success(Unit)
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "datastore_write_failed"))
            }
        }
}
