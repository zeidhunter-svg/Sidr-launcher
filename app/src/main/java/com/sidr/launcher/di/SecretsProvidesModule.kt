package com.sidr.launcher.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.security.SecretsDataStore
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
 * Provides the **dedicated** `sidr_secrets` Preferences [DataStore] for [SecureSecretStoreImpl]
 * (Block J). Qualified with [SecretsDataStore] so it never collides with Block E's unqualified
 * `sidr_preferences` store ([PersistenceProvidesModule]). Separate file + scope by design — the
 * secrets store holds only Keystore-ciphertext blobs. Split from the @Binds module per the Hilt rule
 * (no @Provides + @Binds in one module — Block D/E precedent).
 */
@Module
@InstallIn(SingletonComponent::class)
object SecretsProvidesModule {

    private const val SECRETS_FILE_NAME = "sidr_secrets"

    @Provides
    @Singleton
    @SecretsDataStore
    fun provideSecretsDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(ioDispatcher + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile(SECRETS_FILE_NAME) },
        )
}
