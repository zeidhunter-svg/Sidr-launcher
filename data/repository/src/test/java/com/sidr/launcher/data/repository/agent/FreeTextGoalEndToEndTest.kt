package com.sidr.launcher.data.repository.agent

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeCommandPlanner
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.NoOpIntentMatcher
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.data.repository.db.SidrDatabase
import com.sidr.launcher.data.repository.db.dao.AgentSessionDao
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CompositePlanner
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The planner and the router never invoke anything; the federation just needs a worker to exist. */
private object NeverInvokedWorker : ToolWorker {
    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
        error("no tool may be invoked while only planning and persisting")
}

/**
 * **The seam no single-layer test covers.** A1' Task 9 composed `TemplatePlanner` with
 * `ToolMatchPlanner`, so the Android planner now produces [GoalShape.Free]; every layer of that path
 * was tested against a fake of the next one — `RouteCommandUseCase` against `FakeAgentSessionStore`,
 * `ToolMatchPlanner` against `FakeToolRegistry`, the mapper against hand-built sessions — and the one
 * join none of them crossed was planner -> **real Room store**, where `toSessionEntity` refused
 * `Free`. The whole block's headline capability was therefore dead in production with 1285 green
 * tests.
 *
 * So everything below the fakes is the production object: the real [ToolVocabulary], the real
 * [ToolMatchPlanner] inside the real [CompositePlanner] that `AgentProvidesModule` builds, the real
 * [Tier0IntentToolSource] seen through the real [ToolFederation], the real
 * [StartAgentSessionUseCase], and the real [RoomAgentSessionStore] over a real Room database. Only
 * the things a JVM test cannot have — FastPath's matcher, the model planner, the provider config,
 * connectivity — are fakes, and each is set to the state that makes the agent branch the one under
 * test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class FreeTextGoalEndToEndTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: AgentSessionDao
    private lateinit var store: RoomAgentSessionStore

    private val modelPlanner = FakeCommandPlanner()

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

    /** Exactly what `AgentProvidesModule.provideToolRegistry` builds, minus a worker that can run. */
    private val registry = ToolFederation(
        listOf(ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), NeverInvokedWorker)),
    ).registry

    /** Exactly what `AgentProvidesModule.providePlanner` builds. */
    private val planner = CompositePlanner(listOf(TemplatePlanner(), ToolMatchPlanner(ToolVocabulary())))

    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    private fun startAgentSession() = StartAgentSessionUseCase(planner, store, ids, registry)

    /**
     * The command reaches step 2b: FastPath is undecided ([NoOpIntentMatcher] answers
     * `UnknownIntent` at confidence 0), the device is not local-only, a provider is configured and
     * the network is up — so if the agent branch fails open, the fall-through below it is a *live*
     * path and the model planner records the call.
     */
    private fun routeCommand() = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = NoOpIntentMatcher(),
            resolver = IntentActionResolver(FakeInstalledAppsRepository()),
            executor = FakeActionExecutor(),
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(SupervisorJob()),
        ),
        planner = modelPlanner,
        catalog = FakeActionCatalog(),
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = false)),
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
        startAgentSession = startAgentSession(),
    )

    private suspend fun activeSession() = (store.active() as OperationResult.Success).value

    /**
     * The join itself: the real planner's `Free` session survives the real store. Asserted through
     * `active()` rather than through the DAO, because reading it back is the half `readShape` owns —
     * an encode that writes a row a decode cannot read is the same outage one layer later.
     */
    @Test
    fun `a free-text goal planned for a registered tool is written to Room and read back whole`() = runTest {
        val goal = AgentGoal("поставь таймер на 10 минут", GoalShape.Free("поставь таймер на 10 минут"))

        val started = startAgentSession().start(goal)

        assertTrue("the session must start, not fail: $started", started is OperationResult.Success)
        assertEquals(AgentSessionId("s1"), (started as OperationResult.Success).value)

        val restored = activeSession()
        assertNotNull("the session must be on disk", restored)
        checkNotNull(restored)
        assertEquals(goal, restored.goal)
        assertEquals(ExecutionState.Running, restored.state)
        assertEquals(1, restored.plan.steps.size)
        assertEquals(Tier0ToolIds.SET_TIMER, restored.plan.steps[0].invocation.id)
        assertEquals(ArgSource.Literal("10 минут"), restored.plan.steps[0].invocation.args["duration"])
        assertEquals(StepRationale.GOAL_DIRECT, restored.plan.steps[0].rationale)
    }

    /**
     * The live defect, end to end and in the user's own words: an undecided command that a registered
     * tool matches must become an agent session, and must not fall through to the model. The
     * `planCallCount` assertion is what makes this a reproduction rather than a restatement — with a
     * refusing mapper the store failure is contained, step 2b's `is Success` test is false, and the
     * command reaches the provider path in silence with nothing on disk and no error reported.
     */
    @Test
    fun `an undecided command that matches a registered tool starts a persisted session, not a model call`() =
        runTest {
            val outcome = routeCommand().route("поставь таймер на 10 минут")

            assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
            assertNotNull("a started session must leave a row behind", dao.activeSession())
            assertEquals("the model planner must never be reached", 0, modelPlanner.planCallCount)
        }
}
