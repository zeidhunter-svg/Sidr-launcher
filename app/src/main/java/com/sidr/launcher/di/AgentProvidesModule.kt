package com.sidr.launcher.di

import com.sidr.launcher.data.repository.agent.ContextIntentLauncher
import com.sidr.launcher.data.repository.agent.IntentLauncher
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.SystemIntentToolWorker
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolWorker
import com.sidr.launcher.data.repository.agent.ToolMatchPlanner
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.CompositePlanner
import com.sidr.launcher.domain.agent.Planner
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Singleton

/**
 * Wires the A0 agentic spike into the graph (Task 11): the two ports the engine talks to, the
 * deterministic planner, the id factory, the engine itself and the four use cases.
 *
 * Everything is a lazy `@Singleton`, so nothing here is on the launcher cold path and the offline
 * launcher core is unaffected. There is no `data -> data` edge: both implementations depend only on
 * `domain` ports.
 *
 * Two notes on what is deliberately absent:
 *  - [AgentSessionStore] is **not** bound here. Task 10 already bound it in [AgentBindsModule], and
 *    Hilt would reject a second binding of the same key. It is injected below instead.
 *  - [RuntimeBudget] is passed as [RuntimeBudget.Default] rather than bound as its own type: the
 *    budget is a value of the engine's construction, not a port anything else in the graph asks for.
 */
@Module
@InstallIn(SingletonComponent::class)
object AgentProvidesModule {

    /**
     * The federation, and the **composition root's ordering decision**: built-ins first. Collision
     * precedence is first-adapter-wins (`ToolFederation`), so this order is what prevents a later
     * source from shadowing a projected family. `DoctrineGuardTest` asserts this list is the declared
     * set and that no adapter in it names a network type.
     *
     * Two adapters: the `IN_APP` projection of `ActionCatalog`, and Task 6's `Tier0IntentToolSource` /
     * `Tier0IntentToolWorker` — two Android system intents that are not among the frozen seven
     * `ActionIds`, so they mint their own ids and never travel `ExecuteActionUseCase`.
     */
    @Provides
    @Singleton
    fun provideToolFederation(
        inAppRegistry: SystemIntentToolSource,
        inAppWorker: SystemIntentToolWorker,
        tier0Registry: Tier0IntentToolSource,
        tier0Worker: Tier0IntentToolWorker,
    ): ToolFederation = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, inAppRegistry, inAppWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, tier0Registry, tier0Worker),
        ),
    )

    /** `Tier0IntentToolWorker`'s one seam to the world (Task 6). */
    @Provides
    @Singleton
    fun provideIntentLauncher(impl: ContextIntentLauncher): IntentLauncher = impl

    /**
     * Both ports come from the **same** federation object, which is why the registry cannot advertise
     * a tool the dispatcher fails to route. Deriving them separately from a shared list would restore
     * exactly the two-sources-one-decision shape of A0 finding F2.
     */
    @Provides
    @Singleton
    fun provideToolRegistry(federation: ToolFederation): ToolRegistry = federation.registry

    @Provides
    @Singleton
    fun provideToolExecutor(federation: ToolFederation): ToolExecutor = federation.executor

    /**
     * A0 bound the deterministic template planner alone. A1' composes it with the tool matcher so a
     * registered tool is reachable **without a goal shape of its own** — the alternative was one
     * `GoalShape` value and one planner arm per tool, which is linear per tool and contradicts the
     * federation's whole claim. A4' adds the model planner to this same list.
     *
     * The order is a tie-break that never fires: the two planners own disjoint goal shapes
     * (`AppNotInstalled` and `Free`), which `CompositePlanner`'s KDoc states and their own tests hold.
     *
     * [ToolMatchPlanner] is **injected** rather than built here, unlike [TemplatePlanner]: it declares
     * an `@Inject` constructor, so constructing it by hand would make that annotation decorative and
     * would hard-code its dependency list into this module. `TemplatePlanner` is built by hand because
     * it lives in `:domain`, which is `commonMain` and carries no `javax.inject`. Same shape as
     * [provideToolFederation], which injects its `@Inject`-constructor sources and composes them.
     */
    @Provides
    @Singleton
    fun providePlanner(toolMatchPlanner: ToolMatchPlanner): Planner =
        CompositePlanner(listOf(TemplatePlanner(), toolMatchPlanner))

    /** The domain must not know about UUIDs — hence a port, and hence its one implementation here. */
    @Provides
    @Singleton
    fun provideAgentSessionIdFactory(): AgentSessionIdFactory = object : AgentSessionIdFactory {
        override fun newId(): AgentSessionId = AgentSessionId(UUID.randomUUID().toString())
    }

    @Provides
    @Singleton
    fun provideAgentExecutor(
        registry: ToolRegistry,
        toolExecutor: ToolExecutor,
    ): AgentExecutor = AgentExecutor(
        registry = registry,
        toolExecutor = toolExecutor,
        budget = RuntimeBudget.Default,
    )

    @Provides
    @Singleton
    fun provideStartAgentSessionUseCase(
        planner: Planner,
        store: AgentSessionStore,
        ids: AgentSessionIdFactory,
        registry: ToolRegistry,
    ): StartAgentSessionUseCase = StartAgentSessionUseCase(
        planner = planner,
        store = store,
        ids = ids,
        registry = registry,
    )

    @Provides
    @Singleton
    fun provideRunAgentSessionUseCase(
        executor: AgentExecutor,
        store: AgentSessionStore,
    ): RunAgentSessionUseCase = RunAgentSessionUseCase(executor = executor, store = store)

    @Provides
    @Singleton
    fun provideResolveConsentUseCase(
        store: AgentSessionStore,
        run: RunAgentSessionUseCase,
    ): ResolveConsentUseCase = ResolveConsentUseCase(store = store, run = run)

    @Provides
    @Singleton
    fun provideCancelAgentSessionUseCase(
        store: AgentSessionStore,
    ): CancelAgentSessionUseCase = CancelAgentSessionUseCase(store)
}
