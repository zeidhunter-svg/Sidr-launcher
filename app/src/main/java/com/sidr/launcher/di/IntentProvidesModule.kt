package com.sidr.launcher.di

import com.sidr.launcher.data.repository.intent.RuleBasedIntentMatcher
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.IntentConfidencePolicy
import com.sidr.launcher.domain.intent.IntentMatcher
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the pure-domain collaborators of the Block D intent pipeline. These are plain Kotlin
 * classes without `@Inject`, so they are constructed here. `CommandNormalizer` is an `object`
 * invoked statically inside the use case — nothing to provide. The [ActionExecutor] binding lives
 * in [IntentBindsModule].
 */
@Module
@InstallIn(SingletonComponent::class)
object IntentProvidesModule {

    @Provides
    @Singleton
    fun provideIntentMatcher(): IntentMatcher = RuleBasedIntentMatcher()

    @Provides
    @Singleton
    fun provideConfidencePolicy(): IntentConfidencePolicy = DefaultIntentConfidencePolicy()

    @Provides
    @Singleton
    fun provideIntentActionResolver(
        repository: InstalledAppsRepository,
    ): IntentActionResolver = IntentActionResolver(repository)

    @Provides
    @Singleton
    fun provideHandleUserCommandUseCase(
        matcher: IntentMatcher,
        resolver: IntentActionResolver,
        executor: ActionExecutor,
        confidencePolicy: IntentConfidencePolicy,
    ): HandleUserCommandUseCase = HandleUserCommandUseCase(
        matcher = matcher,
        resolver = resolver,
        executor = executor,
        confidencePolicy = confidencePolicy,
    )
}
