package com.sidr.launcher.data.repository.agent

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.data.repository.db.SidrDatabase
import com.sidr.launcher.data.repository.db.dao.AgentSessionDao
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.Dispatchers
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
 * **"At rest, the agent tables are empty" — proved per terminal state, against the real database.**
 *
 * `AgentSessionStore`'s KDoc says there is no history: a terminal state deletes the record. That is a
 * privacy claim, not a housekeeping detail — a session row carries the user's raw command, the app
 * they were looking for, and every step the agent took on their behalf. So it is tested once for each
 * way a session can end rather than once for the convenient one: `RunAgentSessionUseCase.persist`
 * keys deletion on `ExecutionState.isTerminal`, and a future edit that special-cased any single state
 * would leave the others silently retained.
 *
 * The five paths below are the five ways a session actually stops in A0, and every one of them is
 * driven through the real [AgentExecutor] and the real [RoomAgentSessionStore] — no hand-constructed
 * terminal session, because what is being tested is that the *production path* deletes.
 *
 * **[ExecutionState.Cancelled] has two paths, and the second is the one that matters most.** A session
 * can be cancelled explicitly ([CancelAgentSessionUseCase]) or by the user **refusing consent** at a
 * checkpoint — `AgentExecutor.prepare` sees `consents[i] == false`, records `ConsentResolved(granted =
 * false)` and ends the session `Cancelled`. That second path is a user saying "no", and a record that
 * outlived the refusal would be the worst version of this bug.
 *
 * **Two budgets are deliberately tighter than `RuntimeBudget.Default`.** Neither `Failed` nor `Blocked`
 * is reachable with the A0 plan under the default budget — one failed step is under the
 * consecutive-failure limit, and the plan is shorter than `maxSteps` — so a test claiming to cover
 * them under the default budget would be covering `Completed` twice. `AgentSessionUseCasesTest`
 * already uses the same idiom for its `Failed` case.
 *
 * Every case asserts the tables are **non-empty first**. Otherwise "empty at rest" would pass just as
 * well for a session that was never written, which is the vacuous shape Этап 2 found elsewhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AgentAtRestGuardTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: AgentSessionDao
    private lateinit var store: RoomAgentSessionStore

    private val registry = FakeToolRegistry.withA0Tools()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }
    private val goal = AgentGoal(text = "открой убер", shape = GoalShape.AppNotInstalled("убер"))

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

    /** `launch_app`'s missing-app result, carrying the `resolved_query` step 1 binds to (F6). */
    private fun notInstalled() = ToolResult.Observed(
        ObservedFact.APP_NOT_INSTALLED,
        ToolOutput(mapOf("resolved_query" to "убер")),
    )

    private fun runner(tools: ToolExecutor, budget: RuntimeBudget = RuntimeBudget.Default) =
        RunAgentSessionUseCase(AgentExecutor(registry, tools, budget), store)

    private suspend fun active(): AgentSession? =
        (store.active() as OperationResult.Success).value

    /**
     * Starts a session and asserts it is genuinely on disk — the row, its steps and its trace. Without
     * this half, every "and then it was empty" below would hold for a session that never existed.
     */
    private suspend fun startPersisted(): AgentSessionId {
        val started = StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        val id = (started as OperationResult.Success).value
        assertNotNull("the planner produced no plan, so nothing was written to compare against", id)

        assertNotNull("the session row must exist before the terminal transition", dao.activeSession())
        assertTrue("the plan's steps must be on disk", dao.stepsFor(id!!.value).isNotEmpty())
        assertTrue("the trace must be on disk", dao.traceFor(id.value).isNotEmpty())
        return id
    }

    private suspend fun assertAtRest(id: AgentSessionId, reached: ExecutionState) {
        assertNull("$reached left a session row behind", dao.activeSession())
        assertEquals("$reached left plan steps behind", 0, dao.stepsFor(id.value).size)
        assertEquals("$reached left trace events behind", 0, dao.traceFor(id.value).size)
        assertNull("$reached left a readable active session behind", active())
    }

    /** Drives a fresh session to the consent checkpoint, where four of the five paths diverge. */
    private suspend fun atCheckpoint(tools: ToolExecutor): Pair<AgentSessionId, RunAgentSessionUseCase> {
        val id = startPersisted()
        val run = runner(tools)
        val ran = run.run(active()!!)
        assertEquals(
            ExecutionState.AwaitingConsent,
            (ran as OperationResult.Success).value.state,
        )
        return id to run
    }

    @Test
    fun `Completed leaves nothing at rest`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val (id, run) = atCheckpoint(tools)

        val resolved = ResolveConsentUseCase(store, run).resolve(id, 1, granted = true)

        assertEquals(ExecutionState.Completed, (resolved as OperationResult.Success).value?.state)
        assertAtRest(id, ExecutionState.Completed)
    }

    /** Path one to [ExecutionState.Cancelled]: the user cancelled the session outright. */
    @Test
    fun `Cancelled by an explicit cancel leaves nothing at rest`() = runTest {
        val (id, _) = atCheckpoint(FakeToolExecutor(listOf(notInstalled())))

        CancelAgentSessionUseCase(store).cancel(id)

        assertAtRest(id, ExecutionState.Cancelled)
    }

    /**
     * Path two to [ExecutionState.Cancelled], and the one a user would actually feel: they were asked
     * to confirm a step and said no. `AgentExecutor.prepare` reads `consents[1] == false`, records the
     * refusal and ends the session; `RunAgentSessionUseCase.persist` must then delete it. A refusal
     * that left the record on disk would be the system keeping what the user just declined.
     */
    @Test
    fun `Cancelled by a refused consent leaves nothing at rest`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val (id, run) = atCheckpoint(tools)

        val resolved = ResolveConsentUseCase(store, run).resolve(id, 1, granted = false)

        assertEquals(ExecutionState.Cancelled, (resolved as OperationResult.Success).value?.state)
        assertEquals("the refused step must never have run", 1, tools.invocations.size)
        assertAtRest(id, ExecutionState.Cancelled)
    }

    /**
     * `maxConsecutiveFailures = 1`, because under `RuntimeBudget.Default` one failed step is under the
     * limit and the A0 plan's step 1 is then skipped by its precondition — the session would end
     * `Completed` and never exercise `Failed` at all.
     */
    @Test
    fun `Failed leaves nothing at rest`() = runTest {
        val id = startPersisted()
        val tools = FakeToolExecutor(listOf(ToolResult.Failed(CommandFailure.Generic)))

        val ran = runner(tools, RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 1)).run(active()!!)

        assertEquals(ExecutionState.Failed, (ran as OperationResult.Success).value.state)
        assertAtRest(id, ExecutionState.Failed)
    }

    /**
     * `maxSteps = 1`, for the same reason: `Blocked` is the loop bound firing, and the A0 plan is two
     * steps long, so under the default budget of eight it is unreachable. With a bound of one, step 0
     * runs for real and the plan is stopped before step 1 — the honest shape of "this plan wanted more
     * than it was allowed".
     */
    @Test
    fun `Blocked leaves nothing at rest`() = runTest {
        val id = startPersisted()
        val tools = FakeToolExecutor(listOf(notInstalled()))

        val ran = runner(tools, RuntimeBudget(maxSteps = 1, maxConsecutiveFailures = 2)).run(active()!!)

        assertEquals(ExecutionState.Blocked, (ran as OperationResult.Success).value.state)
        assertEquals("step 0 must actually have run, or the budget stopped nothing", 1, tools.invocations.size)
        assertAtRest(id, ExecutionState.Blocked)
    }

    /**
     * The negative control. A session that has NOT reached a terminal state must still be on disk —
     * otherwise `assertAtRest` would be satisfied by a store that deletes everything always, and the
     * five tests above would prove nothing about `isTerminal`.
     */
    @Test
    fun `a session still awaiting consent is deliberately not at rest`() = runTest {
        val (id, _) = atCheckpoint(FakeToolExecutor(listOf(notInstalled())))

        assertNotNull("a live session must survive", dao.activeSession())
        assertTrue(dao.stepsFor(id.value).isNotEmpty())
        assertTrue(dao.traceFor(id.value).isNotEmpty())
        assertEquals(ExecutionState.AwaitingConsent, active()?.state)
    }
}
