package com.sidr.launcher.di

import com.sidr.launcher.core.common.di.ApplicationScope
import com.sidr.launcher.data.repository.intent.LayeredIntentMatcher
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
     * Block R — the unqualified [IntentMatcher] is now the rule-first [LayeredIntentMatcher]
     * composing the [RuleMatcher] (rule) + [NluMatcher] (ONNX NLU) sources from
     * [NluMatcherProvidesModule]. The use-case provider below is unchanged: it still injects the
     * single unqualified [IntentMatcher] and never sees the swap (Fork P6-2).
     */
    @Provides
    @Singleton
    fun provideIntentMatcher(
        @RuleMatcher ruleMatcher: IntentMatcher,
        @NluMatcher nluMatcher: IntentMatcher,
        confidencePolicy: IntentConfidencePolicy,
    ): IntentMatcher = LayeredIntentMatcher(
        primary = ruleMatcher,
        secondary = nluMatcher,
        policy = confidencePolicy,
    )

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
