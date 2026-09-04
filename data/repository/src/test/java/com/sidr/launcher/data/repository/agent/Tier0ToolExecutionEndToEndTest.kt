package com.sidr.launcher.data.repository.agent

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock
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
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.db.SidrDatabase
import com.sidr.launcher.data.repository.db.dao.AgentSessionDao
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CompositePlanner
import com.sidr.launcher.domain.agent.ExecutionState
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
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationResult
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

/**
 * **The last join of the headline path** (final whole-branch review, finding 2).
 *
 * [FreeTextGoalEndToEndTest] stops at persistence — its worker errors if anything invokes it — and
 * `Tier0IntentToolWorkerTest` starts from a hand-built [ResolvedInvocation]. Between them sat the one
 * hop nothing ran: plan -> [AgentExecutor] -> [ToolFederation] -> [Tier0IntentToolWorker]. That is the
 * same shape as the CRITICAL this block found in review, where four layers each fully tested in
 * isolation summed to a capability that was dead in production with 1285 tests green; and it is the
 * join finding 1 (the uncaught `startActivity`) lived inside — a test crossing it would have caught
 * that for free, which is why the second test below plants a throwing launcher rather than only a
 * recording one.
 *
 * **Where it lives and why.** `:data:repository`'s test source set is the only place that sees both
 * modules: [ToolMatchPlanner]/[ToolVocabulary]/[Tier0IntentToolSource]/[Tier0IntentToolWorker] are
 * `:data:repository`, the engine and the federation are `:domain`, and `:domain:jvmTest` cannot see
 * `:data:repository` (the dependency runs the other way) while `:app` would drag Hilt in for nothing.
 * The alternative — an instrumented test in `:app` — is where a *device* check belongs, and this block
 * has not had one; a JVM/Robolectric test is what can hold the join in CI.
 *
 * Everything below the four fakes is the production object, wired exactly as `AgentProvidesModule`
 * wires it: the real [ToolVocabulary] inside the real [ToolMatchPlanner] inside the real
 * [CompositePlanner], the real two-adapter [ToolFederation] in production's own `IN_APP`-first order,
 * the real [AgentExecutor] over that federation's own executor, the real [StartAgentSessionUseCase] /
 * [RunAgentSessionUseCase], and the real [RoomAgentSessionStore] over a real Room database. Only what
 * a JVM test cannot have is faked — FastPath's matcher, the model planner, the provider config,
 * connectivity — plus the one seam an intent leaves through, which is the point of the test.
 *
 * The `IN_APP` adapter keeps a [RefusingInAppWorker]: the timer tool belongs to the `SYSTEM_INTENT`
 * adapter, so the federation routing to the wrong one is a failure, not a silent pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Tier0ToolExecutionEndToEndTest {

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

    /** Exactly `AgentProvidesModule.provideToolFederation`'s list, with the real Tier-0 worker in it. */
    private fun federation(launcher: IntentLauncher) = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), RefusingInAppWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), Tier0IntentToolWorker(launcher)),
        ),
    )

    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    /**
     * Route -> run, which is what the product does: `RouteCommandUseCase` step 2b starts and persists
     * the session, and `LauncherAgentSession.attach` then loads the active row and turns the crank.
     * Reloading through [store] rather than passing an in-memory session is deliberate — it is the
     * hand-off the ViewModel actually performs.
     */
    private suspend fun routeAndRun(command: String, launcher: IntentLauncher): Pair<CommandOutcome, AgentSession?> {
        val federation = federation(launcher)
        val start = StartAgentSessionUseCase(
            planner = CompositePlanner(listOf(TemplatePlanner(), ToolMatchPlanner(ToolVocabulary()))),
            store = store,
            ids = ids,
            registry = federation.registry,
        )
        val route = RouteCommandUseCase(
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
            startAgentSession = start,
        )
        val run = RunAgentSessionUseCase(
            executor = AgentExecutor(
                registry = federation.registry,
                toolExecutor = federation.executor,
                budget = RuntimeBudget.Default,
            ),
            store = store,
        )

        val outcome = route.route(command)
        val active = (store.active() as OperationResult.Success).value ?: return outcome to null
        return outcome to (run.run(active) as? OperationResult.Success)?.value
    }

    /**
     * The join, asserted on the **intent that actually arrives** rather than on the endpoints: action,
     * length and the un-skipped clock UI together pin the whole chain's argument binding — the
     * vocabulary's remainder, the planner's `ArgSource.Literal`, the validator's resolution, the
     * federation's routing by id, and the worker's duration parse.
     */
    @Test
    fun `a typed timer command reaches the tier-0 worker and issues the timer intent`() = runTest {
        val launched = mutableListOf<Intent>()

        val (outcome, finished) = routeAndRun("set a timer for 10 minutes", RecordingIntentLauncher(launched))

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
        assertEquals("the model planner must never be reached", 0, modelPlanner.planCallCount)

        val intent = launched.single()
        assertEquals(AlarmClock.ACTION_SET_TIMER, intent.action)
        assertEquals(600, intent.getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertEquals(false, intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))

        assertNotNull("the run must return a session", finished)
        checkNotNull(finished)
        assertEquals(ExecutionState.Completed, finished.state)
        assertEquals(ToolResult.Effected(), finished.observations[0])
    }

    /**
     * The same crossing with the world refusing: no activity is registered for
     * `AlarmClock.ACTION_SET_TIMER` (an AOSP or ROM build, or Deskclock disabled). Before finding 1's
     * fix this threw out of `Tier0IntentToolWorker`, through `AgentExecutor.perform`'s one call site
     * and `RunAgentSessionUseCase.run`, into `viewModelScope` — and killed the home screen. The step
     * must be observed as [ToolResult.Failed] and the run must return normally.
     *
     * The plan still ends [ExecutionState.Completed] rather than `Failed`: one failure is under
     * `RuntimeBudget.Default`'s consecutive-failure limit and the plan then runs out of steps. That is
     * A0's already-recorded "план пройден, выполнено не всё" shape — the per-step state is what the
     * surface reads — so the assertion is on the observation, not on the terminal state.
     */
    @Test
    fun `a device with no timer app fails the step instead of killing the process`() = runTest {
        val (outcome, finished) = routeAndRun("set a timer for 10 minutes", RefusingIntentLauncher)

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
        assertNotNull("the run must return rather than throw", finished)
        checkNotNull(finished)
        assertEquals(ToolResult.Failed(CommandFailure.Generic), finished.observations[0])
        assertTrue("nothing may be left running", finished.state.isTerminal)
    }
}

/** The timer belongs to the `SYSTEM_INTENT` adapter; the federation routing here is a defect. */
private object RefusingInAppWorker : ToolWorker {
    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
        error("the in-app adapter must not be asked to run ${invocation.id.value}")
}

private class RecordingIntentLauncher(private val record: MutableList<Intent>) : IntentLauncher {
    override fun launch(intent: Intent) { record += intent }
}

/** A device with nothing registered for the intent — what `startActivity` does there, done here. */
private object RefusingIntentLauncher : IntentLauncher {
    override fun launch(intent: Intent): Nothing =
        throw ActivityNotFoundException("no activity handles ${intent.action}")
}
