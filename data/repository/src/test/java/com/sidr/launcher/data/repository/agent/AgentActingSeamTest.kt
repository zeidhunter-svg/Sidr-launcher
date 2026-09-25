package com.sidr.launcher.data.repository.agent

import android.app.Application
import android.content.Intent
import android.provider.AlarmClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeCommandPlanner
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.core.testing.NoOpIntentMatcher
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.data.repository.agent.memory.MemoryToolWorker
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutLauncher
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolWorker
import com.sidr.launcher.data.repository.db.SidrDatabase
import com.sidr.launcher.data.repository.db.dao.AgentSessionDao
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CompositePlanner
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningRequest
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.memory.resolution.DeleteLearnedChoiceUseCase
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **The acting seam, end to end** (A1″ Phase 3a, Task 12) — the one test in this block that crosses
 * *vocabulary → selector → planner → federation → executor → worker → store* in a single run, over
 * production objects, with only the four things a JVM test cannot have faked.
 *
 * It exists because this block's most expensive lesson, three times over, is that four layers each
 * fully tested in isolation summed to a dead capability. [Tier0ToolExecutionEndToEndTest] already
 * crosses the seam for a `SAFE` tool; what had never been crossed once is the seam **with the consent
 * gate in it**, which is the whole of what Phase 3a added: `uninstall_app` is the track's first
 * `CONFIRM` tool, so until this file no test drove a real command into a real stop.
 *
 * **It goes through [AgentExecutor], never straight to `federation.executor`** (plan review finding
 * I7). Calling the federation's executor directly skips `prepare`/`checkpointFor` — and that is
 * exactly where both of this phase's Critical review findings lived: a `DURABLE` memory tool silently
 * raising a consent card, and a consent card built *before* the package was resolved. A seam test that
 * cannot see the consent decision is not testing this block's seam. So every assertion below is on
 * **`AgentSession.state` plus the persisted trace** — [AgentSession] has no `consentCheckpoint`
 * property, and `ConsentCheckpoint` is an internal return value of `AgentExecutor.checkpointFor` that
 * reaches the outside world only as [TraceEvent.ConsentRequested] and a state change.
 *
 * **Fixture values are chosen so that no expected value is a substring of another, or of the goal
 * text.** That is not fussiness: the two preceding tasks each shipped an assertion that passed for the
 * wrong reason and was caught only by mutation, and in one of them the real package
 * `org.telegram.messenger` *contains* the label `telegram`, so binding the label where the package
 * belonged still matched. Here [TARGET_LABEL] `zephyr` resolves to [TARGET_PACKAGE]
 * `com.quill.notepad`; the package shares no substring with the label, with the goal text, or with
 * [OWN_PACKAGE]. A label/package mix-up therefore cannot pass, and neither can a goal-text fallback.
 *
 * **What is real and what is faked.** Real: [ToolVocabulary], [ToolSelector], [AppTargetResolver]
 * (over faked *inputs*, so the production resolution path runs), [ToolMatchPlanner], [CompositePlanner],
 * all four production [ToolAdapter]s in `AgentProvidesModule.provideToolFederation`'s own order,
 * [ToolFederation], [AgentExecutor], [StartAgentSessionUseCase] / [RunAgentSessionUseCase] /
 * [ResolveConsentUseCase], [RouteCommandUseCase], and [RoomAgentSessionStore] over a real Room
 * database — so every session below is written to disk and read back before it is asserted on. Faked:
 * FastPath's matcher, the model planner (the one outbound seam), the provider config, connectivity,
 * the `IntentLauncher` seam, and the two memory stores.
 *
 * **One narrowed fidelity, stated rather than implied:** the [AppTargetResolver]'s alias store and the
 * memory worker's alias store are two different objects, where production injects one. Nothing here
 * asserts across them — the resolutions below all go through the label path — so sharing them would
 * change no result; it is named so the next reader does not mistake the split for a claim.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AgentActingSeamTest {

    /**
     * Grant-everything, because the subject of most tests here is the seam and not the permission
     * filter; the one test whose subject *is* the filter builds its own denying presence. Same
     * reasoning, and same wording, as [Tier0ToolExecutionEndToEndTest]'s.
     */
    private val grantsEverything = PermissionPresence { true }

    private lateinit var db: SidrDatabase
    private lateinit var dao: AgentSessionDao
    private lateinit var store: RoomAgentSessionStore

    /** The one dependency of [RouteCommandUseCase] that can leave the device. */
    private val modelPlanner = FakeCommandPlanner()

    /** Every intent the Tier-0 worker issues, in order. Empty is an assertion, not an absence. */
    private val launched = mutableListOf<Intent>()

    private val aliasStore = FakeAliasStore()
    private val preferenceStore = FakeResolutionPreferenceStore()

    /**
     * Production's `app_shortcut` adapter, over a catalog that was never refreshed and is therefore
     * empty. It is wired rather than replaced by `namesOf()` for two reasons: it is the object
     * [ToolSelector] takes as its `DynamicToolNames` in the graph, and its presence means a third-party
     * label cannot silently shadow one of the authored triggers below without this file seeing it.
     */
    private val shortcutSource = ShortcutToolSource(
        ShortcutCatalog(query = { emptyList() }, ioDispatcher = UnconfinedTestDispatcher()),
    )

    private val appTargets = appTargetsOf(TARGET_LABEL to TARGET_PACKAGE, OWN_LABEL to OWN_PACKAGE)

    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.agentSessionDao()
        store = RoomAgentSessionStore(dao, Dispatchers.Unconfined) { 100L }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * `AgentProvidesModule.provideToolFederation`'s four adapters, in its order. The `in_app` and
     * `app_shortcut` workers refuse: nothing planned here belongs to them, so the federation routing to
     * one is a defect this file must see rather than pass over.
     *
     * [ownPackage] is a parameter and [OWN_PACKAGE] is deliberately *not* `com.sidr.launcher`: owner
     * condition 4 must key on the injected name rather than on a literal the worker recognises, and a
     * fixture that used the real value could not tell the two apart.
     */
    private fun federation(
        presence: PermissionPresence = grantsEverything,
        ownPackage: String = OWN_PACKAGE,
    ): ToolFederation = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), SeamRefusingWorker),
            ToolAdapter(
                ToolLevels.SYSTEM_INTENT,
                Tier0IntentToolSource(ToolPermissionCatalog(), presence),
                Tier0IntentToolWorker(
                    launcher = SeamIntentRecorder(launched),
                    catalog = ToolPermissionCatalog(),
                    presence = presence,
                    ownPackageName = ownPackage,
                ),
            ),
            ToolAdapter(
                ToolLevels.APP_SHORTCUT,
                shortcutSource,
                ShortcutToolWorker(ShortcutLauncher { pkg, id -> error("no shortcut may run here: $pkg/$id") }),
            ),
            ToolAdapter(
                ToolLevels.LAUNCHER_MEMORY,
                MemoryToolSource(),
                MemoryToolWorker(
                    save = SaveAliasUseCase(aliasStore),
                    deleteAlias = DeleteAliasUseCase(aliasStore),
                    deleteChoice = DeleteLearnedChoiceUseCase(preferenceStore),
                ),
            ),
        ),
    )

    /** `AgentProvidesModule.providePlanner`'s own list, with the real selector and resolver in it. */
    private fun planner() = CompositePlanner(
        listOf(TemplatePlanner(), ToolMatchPlanner(ToolSelector(ToolVocabulary(), shortcutSource), appTargets)),
    )

    private fun startUseCase(federation: ToolFederation) =
        StartAgentSessionUseCase(planner(), store, ids, federation.registry)

    private fun runUseCase(federation: ToolFederation) = RunAgentSessionUseCase(
        executor = AgentExecutor(
            registry = federation.registry,
            toolExecutor = federation.executor,
            budget = RuntimeBudget.Default,
        ),
        store = store,
    )

    /**
     * The command reaches step 2b: FastPath is undecided ([NoOpIntentMatcher] answers `UnknownIntent`
     * at confidence 0), so if the tool branch fails open the fall-through below it is live and
     * [modelPlanner] records the call. `localOnly`/`online` are parameters because parity is a claim
     * about exactly those two states.
     */
    private fun routeUseCase(
        federation: ToolFederation,
        localOnly: Boolean = false,
        online: Boolean = true,
    ) = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = NoOpIntentMatcher(),
            resolver = IntentActionResolver(FakeInstalledAppsRepository()),
            executor = FakeActionExecutor(),
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(SupervisorJob()),
        ),
        planner = modelPlanner,
        catalog = FakeActionCatalog(),
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = localOnly)),
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(initiallyOnline = online),
        startAgentSession = startUseCase(federation),
    )

    /**
     * Route → reload the persisted row → turn the crank, which is the hand-off the product performs:
     * `RouteCommandUseCase` step 2b starts and persists, and `LauncherAgentSession.attach` then loads
     * the active row and runs it. Reloading through the store rather than passing an in-memory session
     * is what makes every assertion below one about state that survived Room.
     */
    private suspend fun routeAndRun(
        command: String,
        federation: ToolFederation,
        localOnly: Boolean = false,
        online: Boolean = true,
    ): Pair<CommandOutcome, AgentSession?> {
        val outcome = routeUseCase(federation, localOnly, online).route(command)
        val active = (store.active() as OperationResult.Success).value ?: return outcome to null
        return outcome to (runUseCase(federation).run(active) as? OperationResult.Success)?.value
    }

    private suspend fun resolveConsent(
        federation: ToolFederation,
        id: AgentSessionId,
        granted: Boolean,
    ): AgentSession? =
        (ResolveConsentUseCase(store, runUseCase(federation)).resolve(id, 0, granted) as OperationResult.Success).value

    private fun goal(text: String) = AgentGoal(text = text, shape = GoalShape.Free(text))

    private fun startedId(outcome: CommandOutcome): AgentSessionId {
        assertTrue("expected an agent session, was $outcome", outcome is CommandOutcome.AgentSessionStarted)
        return (outcome as CommandOutcome.AgentSessionStarted).id
    }

    // ---------------------------------------------------------------------------------------------
    // The gated crossing: a typed command reaches a CONFIRM tool and stops.
    // ---------------------------------------------------------------------------------------------

    /**
     * **The headline of the phase.** A typed Russian command crosses every layer and the loop stops at
     * [ExecutionState.AwaitingConsent] with the *resolved package* already bound — which is owner
     * condition 2 seen from the engine's side: what the consent card can name is exactly what is in the
     * pending step, and the step carries both the package that will be removed and the word the user
     * typed. (Task 11 holds the rendering of those two values; this holds that both are there to
     * render, after a round trip through Room.)
     *
     * `app` is asserted against [TARGET_PACKAGE], which appears nowhere in [GOAL_UNINSTALL] and shares
     * no substring with [TARGET_LABEL] — so neither a goal-text fallback nor a label/package mix-up can
     * satisfy it.
     */
    @Test
    fun `a typed uninstall command stops at AwaitingConsent with the resolved package already bound`() = runTest {
        val federation = federation()

        val (outcome, session) = routeAndRun(GOAL_UNINSTALL, federation)

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
        assertNotNull("the run must return a session", session)
        checkNotNull(session)
        assertEquals(ExecutionState.AwaitingConsent, session.state)
        assertEquals(
            "the stop must be recorded as a risk-level checkpoint on step 0",
            listOf(TraceEvent.ConsentRequested(0, ConsentReason.RISK_LEVEL)),
            session.trace.events.filterIsInstance<TraceEvent.ConsentRequested>(),
        )

        val pending = session.plan.steps.single()
        assertEquals(Tier0ToolIds.UNINSTALL_APP, pending.invocation.id)
        assertEquals(ArgSource.Literal(TARGET_PACKAGE), pending.invocation.args["app"])
        assertEquals(ArgSource.Literal(TARGET_LABEL), pending.invocation.args["app_label"])

        assertTrue("nothing may reach the world before consent: $launched", launched.isEmpty())
        assertEquals("the model planner must never be reached", 0, modelPlanner.planCallCount)
    }

    /**
     * The other half of the same crossing: consent granted, the step dispatches, and the intent that
     * actually leaves carries the package — not the label. Asserted on `intent.data`, which is what
     * `Uri.fromParts("package", …)` produced, so the whole binding chain is pinned by one value.
     *
     * The trace is asserted as an **ordered** sequence, because the order is the property: a
     * `ToolInvoked` before its `ConsentResolved` would mean the gate was decorative.
     */
    @Test
    fun `consent granted - the uninstall dispatches the resolved package and the trace records the order`() =
        runTest {
            val federation = federation()
            val (outcome, awaiting) = routeAndRun(GOAL_UNINSTALL, federation)
            assertEquals(ExecutionState.AwaitingConsent, checkNotNull(awaiting).state)

            val finished = resolveConsent(federation, startedId(outcome), granted = true)

            assertNotNull("resolving consent must return the finished session", finished)
            checkNotNull(finished)
            assertEquals(ExecutionState.Completed, finished.state)
            assertEquals(ToolResult.Effected(), finished.observations[0])

            val intent = launched.single()
            assertEquals(Intent.ACTION_DELETE, intent.action)
            assertEquals("package:$TARGET_PACKAGE", intent.data.toString())

            assertEquals(
                listOf(
                    TraceEvent.ConsentRequested(0, ConsentReason.RISK_LEVEL),
                    TraceEvent.ConsentResolved(0, granted = true),
                    TraceEvent.ToolInvoked(0, Tier0ToolIds.UNINSTALL_APP),
                    TraceEvent.ToolObserved(0, ToolResult.Effected()),
                ),
                finished.trace.events.filter {
                    it is TraceEvent.ConsentRequested ||
                        it is TraceEvent.ConsentResolved ||
                        it is TraceEvent.ToolInvoked ||
                        it is TraceEvent.ToolObserved
                },
            )
        }

    /** Consent refused ends the session and the world is never touched. */
    @Test
    fun `consent refused - the session is cancelled and no intent is ever issued`() = runTest {
        val federation = federation()
        val (outcome, _) = routeAndRun(GOAL_UNINSTALL, federation)

        val finished = resolveConsent(federation, startedId(outcome), granted = false)

        assertEquals(ExecutionState.Cancelled, checkNotNull(finished).state)
        assertTrue("a refused step must issue nothing: $launched", launched.isEmpty())
        assertEquals(
            listOf(TraceEvent.ConsentResolved(0, granted = false)),
            finished.trace.events.filterIsInstance<TraceEvent.ConsentResolved>(),
        )
    }

    // ---------------------------------------------------------------------------------------------
    // The ungated crossing: a SAFE tool must not stop the loop.
    // ---------------------------------------------------------------------------------------------

    /**
     * A `SAFE`/`TRANSIENT` memory tool crosses the same seam and raises **no** checkpoint at all.
     *
     * Asserted on the session and the trace rather than on `requiresConsent(step.risk)`, which is
     * true-by-definition for `SAFE` and cannot see `checkpointFor`'s three other triggers — the
     * `DURABLE` branch among them, which is exactly what the phase's first Critical finding was
     * (`launcher_memory` marked `DURABLE`, silently raising a card over an alias rename).
     *
     * Non-vacuity: the alias really reached the store, and it points at [TARGET_PACKAGE] — a value the
     * goal text does not contain — so this is the whole chain, not a session that quietly did nothing.
     */
    @Test
    fun `a SAFE memory tool runs to Completed without ever raising a checkpoint`() = runTest {
        val federation = federation()

        val (outcome, session) = routeAndRun(GOAL_ALIAS, federation)

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
        checkNotNull(session)
        assertEquals(ExecutionState.Completed, session.state)
        assertTrue(
            "a memory write must not stop the loop: ${session.trace.events}",
            session.trace.events.none { it is TraceEvent.ConsentRequested },
        )
        assertEquals(MemoryToolIds.SET_APP_ALIAS, session.plan.steps.single().invocation.id)
        assertEquals(ToolResult.Effected(), session.observations[0])

        val stored = aliasStore.observeAll().first().single()
        assertEquals(ALIAS_PHRASE, stored.phrase)
        assertEquals(AliasTarget.App(TARGET_PACKAGE), stored.target)
    }

    /**
     * The third acting family, and the one that proves the `app`-resolution branch is keyed on the
     * argument name rather than applied to every tool: `set_alarm` has no `app` argument, so its
     * literal survives untouched all the way into the clock intent.
     */
    @Test
    fun `a SAFE alarm command crosses the same seam and issues its intent unresolved`() = runTest {
        val federation = federation()

        val (_, session) = routeAndRun(GOAL_ALARM, federation)

        assertEquals(ExecutionState.Completed, checkNotNull(session).state)
        assertTrue(
            "set_alarm is SAFE and must not stop the loop",
            session.trace.events.none { it is TraceEvent.ConsentRequested },
        )
        val intent = launched.single()
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(7, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
    }

    // ---------------------------------------------------------------------------------------------
    // Owner condition 1 — uninstall_app runs only from an explicit command.
    // ---------------------------------------------------------------------------------------------

    /**
     * **Owner condition 1, held mechanically** (spec §7.6): `uninstall_app` runs only from an explicit
     * user command — never from a suggestion, never as an unasked second step. There is no suggestion
     * path into this planner at all, and the part that can be asserted is that a recognised tool yields
     * exactly **one** step, so nothing can be appended beside what the user asked for. The day
     * something re-plans or chains, this is what goes red.
     */
    @Test
    fun `the planner never builds more than one step for a recognised tool`() = runTest {
        val registry = federation().registry

        listOf(GOAL_UNINSTALL, GOAL_ALARM, GOAL_ALIAS).forEach { text ->
            val planned = planner().plan(PlanningRequest(goal(text)), registry)
            assertTrue("\"$text\" must plan, was $planned", planned is PlanningResult.Planned)
            assertEquals("\"$text\"", 1, (planned as PlanningResult.Planned).plan.steps.size)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Owner condition 4 — our own package is refused (and R14-38: where the refusal sits).
    // ---------------------------------------------------------------------------------------------

    /**
     * **Owner condition 4, and the ordering it currently has — documented, not repaired (R14-38).**
     *
     * Our own package is refused: the worker returns [ToolResult.Failed] and no intent is ever issued.
     * But the refusal lives in the **worker**, below the consent checkpoint, and [AppTargetResolver]
     * has no own-package exclusion — so a command naming this launcher resolves normally, draws a
     * `CONFIRM` card naming our own package, and is only refused after the user has already tapped
     * through. **The user consents to something that cannot happen.**
     *
     * That placement is what spec §7.6/§7.7 and `Tier0IntentToolWorker.uninstallApp`'s KDoc describe,
     * so this test states the current ordering rather than asserting a better one. Moving the refusal
     * above the gate would change what the user sees and is an **owner decision**, not an
     * implementer's — it is recorded as R14-38 and addressed out of this task.
     */
    @Test
    fun `our own package is refused by the worker - after a consent card has already named it`() = runTest {
        val federation = federation()

        val (outcome, awaiting) = routeAndRun(GOAL_UNINSTALL_OWN, federation)

        // The card is drawn first, and it names our own package: the ordering, stated.
        assertEquals(ExecutionState.AwaitingConsent, checkNotNull(awaiting).state)
        assertEquals(ArgSource.Literal(OWN_PACKAGE), awaiting.plan.steps.single().invocation.args["app"])

        val finished = resolveConsent(federation, startedId(outcome), granted = true)

        checkNotNull(finished)
        assertEquals(ToolResult.Failed(CommandFailure.Generic), finished.observations[0])
        assertTrue("our own package must never be handed to the uninstaller: $launched", launched.isEmpty())
        assertTrue("nothing may be left running", finished.state.isTerminal)
    }

    // ---------------------------------------------------------------------------------------------
    // Parity, and the fall-through that is not parity (R14-39).
    // ---------------------------------------------------------------------------------------------

    /**
     * **Parity** (plan review finding I8): the five acting tools are reachable through routing step 2b,
     * which sits *above* the `localOnlyMode` check — so they run with the flag on and the network down.
     * Parity means exactly two things, and this asserts both: the model planner is **not consulted**
     * and **nothing leaves the device**.
     *
     * Each goal is routed and its row deleted, because `RoomAgentSessionStore.active()` answers with one
     * session and this test is about the routing decision rather than the run.
     */
    @Test
    fun `parity - every acting goal routes to the agent in local-only mode and offline, model untouched`() =
        runTest {
            listOf(GOAL_UNINSTALL, GOAL_ALARM, GOAL_ALIAS).forEach { text ->
                val outcome = routeUseCase(federation(), localOnly = true, online = false).route(text)
                assertTrue("\"$text\" must start an agent session, was $outcome", outcome is CommandOutcome.AgentSessionStarted)
                store.delete((outcome as CommandOutcome.AgentSessionStarted).id)
            }

            assertEquals("the model planner was consulted", 0, modelPlanner.planCallCount)
            assertNull("an outbound payload was recorded: ${modelPlanner.lastCommand}", modelPlanner.lastCommand)
        }

    /**
     * The same claim for a whole **run**, not only for the routing decision: with local-only on and the
     * network down, a recognised tool executes to `Completed` and the outbound seam is still untouched.
     */
    @Test
    fun `parity - a recognised tool runs to completion in local-only mode with nothing leaving`() = runTest {
        val (_, session) = routeAndRun(GOAL_ALIAS, federation(), localOnly = true, online = false)

        assertEquals(ExecutionState.Completed, checkNotNull(session).state)
        assertEquals(ToolResult.Effected(), session.observations[0])
        assertEquals(0, modelPlanner.planCallCount)
        assertNull(modelPlanner.lastCommand)
    }

    /**
     * **Non-vacuity for every `planCallCount == 0` above.** The same [FakeCommandPlanner] instance,
     * built by the same [routeUseCase] wiring, *does* record a command on the path where consulting a
     * model is correct. Without this, a zero call count would be a fact about a disconnected fake.
     */
    @Test
    fun `the outbound seam is live when nothing in the vocabulary claims the text`() = runTest {
        val outcome = routeUseCase(federation()).route(GOAL_UNMATCHED)

        assertEquals(1, modelPlanner.planCallCount)
        assertEquals(GOAL_UNMATCHED, modelPlanner.lastCommand)
        assertTrue("no session may be started for an unrecognised command", outcome !is CommandOutcome.AgentSessionStarted)
    }

    /**
     * **R14-39, pinned as current behaviour — not endorsed.**
     *
     * Since Task 6b, `NoPlan` at step 2b has a second meaning: not only "nothing matched" but also "a
     * registered tool matched and its target could not be resolved". `RouteCommandUseCase` cannot tell
     * the two apart, so a command a tool claimed **deterministically** falls through to steps (5)–(7)
     * and the raw text is sent to the cloud model.
     *
     * The first assertion is what makes this a reproduction rather than a restatement: the vocabulary
     * *did* claim the text for `uninstall_app`; only [AppTargetResolver] declined. Changing the routing
     * is **A4′'s** (`CLAUDE.md`, A1′ residual (3)), not this task's.
     */
    @Test
    fun `R14-39 - an unresolvable target makes a matched tool command fall through to the cloud model`() =
        runTest {
            assertEquals(
                "the premise: the vocabulary must claim this text, or this test proves nothing",
                Tier0ToolIds.UNINSTALL_APP,
                ToolVocabulary().match(GOAL_UNINSTALL_UNKNOWN)?.id,
            )

            val outcome = routeUseCase(federation()).route(GOAL_UNINSTALL_UNKNOWN)

            assertTrue("no session may be started: $outcome", outcome !is CommandOutcome.AgentSessionStarted)
            assertEquals(1, modelPlanner.planCallCount)
            assertEquals(GOAL_UNINSTALL_UNKNOWN, modelPlanner.lastCommand)
        }

    // ---------------------------------------------------------------------------------------------
    // The precondition gate, from text.
    // ---------------------------------------------------------------------------------------------

    /**
     * A tool whose manifest permission this process does not hold is withheld by its source, so it is
     * unreachable **from text** — the planner finds the trigger and then finds no descriptor. The
     * grant-everything control beside it is what keeps this from passing because the trigger was wrong.
     */
    @Test
    fun `a tool whose permission is absent is unreachable from text`() = runTest {
        val denied = federation(presence = PermissionPresence { false })

        val withheld = planner().plan(PlanningRequest(goal(GOAL_ALARM)), denied.registry)
        assertTrue("the registry withheld it, so the planner must find nothing: $withheld", withheld is PlanningResult.NoPlan)

        val available = planner().plan(PlanningRequest(goal(GOAL_ALARM)), federation().registry)
        assertTrue("the same text must plan when the permission is held: $available", available is PlanningResult.Planned)
    }

    private companion object {
        /**
         * **No two of these share a substring, and none of the packages occurs in any goal text.**
         * That is the whole defence against the two hollow-proof shapes this block has already been
         * bitten by — an expected value contained in another, and a goal text that literally contains
         * what is asserted.
         */
        const val TARGET_LABEL = "zephyr"
        const val TARGET_PACKAGE = "com.quill.notepad"
        const val OWN_LABEL = "kestrel"
        const val OWN_PACKAGE = "net.ownward.shell"
        const val ALIAS_PHRASE = "marmoset"

        const val GOAL_UNINSTALL = "удали приложение $TARGET_LABEL"
        const val GOAL_UNINSTALL_OWN = "удали приложение $OWN_LABEL"
        const val GOAL_UNINSTALL_UNKNOWN = "удали приложение pangolin"
        const val GOAL_ALIAS = "называй $TARGET_LABEL как $ALIAS_PHRASE"
        const val GOAL_ALARM = "поставь будильник на 7:30"

        /** Claimed by neither FastPath nor the vocabulary, so the model is legitimately consulted. */
        const val GOAL_UNMATCHED = "расскажи что-нибудь про горы"
    }
}

/** The `in_app` and `app_shortcut` adapters own nothing planned here; routing to one is a defect. */
private object SeamRefusingWorker : ToolWorker {
    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
        error("this adapter must not be asked to run ${invocation.id.value}")
}

/** `IntentLauncher` is a plain interface, not a `fun interface`, so the recorder is a class. */
private class SeamIntentRecorder(private val record: MutableList<Intent>) : IntentLauncher {
    override fun launch(intent: Intent) {
        record += intent
    }
}
