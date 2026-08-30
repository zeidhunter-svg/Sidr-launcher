package com.sidr.launcher.di

import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.SystemIntentToolWorker
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
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
     * One adapter today: `Tier0IntentToolSource`/`Tier0IntentToolWorker` do not exist yet — Task 6
     * creates them and adds the second `ToolAdapter(ToolLevels.SYSTEM_INTENT, …)` line here. The
     * single-adapter list below is that sequencing, not an oversight.
     */
    @Provides
    @Singleton
    fun provideToolFederation(
        inAppRegistry: SystemIntentToolSource,
        inAppWorker: SystemIntentToolWorker,
    ): ToolFederation = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, inAppRegistry, inAppWorker),
        ),
    )

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

    /** A0 binds the deterministic planner; A4' binds a model planner behind this same seam. */
    @Provides
    @Singleton
    fun providePlanner(): Planner = TemplatePlanner()

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
