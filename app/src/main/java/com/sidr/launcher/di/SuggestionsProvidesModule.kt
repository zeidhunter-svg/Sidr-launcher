package com.sidr.launcher.di

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.ailocal.provision.ModelDownloadConfig
import com.sidr.launcher.data.repository.suggestions.CalendarSuggestionProvider
import com.sidr.launcher.data.repository.suggestions.LocationSuggestionProvider
import com.sidr.launcher.data.repository.suggestions.SemanticSuggestionRanker
import com.sidr.launcher.data.repository.suggestions.SuggestionEngineImpl
import com.sidr.launcher.data.repository.suggestions.TimeOfDaySuggestionProvider
import com.sidr.launcher.data.repository.suggestions.UsageSuggestionProvider
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.TextEmbedder
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.history.SuggestionRankingRepository
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.SuggestionsCacheRepository
import com.sidr.launcher.domain.suggestions.HeuristicSuggestionRanker
import com.sidr.launcher.domain.suggestions.SuggestionActionTargetResolver
import com.sidr.launcher.domain.suggestions.SuggestionEngine
import com.sidr.launcher.domain.suggestions.SuggestionRanker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * Wires the Block-U suggestion engine (Phase 7, Block U3).
 *
 * Composes the offline-first providers ([TimeOfDaySuggestionProvider], [UsageSuggestionProvider]) and
 * the opt-in, permission-gated providers ([CalendarSuggestionProvider], [LocationSuggestionProvider])
 * into a single [SuggestionEngine]. [SuggestionEngineImpl] takes a composed `List<SuggestionProvider>`
 * rather than a constructor injection, so it is wired via `@Provides` here — mirroring the Block-M
 * precedent ([GenerationProvidesModule]) for objects composed from multiple ports rather than a single
 * `@Binds` target.
 *
 * Nothing here runs on the launcher cold path — [SuggestionEngine.refresh] is gated by
 * `FeatureFlags.aiSuggestionsEnabled` inside [SuggestionEngineImpl] and is only invoked by the host
 * `LauncherViewModel` (Block W2).
 */
@Module
@InstallIn(SingletonComponent::class)
object SuggestionsProvidesModule {

    @Provides
    @Singleton
    fun provideSuggestionRanker(
        textEmbedder: TextEmbedder,
        deviceProfileProvider: DeviceProfileProvider,
        modelAvailabilityRepository: ModelAvailabilityRepository,
        @EmbeddingModelConfig embeddingConfig: ModelDownloadConfig,
    ): SuggestionRanker = SemanticSuggestionRanker(
        heuristic = HeuristicSuggestionRanker(),
        textEmbedder = textEmbedder,
        deviceProfileProvider = deviceProfileProvider,
        modelAvailabilityRepository = modelAvailabilityRepository,
        embeddingModelId = embeddingConfig.modelId,
        embeddingModelPinned = embeddingConfig.isPinned,
    )

    @Provides
    @Singleton
    fun provideSuggestionEngine(
        timeOfDayProvider: TimeOfDaySuggestionProvider,
        usageProvider: UsageSuggestionProvider,
        calendarProvider: CalendarSuggestionProvider,
        locationProvider: LocationSuggestionProvider,
        ranker: SuggestionRanker,
        rankingRepository: SuggestionRankingRepository,
        cacheRepository: SuggestionsCacheRepository,
        featureFlagRepository: FeatureFlagRepository,
        actionTargetResolver: SuggestionActionTargetResolver,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): SuggestionEngine = SuggestionEngineImpl(
        providers = listOf(timeOfDayProvider, usageProvider, calendarProvider, locationProvider),
        ranker = ranker,
        rankingRepository = rankingRepository,
        cacheRepository = cacheRepository,
        featureFlagRepository = featureFlagRepository,
        actionTargetResolver = actionTargetResolver,
        ioDispatcher = ioDispatcher,
    )
}
