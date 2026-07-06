package com.sidr.launcher.di

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.aicloud.LlmCommandPlanner
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.router.CommandPlanner
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.security.SecureSecretStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * Wires the AIL-4 LLM Action Router (`CommandPlanner`) + its composition over the rule pipeline
 * (`RouteCommandUseCase`).
 *
 * - [CommandPlanner] → [LlmCommandPlanner] over the **shared** cloud [HttpClient] (from
 *   [AiCloudProvidesModule]) + the existing BYOK config/key ports. It is a separate, non-streaming
 *   request path; the assistant's streaming engine is untouched.
 * - [RouteCommandUseCase] composes the **unchanged** [HandleUserCommandUseCase] (rule-first) with the
 *   planner (consulted only on low confidence, and only when [FeatureFlagRepository]'s
 *   `llmRouterEnabled` is on — off ⇒ byte-for-byte rule-only parity).
 *
 * Nothing here is on the launcher cold path (all lazy singletons); the launcher core stays fully
 * offline. No `data → data` edge — the impl depends only on `domain` ports.
 */
@Module
@InstallIn(SingletonComponent::class)
object RouterProvidesModule {

    @Provides
    @Singleton
    fun provideCommandPlanner(
        httpClient: HttpClient,
        secretStore: SecureSecretStore,
        configRepository: AiProviderConfigRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CommandPlanner = LlmCommandPlanner(
        httpClient = httpClient,
        secretStore = secretStore,
        configRepository = configRepository,
        ioDispatcher = ioDispatcher,
    )

    @Provides
    @Singleton
    fun provideRouteCommandUseCase(
        handleUserCommand: HandleUserCommandUseCase,
        planner: CommandPlanner,
        catalog: ActionCatalog,
        featureFlagRepository: FeatureFlagRepository,
        connectivityChecker: ConnectivityChecker,
    ): RouteCommandUseCase = RouteCommandUseCase(
        handleUserCommand = handleUserCommand,
        planner = planner,
        catalog = catalog,
        featureFlagRepository = featureFlagRepository,
        connectivityChecker = connectivityChecker,
    )
}
