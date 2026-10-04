package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 2 — staleness, layer 1 (spec §3 0.3, §6.4 as amended by owner fork F2-3.1).
 *
 * The A0 acceptance found (§12.8) that a paused plan acts on what it saw before the pause: it had
 * observed "not installed", the user installed the app meanwhile, and «Продолжить» opened the store
 * anyway. A plan in which nothing has ACTED is now planned afresh — a new session, which replaces the
 * stale one in the store (plan §0.3 P5). Anything that may already have acted continues as before.
 */
class ResumeAgentSessionUseCaseTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val store = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }
    private val goal = AgentGoal("открой вельтрикс", GoalShape.AppNotInstalled("вельтрикс"))

    private fun notInstalled() = ToolResult.Observed(
        ObservedFact.APP_NOT_INSTALLED,
        ToolOutput(mapOf("resolved_query" to "вельтрикс")),
    )

    private fun runner(tools: FakeToolExecutor) = RunAgentSessionUseCase(AgentExecutor(registry, tools), store)

    private fun resume(tools: FakeToolExecutor, planner: Planner = TemplatePlanner()) =
        ResumeAgentSessionUseCase(StartAgentSessionUseCase(planner, store, ids, registry), runner(tools))

    /** The §12.8 shape: step 0 observed "not installed", the store step waits for consent, the process died. */
    private suspend fun pausedAfterLooking(): AgentSession {
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        runner(FakeToolExecutor(listOf(notInstalled()))).run(store.active)
        val paused = store.active.pausedForRestore()
        store.save(paused)
        return paused
    }

    private fun OperationResult<Resumption>.ran(): AgentSession =
        ((this as OperationResult.Success).value as Resumption.Ran).session

    /** A one-step store search the MODEL proposed, stopped at its consent card, the process killed. */
    private suspend fun pausedModelPlan(): AgentSession {
        val session = AgentSession(
            id = AgentSessionId("m1"),
            goal = AgentGoal("найди lumora в магазине", GoalShape.Free("найди lumora в магазине")),
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("lumora"))),
                        risk = ActionRiskLevel.CONFIRM,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                ),
            ),
            cursor = 0,
            state = ExecutionState.Paused,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.PlanCreated(1),
                    TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL)),
                    TraceEvent.SessionPaused,
                ),
            ),
        )
        store.save(session)
        return session
    }

    /** Counts every ask, whatever it answers (for `GoalShape.Free` it delegates to `TemplatePlanner`, which says `NoPlan`). */
    private class CountingPlanner : Planner {
        var calls = 0
        override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
            calls++
            return TemplatePlanner().plan(request, registry)
        }
    }

    @Test
    fun `a paused plan that has only looked is planned again, and an app installed meanwhile opens`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val after = resume(tools).resume(pausedAfterLooking()).ran()

        assertEquals(AgentSessionId("s2"), after.id)
        assertEquals(ExecutionState.Completed, after.state)
        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
    }

    @Test
    fun `a paused plan still missing its app offers the store again, after a fresh look`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))

        val after = resume(tools).resume(pausedAfterLooking()).ran()

        assertEquals(AgentSessionId("s2"), after.id)
        assertEquals(ExecutionState.AwaitingConsent, after.state)
        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
        assertEquals("the fresh session replaced the stale one", AgentSessionId("s2"), store.active.id)
    }

    @Test
    fun `a paused plan in which a step already acted continues from its cursor`() = runTest {
        val launch = { index: Int, name: String ->
            PlanStep(index, ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal(name))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT)
        }
        val paused = AgentSession(
            id = AgentSessionId("s-acted"),
            goal = goal,
            plan = ExecutionPlan(listOf(launch(0, "лумора"), launch(1, "вельтрикс"))),
            cursor = 1,
            state = ExecutionState.Paused,
            observations = mapOf(0 to ToolResult.Effected()),
            consents = emptyMap(),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.PlanCreated(2), TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
                    TraceEvent.ToolObserved(0, ToolResult.Effected()), TraceEvent.SessionPaused,
                ),
            ),
        )
        store.save(paused)
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val after = resume(tools).resume(paused).ran()

        assertEquals(AgentSessionId("s-acted"), after.id)
        assertEquals(listOf(mapOf("query" to "вельтрикс")), tools.invocations.map { it.args })
    }

    /** Review Focus 3: the act may already have happened, so planning it again would perform it twice. */
    @Test
    fun `a call in flight at the pause is resumed, never planned again`() = runTest {
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        store.save(AgentExecutor(registry, FakeToolExecutor(emptyList())).prepare(store.active).pausedForRestore())
        val tools = FakeToolExecutor(listOf(notInstalled()))

        val after = resume(tools).resume(store.active).ran()

        assertEquals(AgentSessionId("s1"), after.id)
        assertEquals(1, tools.invocations.size)
        assertEquals(1, after.trace.events.count { it is TraceEvent.ToolInvoked && it.index == 0 })
    }

    /** Review Focus 4: an undo's goal is the ORIGINAL goal — planning it again would redo what it undoes. */
    @Test
    fun `an undo plan is never planned again from its goal, which would redo what it undoes`() = runTest {
        var asked = 0
        val eager = object : Planner {
            override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
                asked++
                return TemplatePlanner().plan(request, registry)
            }
        }
        val undo = AgentSession(
            id = AgentSessionId("s-undo"),
            goal = goal,
            plan = ExecutionPlan(
                listOf(PlanStep(0, ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("лумора"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.COMPENSATION)),
            ),
            cursor = 0,
            state = ExecutionState.Paused,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(listOf(TraceEvent.PlanCreated(1), TraceEvent.SessionPaused)),
        )
        store.save(undo)
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val after = resume(tools, eager).resume(undo).ran()

        assertEquals(0, asked)
        assertEquals(AgentSessionId("s-undo"), after.id)
        assertEquals(listOf(mapOf("query" to "лумора")), tools.invocations.map { it.args })
    }

    /**
     * Owner decision 2026-10-03, M1(b): the card promised a fresh look, so when the look finds no plan
     * the stale session ends Failed. Continuing a plan whose re-check just failed is §12.8's staleness.
     */
    @Test
    fun `a goal that no longer plans ends the stale plan honestly instead of continuing it`() = runTest {
        val nothing = object : Planner {
            override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult = PlanningResult.NoPlan
        }
        val paused = pausedAfterLooking()
        val tools = FakeToolExecutor(emptyList())

        val after = resume(tools, nothing).resume(paused).ran()

        assertEquals(AgentSessionId("s1"), after.id)
        assertEquals(ExecutionState.Failed, after.state)
        assertTrue(tools.invocations.isEmpty())
        assertEquals(TraceEvent.SessionEnded(ExecutionState.Failed), after.trace.events.last())
        assertTrue(TraceEvent.SessionResumed !in after.trace.events)
        assertEquals("at rest the store is empty", null, store.activeOrNull)
    }

    /**
     * Spec §7.12.3 condition 4, criterion 20. A plan the model wrote, in which nothing has acted, is not
     * planned again here: `CompositePlanner` would answer `NoPlan` (`ToolMatchPlanner` cannot read the
     * model's words), and M1(b) would end a legitimate plan «не удалось завершить». The engine hands it
     * back; the layer that holds the route takes its words through the route again.
     */
    @Test
    fun `a model-planned paused plan that has only looked is handed back for a new route, never planned again`() = runTest {
        val planner = CountingPlanner()
        val paused = pausedModelPlan()

        val result = resume(FakeToolExecutor(emptyList()), planner).resume(paused)

        assertEquals(OperationResult.Success(Resumption.NeedsRoute(paused.goal)), result)
        assertEquals(0, planner.calls)
        assertEquals("the stale session is the route layer's to close", paused, store.active)
    }

    /**
     * The phase-2 rule stands for a model plan too: a call in flight at the pause may have acted, so it is
     * resumed once, never re-routed. A `SAFE` step (`web_search`), so no consent question confounds it.
     */
    @Test
    fun `a model-planned plan paused mid-call resumes its call instead of going back to the route`() = runTest {
        val webSearch = ToolId("web_search")
        val paused = AgentSession(
            id = AgentSessionId("m2"),
            goal = AgentGoal("найди ostravel lectures", GoalShape.Free("найди ostravel lectures")),
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(webSearch, mapOf("query" to ArgSource.Literal("ostravel lectures"))),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                ),
            ),
            cursor = 0,
            state = ExecutionState.Paused,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(
                listOf(TraceEvent.PlanCreated(1), TraceEvent.ToolInvoked(0, webSearch), TraceEvent.SessionPaused),
            ),
        ).also { store.save(it) }
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val planner = CountingPlanner()

        val after = resume(tools, planner).resume(paused).ran()

        assertEquals(ExecutionState.Completed, after.state)
        assertEquals(listOf(webSearch), tools.invocations.map { it.id })
        assertEquals(0, planner.calls)
    }
}
