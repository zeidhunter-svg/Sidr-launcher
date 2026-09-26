package com.sidr.launcher.di

import com.sidr.launcher.domain.suggestions.SuggestionScheduling
import com.sidr.launcher.work.SuggestionSchedulingImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the [SuggestionScheduling] domain port to its `:app` WorkManager-backed implementation
 * (Block X5, Fork X5-A). Consumed by `:feature:settings`' SettingsViewModel.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SuggestionSchedulingModule {

    @Binds
    @Singleton
    abstract fun bindSuggestionScheduling(impl: SuggestionSchedulingImpl): SuggestionScheduling
}
