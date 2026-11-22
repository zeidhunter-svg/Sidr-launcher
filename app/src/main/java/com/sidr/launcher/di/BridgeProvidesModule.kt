package com.sidr.launcher.di

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.aicloud.BridgeTransportMode
import com.sidr.launcher.data.aicloud.OpenAiCompatibleBridgeTransport
import com.sidr.launcher.data.aicloud.bridgeHttpClient
import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.data.repository.agent.DeviceMentionBinder
import com.sidr.launcher.data.repository.text.IcuTextFolding
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.bridge.BridgeBudget
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeModelPlanner
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ToolDescriptions
import com.sidr.launcher.domain.ai.router.ModelAskedSignal
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.plan.DefaultLearnedPlans
import com.sidr.launcher.domain.memory.plan.LearnedPlanStore
import com.sidr.launcher.domain.memory.plan.LearnedPlans
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.security.SecureSecretStore
import com.sidr.launcher.domain.text.TextFolding
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.feature.launcher.ModelAskedEvents
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

/**
 * The bridge (A4′ phase 3a, D1–D3) — replaces `RouterProvidesModule`. One model path: the
 * [ModelPlanner] over the federation's offered tools and our descriptions, its own transport over its
 * own HTTP client (socket bound = the longest per-host ceiling, `BridgeBudget.longestCeilingMs`), the device-side [MentionBinder], and [RouteCommandUseCase] asking the
 * planner at step 7 only — and, since A4′ phase 4, replaying a command the user taught at step 2c over the same
 * gates ([LearnedPlans]). Lazy singletons; nothing here is on the launcher's cold path.
 *
 * The model planner is NOT in `AgentProvidesModule`'s `CompositePlanner` list — that list is what step
 * 2b and resume ask, and `DoctrineGuardTest > the composition root's planner list matches the declared
 * planners` keeps it at the two deterministic planners (ADR 2).
 */
@Module
@InstallIn(SingletonComponent::class)
object BridgeProvidesModule {

    /**
     * The transport form (D2, b′). The prompt form, chosen by the 2026-11-07 measurement: 222 of 224 usable
     * answers against 159 and 138, and the only form no model rejected (ADR addendum «по замеру»).
     */
    val DEFAULT_TRANSPORT_MODE: BridgeTransportMode = BridgeTransportMode.PROMPT_ONLY

    @Provides
    @Singleton
    fun provideBridgeTransport(
        secretStore: SecureSecretStore,
        configRepository: AiProviderConfigRepository,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): BridgeTransport = OpenAiCompatibleBridgeTransport(
        // Not the shared cloud client: its 30 s socket would end a silent reasoning model's wait before
        // the ceiling does. Each attempt's socket is bounded by the longest ceiling (the host's own is the
        // planner's, `BridgeBudget.ceilingFor`); the planner bounds the whole.
        bridgeHttpClient(BridgeBudget.Default.longestCeilingMs),
        secretStore,
        configRepository,
        ioDispatcher,
        DEFAULT_TRANSPORT_MODE,
    )

    @Provides
    @Singleton
    fun provideToolDescriptions(impl: AuthoredToolDescriptions): ToolDescriptions = impl

    /**
     * The device's one text folding (phase 3b, B-1): ICU's word segments over the shared character
     * folding. The bridge's derivability compares by it; it is bound here, once, so that whatever else on
     * the device asks "is this the same word?" injects the same answer — since Task 7 that includes
     * FastPath's resolver and learned-choice key, besides step 2b's target and the bridge's binder.
     * Pinned by `TextFoldingBindingGuardTest`.
     */
    @Provides
    @Singleton
    fun provideTextFolding(): TextFolding = IcuTextFolding

