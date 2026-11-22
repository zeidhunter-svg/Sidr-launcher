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
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.text.TextFolding
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
     * `RouteCommandUseCase` and, at its step 7, the bridge's `ModelPlanner` (A4′ phase 3a), not by this
     * binding.
     */
    @Provides
    @Singleton
    fun provideIntentMatcher(): IntentMatcher = RuleBasedIntentMatcher()

    @Provides
    @Singleton
    fun provideConfidencePolicy(): IntentConfidencePolicy = DefaultIntentConfidencePolicy()

    /**
     * FastPath's app names are matched by the device's one [TextFolding] (A4′ phase 3b, Task 7): the
     * binding `BridgeProvidesModule.provideTextFolding` makes once, injected here and passed on — never a
     * second binding, never the resolver's `PlainTextFolding` default (`TextFoldingBindingGuardTest`).
     *
     * Since the A4′ acceptance tail, task 3 (K10), FastPath's launch reads the user's aliases first — the one
     * [AliasStore] binding (`MemoryBindsModule`), injected here. The parameter's default exists for the hand-built
     * call in `TextFoldingBindingGuardTest` only; Hilt passes every parameter and ignores a Kotlin default.
     */
    @Provides
    @Singleton
    fun provideIntentActionResolver(
        repository: InstalledAppsRepository,
        folding: TextFolding,
        aliases: AliasStore = AliasStore.NONE,
    ): IntentActionResolver = IntentActionResolver(repository, folding, aliases)

    /**
     * Executes a `LauncherAction` over the same resolver/executor path the rule pipeline uses. Since A4′
     * phase 3a deleted the router's confirm card, its callers are the `in_app` tool worker (below the
     * engine's consent checkpoint) and the web route chip (a destination the user chose).
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
