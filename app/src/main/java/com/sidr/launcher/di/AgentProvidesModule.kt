package com.sidr.launcher.di

import android.content.Context
import com.sidr.launcher.data.repository.agent.APP_PACKAGE_NAME
import com.sidr.launcher.data.repository.agent.ContextIntentLauncher
import com.sidr.launcher.data.repository.agent.ContextPermissionPresence
import com.sidr.launcher.data.repository.agent.DynamicToolNames
import com.sidr.launcher.data.repository.agent.IntentLauncher
import com.sidr.launcher.data.repository.agent.PermissionPresence
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.SystemIntentToolWorker
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolWorker
import com.sidr.launcher.data.repository.agent.ToolMatchPlanner
import com.sidr.launcher.data.repository.agent.shortcut.AndroidShortcutChangeObserver
import com.sidr.launcher.data.repository.agent.shortcut.AndroidShortcutLauncher
import com.sidr.launcher.data.repository.agent.shortcut.AndroidShortcutQuery
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutChangeObserver
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutLauncher
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutQuery
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolWorker
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
import com.sidr.launcher.feature.launcher.agent.DynamicToolLabel
import com.sidr.launcher.feature.launcher.agent.DynamicToolLabels
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Named
import javax.inject.Singleton

/**
 * Wires the A0 agentic spike into the graph (Task 11): the two ports the engine talks to, the
 * deterministic planner, the id factory, the engine itself and the four use cases.
 *
 * **Almost everything here is a lazy `@Singleton` — and since A1″ one branch of the graph is not, so
 * the old flat claim is replaced by a precise one.** `SidrLauncherApp` field-injects
 * [com.sidr.launcher.data.repository.agent.shortcut.ShortcutRefreshTrigger], which makes
 * `Application.onCreate` force the construction of that trigger, of its
 * [com.sidr.launcher.data.repository.agent.shortcut.ShortcutChangeObserver] and of
 * [com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog] — and through the catalog, of
 * [AndroidShortcutQuery]. That is **construction and nothing else**: each of those constructors only
 * stores its arguments, and no `LauncherApps` call, binder hop or I/O happens until `start(scope)`
 * launches onto the application scope's IO dispatcher. Every other binding here stays lazy and off the
 * cold path, and the offline launcher core is unaffected either way. There is no `data -> data` edge:
 * every implementation wired here depends only on `domain` ports.
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
     * Three adapters:
     *  - the `IN_APP` projection of `ActionCatalog`;
     *  - A1′'s `Tier0IntentToolSource` / `Tier0IntentToolWorker` — **four** Android system intents
     *    (`set_timer`, `open_system_settings`, and A1″ Phase 3a's `set_alarm` and `uninstall_app`) that
     *    are not among the frozen seven `ActionIds`, so they mint their own ids and never travel
     *    `ExecuteActionUseCase`. `uninstall_app` is the track's first `CONFIRM`/`DURABLE` tool, so this
     *    adapter is no longer uniformly `SAFE` — a reader sizing up the federation's risk profile from
     *    this list must not infer otherwise;
     *  - A1″'s `ShortcutToolSource` / `ShortcutToolWorker` — one tool per app shortcut another
     *    installed app publishes.
     *
     * **The shortcut adapter is last, and that placement is the whole of its collision safety.** It is
     * the first source whose contents are written by third parties, and first-adapter-wins means a
     * third-party tool can never displace an authored one. (Its ids are additionally prefixed
     * `shortcut:`, which no authored id contains — two independent reasons, neither relying on the
     * other.)
     *
     * It is also the first adapter whose tool set **moves while the process lives**: it is empty until
     * `ShortcutRefreshTrigger` has run, empty forever on a device where the user has chosen another
     * home app, and otherwise as large as the installed apps make it (205 tools from 65 packages on the
     * measured SM-A325F). Nothing here caches it — `ToolFederation` re-derives per call for exactly
     * this reason.
     */
    @Provides
    @Singleton
    fun provideToolFederation(
        inAppRegistry: SystemIntentToolSource,
        inAppWorker: SystemIntentToolWorker,
        tier0Registry: Tier0IntentToolSource,
        tier0Worker: Tier0IntentToolWorker,
        shortcutRegistry: ShortcutToolSource,
        shortcutWorker: ShortcutToolWorker,
    ): ToolFederation = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, inAppRegistry, inAppWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, tier0Registry, tier0Worker),
            ToolAdapter(ToolLevels.APP_SHORTCUT, shortcutRegistry, shortcutWorker),
        ),
    )

    /** `Tier0IntentToolWorker`'s one seam to the world (Task 6). */
    @Provides
    @Singleton
    fun provideIntentLauncher(impl: ContextIntentLauncher): IntentLauncher = impl

    /** The Task 1 `PermissionPresence` port's one production implementation. */
    @Provides
    @Singleton
    fun providePermissionPresence(impl: ContextPermissionPresence): PermissionPresence = impl

    /**
     * Our own package name, for `uninstall_app`'s refusal to remove the launcher it is running inside
     * (Task 7, owner condition 4 of 2026-09-18).
     *
     * **It is injected as a `String` rather than read from a `Context` inside the worker, and that is a
     * testability decision with a safety consequence.** `Tier0IntentToolWorker` is a plain class in
     * `:data:repository` with no Android context of its own; giving it one to ask `packageName` would
     * put the refusal behind Robolectric, where the rest of that worker's fail-closed paths
     * deliberately are not. As a constructor argument the refusal is an ordinary equality a unit test
     * can state — which is what `Tier0IntentToolWorkerTest > uninstalling our own package is refused
     * and nothing is dispatched` does.
     *
     * `context.packageName` is the **running** package, so a debug build's `.debug` suffix (or any
     * future flavour suffix) is carried automatically; a constant spelled here would be wrong for
     * exactly the build the owner runs.
     */
    @Provides
    @Singleton
    @Named(APP_PACKAGE_NAME)
    fun provideAppPackageName(@ApplicationContext context: Context): String = context.packageName

    /**
     * The `app_shortcut` adapter's **three** Android seams, kept behind ports so everything built on
     * them is testable without a device: read the shortcut set, be told it changed, start one. They are
     * also the three — and only three — production callers of `LauncherApps`, enumerated in
     * [AndroidShortcutQuery]'s KDoc.
     */
    @Provides
    @Singleton
    fun provideShortcutQuery(impl: AndroidShortcutQuery): ShortcutQuery = impl

    @Provides
    @Singleton
    fun provideShortcutChangeObserver(impl: AndroidShortcutChangeObserver): ShortcutChangeObserver = impl

    @Provides
    @Singleton
    fun provideShortcutLauncher(impl: AndroidShortcutLauncher): ShortcutLauncher = impl

    /**
     * The names face of the same object that supplies the descriptors — **the same instance**, not a
     * second construction. `ShortcutToolSource` is a `@Singleton`, so `registry.all()` and `names()`
     * read one snapshot; providing them from two instances would let a rendered name disagree with the
     * registered tool it labels, which is the F2 shape in miniature.
     *
     * Held by `DynamicToolLabelWiringTest` (`:app`) rather than asserted in prose — fix round 1,
     * finding 10: this paragraph was a claim nothing tested, over a projection no test exercised.
     */
    @Provides
    @Singleton
    fun provideDynamicToolNames(source: ShortcutToolSource): DynamicToolNames = source

    /**
     * The projection **the composition root exists to make**: `:data:repository`'s [DynamicToolNames]
     * onto `:feature:launcher`'s [DynamicToolLabels].
     *
     * `:feature:launcher` depends on `:domain`, `:core:ui` and `:core:common` — there is no
     * `feature -> data` edge — so the ViewModel cannot hold the data-layer port, and the data-layer type
     * cannot move into `:domain` either: a shortcut's name is a **third-party string**, and `:domain`
     * carries no user-facing copy. `:app` is the only module that sees both sides, so the two-line
     * mapping lives here. Same shape as `StepProvenance`, which the ViewModel fills from a `:domain`
     * port it *can* hold.
     *
     * The lambda re-reads on every call rather than capturing a map: adapter #3's tool set moves while
     * the process lives (`ToolFederation` re-derives per call for the same reason), and a map captured
     * at graph construction would be the empty one that exists before `ShortcutRefreshTrigger` has run.
     * `DynamicToolLabelWiringTest` holds that, and the two-halves shape this projection preserves.
     *
     * **What re-reading costs, and where that cost is paid.** Each call walks the source's snapshot, so
     * the surface must not call it per recomposition: `LauncherScreen` wraps both this map and
     * `agentToolProvenance` in `remember(session)`, which is where the frequency is bounded (fix round
     * 1, finding 5). Bounding it here instead — by caching — would restore precisely the staleness this
     * lambda exists to avoid.
     */
    @Provides
    @Singleton
    fun provideDynamicToolLabels(names: DynamicToolNames): DynamicToolLabels = DynamicToolLabels {
        names.names().associate { it.id to DynamicToolLabel(it.qualifier, it.name) }
    }

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
