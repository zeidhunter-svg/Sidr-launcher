package com.sidr.launcher.di

import android.content.Context
import com.sidr.launcher.core.android.alarm.AndroidNextAlarmSource
import com.sidr.launcher.domain.alarm.NextAlarmSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Android [NextAlarmSource] (A4′ acceptance tail, task 8) — the clock's next alarm for Home's date-line
 * chip. `core/android` keeps no Hilt annotations, so the source is constructed here with the application [Context],
 * mirroring [ConnectivityModule]. Constructing it does nothing: the receiver is registered only while a collector is.
 */
@Module
@InstallIn(SingletonComponent::class)
object NextAlarmModule {

    @Provides
    @Singleton
    fun provideNextAlarmSource(
        @ApplicationContext context: Context,
    ): NextAlarmSource = AndroidNextAlarmSource(context)
}
