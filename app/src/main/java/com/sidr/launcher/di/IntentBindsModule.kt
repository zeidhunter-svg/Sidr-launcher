package com.sidr.launcher.di

import com.sidr.launcher.data.repository.intent.AndroidActionExecutor
import com.sidr.launcher.data.repository.intent.UsageRecordingActionExecutor
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the [ActionExecutor] port: the Android [AndroidActionExecutor] (lives in :data:repository) wrapped in
 * [UsageRecordingActionExecutor], so every launch — a grid or drawer tap, FastPath, a command through the agent
 * engine — writes `app_usage` in this one place (A4′ tail fix round, task 2). It was an abstract `@Binds` module;
 * a decorator needs a `@Provides`, so it is an `object` now. The pure-domain `@Provides` wiring lives in
 * [IntentProvidesModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object IntentBindsModule {

    @Provides
    @Singleton
    fun provideActionExecutor(
        android: AndroidActionExecutor,
        usage: UsageHistoryRepository,
        flags: FeatureFlagRepository,
    ): ActionExecutor = UsageRecordingActionExecutor(
        delegate = android,
        usage = usage,
        flags = flags,
        nowMs = System::currentTimeMillis,
    )
}
