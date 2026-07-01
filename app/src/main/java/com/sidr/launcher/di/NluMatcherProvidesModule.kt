package com.sidr.launcher.di

import com.sidr.launcher.data.ailocal.LocalModelFiles
import com.sidr.launcher.data.ailocal.OnnxIntentClassifier
import com.sidr.launcher.data.ailocal.OnnxTextEmbedder
import com.sidr.launcher.data.ailocal.provision.ModelDownloadConfig
import com.sidr.launcher.data.ailocal.session.OnnxSessionFactory
import com.sidr.launcher.data.ailocal.session.SessionLifecycle
import com.sidr.launcher.domain.ai.local.TextEmbedder
import com.sidr.launcher.data.repository.intent.RuleBasedIntentMatcher
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.intent.IntentMatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Block R / R2 — the two qualified [IntentMatcher] sources `LayeredIntentMatcher` composes, plus the
 * ONNX session factory. The unqualified `IntentMatcher` (= `LayeredIntentMatcher`) is bound in
 * [IntentProvidesModule]; `HandleUserCommandUseCase` is untouched.
 *
 * The collaborators the classifier needs ([DeviceProfileProvider], [LocalModelFiles],
 * [ModelAvailabilityRepository], [ModelDownloadConfig]) are all already in the SingletonComponent
 * graph from Block Q's [ModelProvisionProvidesModule]. Constructing [OnnxIntentClassifier] does
 * **no** ONNX work (lazy session — Block P), so binding it on a LOW_END device is safe.
 *
     * Instance identity (a hard invariant): [provideOnnxIntentClassifier] is `@Singleton`, so the
     * [NluMatcher] `IntentMatcher` and the [SessionLifecycle] set entry the `onTrimMemory` hook tears
     * down are the **same** object — otherwise teardown would free a different, empty instance.
 */
@Module
@InstallIn(SingletonComponent::class)
object NluMatcherProvidesModule {

    @Provides
    @Singleton
    @RuleMatcher
    fun provideRuleMatcher(): IntentMatcher = RuleBasedIntentMatcher()

    @Provides
    @Singleton
    fun provideOnnxSessionFactory(): OnnxSessionFactory = OnnxSessionFactory()

    @Provides
    @Singleton
    fun provideOnnxIntentClassifier(
        deviceProfileProvider: DeviceProfileProvider,
        modelAvailabilityRepository: ModelAvailabilityRepository,
        modelFiles: LocalModelFiles,
        sessionFactory: OnnxSessionFactory,
        config: ModelDownloadConfig,
    ): OnnxIntentClassifier = OnnxIntentClassifier(
        deviceProfileProvider = deviceProfileProvider,
        modelAvailabilityRepository = modelAvailabilityRepository,
        modelFiles = modelFiles,
        sessionFactory = sessionFactory,
        // Same ModelId Block Q's provisioner/manager use — keep the classifier and the download in sync.
        modelId = config.modelId,
    )

    @Provides
    @Singleton
    @NluMatcher
    fun provideNluMatcher(classifier: OnnxIntentClassifier): IntentMatcher = classifier

    @Provides
    @Singleton
    @IntoSet
    fun provideNluSessionLifecycle(classifier: OnnxIntentClassifier): SessionLifecycle = classifier

    @Provides
    @Singleton
    fun provideOnnxTextEmbedder(
        deviceProfileProvider: DeviceProfileProvider,
        modelAvailabilityRepository: ModelAvailabilityRepository,
        modelFiles: LocalModelFiles,
        sessionFactory: OnnxSessionFactory,
        @EmbeddingModelConfig config: ModelDownloadConfig,
    ): OnnxTextEmbedder = OnnxTextEmbedder(
        deviceProfileProvider = deviceProfileProvider,
        modelAvailabilityRepository = modelAvailabilityRepository,
        modelFiles = modelFiles,
        sessionFactory = sessionFactory,
        modelId = config.modelId,
    )

    @Provides
    @Singleton
    fun provideTextEmbedder(embedder: OnnxTextEmbedder): TextEmbedder = embedder

    @Provides
    @Singleton
    @IntoSet
    fun provideEmbeddingSessionLifecycle(embedder: OnnxTextEmbedder): SessionLifecycle = embedder
}
