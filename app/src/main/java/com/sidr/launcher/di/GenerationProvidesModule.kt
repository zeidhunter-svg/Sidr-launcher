package com.sidr.launcher.di

import com.sidr.launcher.data.repository.ai.DefaultGenerativeRouter
import com.sidr.launcher.data.repository.ai.StaticFallbackEngine
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.GenerateReplyUseCase
import com.sidr.launcher.domain.ai.GenerativeAiEngine
import com.sidr.launcher.domain.ai.GenerativeRouter
import com.sidr.launcher.domain.ai.PromptContextBuilder
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.security.SecureSecretStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires the Block-M generation seam (Block M).
 *
 * Binding summary:
 *  - [@FallbackEngine] [GenerativeAiEngine] → [StaticFallbackEngine] (canned, no network)
 *  - [@CloudEngine] [GenerativeAiEngine] → provided by [AiCloudProvidesModule] (Block K)
 *  - [GenerativeRouter] → [DefaultGenerativeRouter] composing cloud + fallback via ports only
 *  - unqualified [GenerativeAiEngine] → the [GenerativeRouter] (the only unqualified binding, so
 *    [GenerateReplyUseCase] receives it without ambiguity)
 *  - [PromptContextBuilder] → plain default instance (no Android context needed)
 *  - [GenerateReplyUseCase] → depends on unqualified engine + builder above
 *
 * Nothing here is on the launcher cold path — all bindings are lazy singletons injected only
 * when the assistant surface (Block N) is first used.
 */
@Module
@InstallIn(SingletonComponent::class)
object GenerationProvidesModule {

    @Provides
    @Singleton
    @FallbackEngine
    fun provideStaticFallbackEngine(): GenerativeAiEngine = StaticFallbackEngine()

    @Provides
    @Singleton
    fun provideGenerativeRouter(
        @CloudEngine cloud: GenerativeAiEngine,
        @FallbackEngine fallback: GenerativeAiEngine,
        connectivity: ConnectivityChecker,
        secretStore: SecureSecretStore,
        configRepo: AiProviderConfigRepository,
    ): GenerativeRouter = DefaultGenerativeRouter(
        cloud = cloud,
        fallback = fallback,
        connectivity = connectivity,
        secretStore = secretStore,
        configRepo = configRepo,
    )

    @Provides
    @Singleton
    fun provideGenerativeAiEngine(router: GenerativeRouter): GenerativeAiEngine = router

    @Provides
    @Singleton
    fun providePromptContextBuilder(): PromptContextBuilder = PromptContextBuilder()

    @Provides
    @Singleton
    fun provideGenerateReplyUseCase(
        engine: GenerativeAiEngine,
        promptContextBuilder: PromptContextBuilder,
    ): GenerateReplyUseCase = GenerateReplyUseCase(engine, promptContextBuilder)
}
