package com.sidr.launcher.di

import com.sidr.launcher.data.prayer.PrayerPreferencesRepositoryImpl
import com.sidr.launcher.data.prayer.PrayerScheduleCacheImpl
import com.sidr.launcher.domain.prayer.PrayerPreferencesRepository
import com.sidr.launcher.domain.prayer.PrayerScheduleCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Interface-to-implementation bindings for the DS-6B prayer stack; the pure-domain `@Provides`
 * wiring lives in [PrayerProvidesModule]. Both impls already carry `@Inject constructor(dataStore,
 * @IoDispatcher ioDispatcher)` over the shared unqualified `DataStore<Preferences>` provided in
 * [PersistenceProvidesModule] — no extra wiring is needed here beyond the interface binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PrayerBindsModule {

    @Binds
    @Singleton
    abstract fun bindPrayerPreferencesRepository(
        impl: PrayerPreferencesRepositoryImpl,
    ): PrayerPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindPrayerScheduleCache(impl: PrayerScheduleCacheImpl): PrayerScheduleCache
}
