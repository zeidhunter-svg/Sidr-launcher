package com.sidr.launcher.di

import com.sidr.launcher.work.UniquePeriodicWorkScheduler
import com.sidr.launcher.work.WorkManagerUniquePeriodicWorkScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the tiny WorkManager scheduling facade used by Phase-7 periodic work so the orchestration
 * remains unit-testable without a real WorkManager instance.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkSchedulingBindsModule {

    @Binds
    @Singleton
    abstract fun bindUniquePeriodicWorkScheduler(
        impl: WorkManagerUniquePeriodicWorkScheduler,
    ): UniquePeriodicWorkScheduler
}