    @Provides
    @Singleton
    fun provideModelPlanner(
        registry: ToolRegistry,
        descriptions: ToolDescriptions,
        transport: BridgeTransport,
        folding: TextFolding,
    ): ModelPlanner =
        BridgeModelPlanner(registry, descriptions, transport, BridgeFilter.ANDROID_OFFERED_LEVELS, BridgeBudget.Default, folding)

    @Provides
    @Singleton
    fun provideMentionBinder(impl: DeviceMentionBinder): MentionBinder = impl

    /**
     * Step 2c's learned-plan cache (A4′ phase 4, spec §8; plan §0.2 B-4): **the same gates as step 7, by
     * construction** — the [registry], [descriptions] and [folding] [provideModelPlanner] receives, Android's
     * offered levels ([BridgeFilter.ANDROID_OFFERED_LEVELS], the planner's own), and the route's [binder]. So a
     * replay is judged against the offering step 7 would send, by the validator step 7's planner runs, and bound
     * by the binder step 7's reading is bound by. No second folding: the one [provideTextFolding] binds.
     *
     * The [store] is the on-device table (`MemoryBindsModule`); nothing it holds goes into a request
     * (`BridgePayloadGuardTest`, its fifth private source, `DOC-AML-1`).
     */
    @Provides
    @Singleton
    fun provideLearnedPlans(
        store: LearnedPlanStore,
        registry: ToolRegistry,
        descriptions: ToolDescriptions,
        binder: MentionBinder,
        folding: TextFolding,
    ): LearnedPlans = DefaultLearnedPlans(store, registry, descriptions, BridgeFilter.ANDROID_OFFERED_LEVELS, binder, folding)

    /**
     * «The model is being asked» (A4′ acceptance tail, task 6; controller ruling K9): the domain port the route raises
     * right before step 7 asks the model, bound to the launcher's own [ModelAskedEvents] — the `@Singleton` the
     * launcher's ViewModel injects and puts its wait up on. One instance, so what the route raises is what the launcher
     * sees (`ModelAskedSignalBindingGuardTest`). Unscoped: it hands out the one singleton every time.
     */
    @Provides
    fun provideModelAskedSignal(events: ModelAskedEvents): ModelAskedSignal = events

    /**
     * [learnedPlans] has no default here (B-12): the graph cannot build a route without step 2c's cache.
     *
     * Step 1a's [aliases] and [installedApps] (A4′ acceptance tail, task 3) — the one [AliasStore] binding
     * (`MemoryBindsModule`) and the one app list — DO carry defaults, for the hand-built call in
     * `BridgePayloadGuardTest` only: Hilt passes every parameter and ignores a Kotlin default, so the graph always
     * builds the route with the device's store and list. So does [modelAsked] (task 6, K9): the graph passes the
     * launcher's events ([provideModelAskedSignal]); `ModelAskedSignalBindingGuardTest` holds that this body hands them on.
     */
    @Provides
    @Singleton
    fun provideRouteCommandUseCase(
        handleUserCommand: HandleUserCommandUseCase,
        modelPlanner: ModelPlanner,
        binder: MentionBinder,
        featureFlagRepository: FeatureFlagRepository,
        providerConfigRepository: AiProviderConfigRepository,
        connectivityChecker: ConnectivityChecker,
        startAgentSession: StartAgentSessionUseCase,
        learnedPlans: LearnedPlans,
        aliases: AliasStore = AliasStore.NONE,
        installedApps: InstalledAppsRepository? = null,
        modelAsked: ModelAskedSignal = ModelAskedSignal.NONE,
    ): RouteCommandUseCase = RouteCommandUseCase(
        handleUserCommand = handleUserCommand,
        modelPlanner = modelPlanner,
        binder = binder,
        featureFlagRepository = featureFlagRepository,
        providerConfigRepository = providerConfigRepository,
        connectivityChecker = connectivityChecker,
        startAgentSession = startAgentSession,
        learnedPlans = learnedPlans,
        aliases = aliases,
        installedApps = installedApps,
        modelAsked = modelAsked,
    )
}
