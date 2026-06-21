package com.sidr.launcher.di

import com.sidr.launcher.data.repository.intent.AndroidActionExecutor
import com.sidr.launcher.data.repository.intent.RuleBasedIntentMatcher
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.IntentConfidencePolicy
import com.sidr.launcher.domain.intent.IntentMatcher
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the Block D intent pipeline into the Hilt graph.
 *
 * - [ActionExecutor] is bound to the Android [AndroidActionExecutor] (lives in :data:repository),
 *   mirroring how [com.sidr.launcher.di.RepositoryModule] binds the repository.
 * - The pure-domain collaborators ([IntentMatcher], [IntentConfidencePolicy],
 *   [IntentActionResolver], [HandleUserCommandUseCase]) are plain Kotlin classes without
 *   `@Inject`, so they are provided here. `CommandNormalizer` is an `object` invoked statically
 *   inside the use case — nothing to provide.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IntentModule {

    @Binds
    @Singleton
    abstract fun bindActionExecutor(impl: AndroidActionExecutor): ActionExecutor

    @Module
    @InstallIn(SingletonComponent::class)
    companion object {

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
}
