package com.sidr.launcher.di

import com.sidr.launcher.data.repository.ai.AiProviderConfigRepositoryImpl
import com.sidr.launcher.data.repository.ailocal.ModelAvailabilityRepositoryImpl
import com.sidr.launcher.data.repository.preferences.DeviceProfileCacheRepositoryImpl
import com.sidr.launcher.data.repository.preferences.FeatureFlagRepositoryImpl
import com.sidr.launcher.data.repository.preferences.PermissionPrefsRepositoryImpl
import com.sidr.launcher.data.repository.preferences.SuggestionsCacheRepositoryImpl
import com.sidr.launcher.data.repository.preferences.UserPreferencesRepositoryImpl
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.permission.PermissionPrefsRepository
import com.sidr.launcher.domain.preferences.DeviceProfileCacheRepository
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.SuggestionsCacheRepository
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the DataStore-backed preference repository implementations to their domain interfaces.
 * Mirrors [RepositoryModule]. Concrete DataStore provision lives in [PersistenceProvidesModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceBindsModule {

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesRepositoryImpl,
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindFeatureFlagRepository(
        impl: FeatureFlagRepositoryImpl,
    ): FeatureFlagRepository

    @Binds
    @Singleton
    abstract fun bindDeviceProfileCacheRepository(
        impl: DeviceProfileCacheRepositoryImpl,
    ): DeviceProfileCacheRepository

    @Binds
    @Singleton
    abstract fun bindSuggestionsCacheRepository(
        impl: SuggestionsCacheRepositoryImpl,
    ): SuggestionsCacheRepository

    // Block G — per-feature permission-education "dismissed" flag, DataStore-backed.
    @Binds
    @Singleton
    abstract fun bindPermissionPrefsRepository(
        impl: PermissionPrefsRepositoryImpl,
    ): PermissionPrefsRepository

    // Block K — non-secret active AI provider config (id + base URL + free-text model), DataStore-backed
    // over the shared sidr_preferences store. The API key is held separately in SecureSecretStore.
    @Binds
    @Singleton
    abstract fun bindAiProviderConfigRepository(
        impl: AiProviderConfigRepositoryImpl,
    ): AiProviderConfigRepository

    // Block Q — observable local-NLU model availability (DataStore-backed set of verified ModelIds).
    @Binds
    @Singleton
    abstract fun bindModelAvailabilityRepository(
        impl: ModelAvailabilityRepositoryImpl,
    ): ModelAvailabilityRepository
}
