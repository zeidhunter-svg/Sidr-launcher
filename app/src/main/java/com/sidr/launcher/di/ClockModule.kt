package com.sidr.launcher.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

/**
 * The wall clock a ViewModel judges freshness by (A4′ acceptance tail, task 12: Home's favourites leave the automatic
 * places after 14 days unopened). One binding, [Clock.systemUTC] — only `millis()` is read, so the zone is
 * irrelevant; tests pass `Clock.fixed(...)` straight to the constructor. `PrayerProvidesModule`'s `Clock.systemDefaultZone()`
 * is a use-case argument there, not a binding.
 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()
}
