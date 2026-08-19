package com.sidr.launcher.di

import com.sidr.launcher.core.common.di.ApplicationScope
import com.sidr.launcher.data.repository.intent.RuleBasedIntentMatcher
import com.sidr.launcher.domain.history.IntentMatchHistoryRepository
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.IntentConfidencePolicy
import com.sidr.launcher.domain.intent.IntentMatcher
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
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

    /**
     * Этап 0.3 — the ONNX secondary is gone (agentic-restart ADR 2/4); the unqualified [IntentMatcher]
     * binds directly to [RuleBasedIntentMatcher]. FastPath stays a latency optimization, not the
     * understanding boundary (see CLAUDE.md Hard rules) — everything past a FastPath miss is routed by
     * `RouteCommandUseCase`/`CommandPlanner`, not by this binding.
     */
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

    /**
     * AIL-5 — executes a *confirmed* router-proposed `LauncherAction` over the same resolver/executor
     * path the rule pipeline uses. Only reached after the user confirms/one-taps a proposal.
     */
    @Provides
    @Singleton
    fun provideExecuteActionUseCase(
        resolver: IntentActionResolver,
        executor: ActionExecutor,
    ): ExecuteActionUseCase = ExecuteActionUseCase(
        resolver = resolver,
        executor = executor,
    )

    @Provides
    @Singleton
    fun provideHandleUserCommandUseCase(
        matcher: IntentMatcher,
        resolver: IntentActionResolver,
        executor: ActionExecutor,
        confidencePolicy: IntentConfidencePolicy,
        intentMatchHistory: IntentMatchHistoryRepository,
        featureFlagRepository: FeatureFlagRepository,
        @ApplicationScope recordingScope: CoroutineScope,
    ): HandleUserCommandUseCase = HandleUserCommandUseCase(
        matcher = matcher,
        resolver = resolver,
        executor = executor,
        confidencePolicy = confidencePolicy,
        intentMatchHistory = intentMatchHistory,
        featureFlagRepository = featureFlagRepository,
        recordingScope = recordingScope,
    )
}
