package com.sidr.launcher.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sidr.launcher.core.common.di.IoDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Provides the single Preferences [DataStore] instance shared by all preference repositories.
 * Split from [PersistenceBindsModule] because Hilt forbids mixing @Provides (concrete) with
 * @Binds (abstract) in one module (see Block D ADR — IntentProvidesModule / IntentBindsModule).
 */
@Module
@InstallIn(SingletonComponent::class)
object PersistenceProvidesModule {

    private const val PREFERENCES_FILE_NAME = "sidr_preferences"

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE_NAME) },
        )
}
