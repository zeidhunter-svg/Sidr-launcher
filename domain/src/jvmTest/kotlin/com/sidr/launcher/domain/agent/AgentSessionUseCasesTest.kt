package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionUseCasesTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val store = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }
    private val goal = AgentGoal("открой убер", GoalShape.AppNotInstalled("убер"))

    /** `launch_app`'s missing-app result, carrying the `resolved_query` step 1 binds to (F6). */
    private fun notInstalled(query: String = "убер") = ToolResult.Observed(
        ObservedFact.APP_NOT_INSTALLED,
        ToolOutput(mapOf("resolved_query" to query)),
    )

    private fun runner(tools: ToolExecutor) =
        RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), store)

    @Test
    fun `start persists a running session and returns its id`() = runTest {
        val started = StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)

        val id = (started as OperationResult.Success).value
        assertEquals(AgentSessionId("s1"), id)
        assertEquals(ExecutionState.Running, store.saved.last().state)
        assertTrue(store.saved.last().trace.events.any { it is com.sidr.launcher.domain.trace.TraceEvent.PlanCreated })
    }

    @Test
    fun `start returns null and persists nothing when there is no plan`() = runTest {
        val blank = AgentGoal("открой", GoalShape.AppNotInstalled("  "))

        val started = StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(blank)

        assertNull((started as OperationResult.Success).value)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `run stops at the consent checkpoint with the session persisted`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)

        val ran = runner(tools).run(store.active)

        assertEquals(ExecutionState.AwaitingConsent, (ran as OperationResult.Success).value.state)
        assertEquals(ExecutionState.AwaitingConsent, store.active.state)
        assertEquals(1, tools.invocations.size)
    }

    @Test
    fun `a completed session is deleted, leaving nothing at rest`() = runTest {
        val tools = FakeToolExecutor(
            listOf(notInstalled(), ToolResult.Effected()),
        )
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        val run = runner(tools)
        run.run(store.active)

        val resolved = ResolveConsentUseCase(store, run).resolve(AgentSessionId("s1"), 1, granted = true)

        assertEquals(ExecutionState.Completed, (resolved as OperationResult.Success).value?.state)
        assertNull(store.activeOrNull)
        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.PLAY_STORE_SEARCH), tools.invocations.map { it.id })
    }

    @Test
    fun `a second confirmation of the same step does not execute it twice`() = runTest {
        val tools = FakeToolExecutor(
            listOf(notInstalled(), ToolResult.Effected()),
        )
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        val run = runner(tools)
        run.run(store.active)
        val consent = ResolveConsentUseCase(store, run)

        consent.resolve(AgentSessionId("s1"), 1, granted = true)
        val second = consent.resolve(AgentSessionId("s1"), 1, granted = true)

        assertNull((second as OperationResult.Success).value)
        assertEquals(2, tools.invocations.size)   // NOT 3
    }

    @Test
    fun `cancelling deletes the session and the next step never runs`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        runner(tools).run(store.active)

        CancelAgentSessionUseCase(store).cancel(AgentSessionId("s1"))

        assertNull(store.activeOrNull)
        assertEquals(1, tools.invocations.size)
    }

    @Test
    fun `a store failure is surfaced as a Failure and never thrown`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        store.failNextSave = true

        val ran = runner(tools).run(store.active)

        assertTrue(ran is OperationResult.Failure)
    }

    // --- Ruling R13: the loop persists BETWEEN prepare and perform ------------------------------

    /**
     * The whole point of the engine's `prepare`/`perform` split is that the caller can save in the
     * gap. This test is the only one that can see that gap: the six above observe end states, which
     * are identical whether the loop drives the two halves or the composed `advance`.
     *
     * The probe reads the store **at the instant the tool is called**. What must already be at rest
     * by then is a session whose trace tail is `ToolInvoked` for the step being invoked with no
     * matching `ToolObserved` — the "the process died during a tool call" signal the resume path
     * reads back. Without the between-save, a crash inside the tool call loses that record and the
     * step is silently re-run on resume, executing its side effect twice.
     */
    @Test
    fun `the session is persisted between prepare and perform, so a mid-call crash is legible`() = runTest {
        val snapshots = mutableListOf<AgentSession>()
        val probe = object : ToolExecutor {
            val invocations = mutableListOf<ResolvedInvocation>()
            override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
                snapshots += store.active
                invocations += invocation
                return notInstalled()
            }
        }
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)

        runner(probe).run(store.active)

        assertEquals(1, probe.invocations.size)
        val atRest = snapshots.single()
        val tail = atRest.trace.events.last()
        assertTrue("the persisted tail must be ToolInvoked, was $tail", tail is TraceEvent.ToolInvoked)
        assertEquals(TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP), tail)
        assertTrue(
            "the persisted session must not yet carry a ToolObserved for step 0",
            atRest.trace.events.none { it is TraceEvent.ToolObserved && it.index == 0 },
        )
    }

    // --- Ruling R14: the fixed-point net is live code, not speculative defence ------------------

    /**
     * `perform`'s "the resolved step does not match the trace" branch returns the session **unchanged
     * and still `Running`**, which makes it this loop's fixed point: `prepare` short-circuits on the
     * mid-step tail, `perform` refuses the lookup, and neither half moves anything. Without the
     * `next == current` net that is a silent permanent stall — no trace event, no state change, no
     * progress. The net turns it into a `Blocked` session.
     *
     * The trace here names step 5, which the two-step plan does not contain — the realistic shape of
     * a persisted trace replayed against a plan swapped underneath it (a restore against a different
     * build, a shorter re-plan). `AgentExecutorTest` uses this same construction for `perform`'s own
     * unit test.
     *
     * The bounded timeout is deliberate: removing the net does not fail this test, it spins forever,
     * so the timeout is what converts "the net is gone" into a reportable failure instead of a hung
     * suite.
     *
     * **Both** bounds are needed, and the JUnit one is the one that actually fires. The loop this
     * guards spins without ever suspending, so `runTest`'s watchdog — which is dispatched onto the
     * same `runBlocking` thread the test body occupies — never gets to run; verified by mutation, the
     * task hung past 400 s with `runTest(timeout = 5.seconds)` as the only bound. JUnit's `timeout`
     * runs the body on its own thread and its watchdog reports from outside, so it is what turns the
     * spin into a `TestTimedOutException`. `runTest`'s bound is kept for the suspending stalls it
     * *can* see.
     */
    @Test(timeout = 30_000)
    fun `a session the engine cannot move is Blocked rather than spun on forever`() = runTest(timeout = 5.seconds) {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        val stalled = store.active.copy(
            state = ExecutionState.Running,
            trace = ExecutionTrace(
                listOf(TraceEvent.StepStarted(5), TraceEvent.ToolInvoked(5, ToolIds.LAUNCH_APP)),
            ),
        )

        val ran = runner(tools).run(stalled)

        assertEquals(ExecutionState.Blocked, (ran as OperationResult.Success).value.state)
        assertEquals(0, tools.invocations.size)
        // `Blocked` is terminal, so the at-rest guarantee applies to it exactly as it does to
        // `Completed`: `RunAgentSessionUseCase.persist` keys deletion on `ExecutionState.isTerminal`,
        // and asserting the state alone would pass even if that deletion were removed.
        assertNull(store.activeOrNull)
    }

    /**
     * The fourth terminal state, and the last one whose deletion was asserted nowhere. `Failed` is
     * reached here through the real engine — `perform` applies the consecutive-failure budget and
     * calls `ended(ExecutionState.Failed)` itself — rather than by hand-constructing a failed session,
     * so what is being tested is the path a device would actually take.
     *
     * The tight budget is the established idiom for this branch (`AgentExecutorTest`'s "repeated tool
     * failures fail the session rather than looping" builds the executor the same way): under
     * `RuntimeBudget.Default` one failed step is under the limit, and the A0 plan's step 1 is then
     * skipped by its precondition, so the session would end `Completed` and never exercise `Failed`.
     */
    @Test
    fun `a failed session is deleted, leaving nothing at rest`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Failed(CommandFailure.Generic)))
        val failFast = RunAgentSessionUseCase(
            AgentExecutor(registry, tools, RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 1)),
            store,
        )
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)

        val ran = failFast.run(store.active)

        assertEquals(ExecutionState.Failed, (ran as OperationResult.Success).value.state)
        assertEquals(1, tools.invocations.size)
        assertNull(store.activeOrNull)
    }

    // --- One decision, one event, through the real use case (review finding F4, 2026-08-23) -------

    /**
     * The user said no **once**. `ResolveConsentUseCase` used to record `ConsentResolved` itself and
     * `AgentExecutor.prepare` recorded it again on its way to `Cancelled`, so the persisted trace
     * carried the same refusal twice — on the one path the design calls the one a user actually feels.
     * The engine is now the single writer; this drives the whole use case to prove it end to end.
     */
    @Test
    fun `a refused consent is traced exactly once through the use case`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val run = runner(tools)
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        run.run(store.active)

        val resolved = ResolveConsentUseCase(store, run).resolve(AgentSessionId("s1"), 1, granted = false)

        val session = (resolved as OperationResult.Success).value!!
        assertEquals(ExecutionState.Cancelled, session.state)
        assertEquals(
            "one refusal must be one event: ${session.trace.events}",
            listOf(TraceEvent.ConsentResolved(1, granted = false)),
            session.trace.events.filterIsInstance<TraceEvent.ConsentResolved>(),
        )
        assertEquals("the refused step must never have run", 1, tools.invocations.size)
    }

    /** The granted half, same path — it must still produce exactly one event, before the step starts. */
    @Test
    fun `a granted consent is traced exactly once and before the step it clears`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val run = runner(tools)
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        run.run(store.active)

        val resolved = ResolveConsentUseCase(store, run).resolve(AgentSessionId("s1"), 1, granted = true)

        val events = (resolved as OperationResult.Success).value!!.trace.events
        assertEquals(
            listOf(TraceEvent.ConsentResolved(1, granted = true)),
            events.filterIsInstance<TraceEvent.ConsentResolved>(),
        )
        assertTrue(
            "consent must be traced before the step it clears: $events",
            events.indexOf(TraceEvent.ConsentResolved(1, granted = true)) <
                events.indexOf(TraceEvent.StepStarted(1)),
        )
    }

    // --- The plan is validated before it is persisted (review finding F10, 2026-08-23) ------------

    /**
     * Spec §4.2/§6.2 say the shape check runs at plan time **and** before every step; only the second
     * half existed, so `start` persisted whatever a `Planner` returned. A0's planner cannot emit a bad
     * plan — these drive the port directly, which is exactly the seam A4' binds a model planner to.
     */
    @Test
    fun `a plan whose step names an unregistered tool is not persisted`() = runTest {
        val bogus = plannerOf(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(ToolId("rm_rf_slash"), emptyMap()),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
        )

        val started = StartAgentSessionUseCase(bogus, store, ids, registry).start(goal)

        assertNull((started as OperationResult.Success).value)
        assertTrue("nothing may reach disk: ${store.saved}", store.saved.isEmpty())
    }

    /**
     * A 0-step plan stays constructible — ADR 4/4 makes it a real future shape ("a 0-step plan *is* a
     * spoken reply") — but A0 has no surface for that reply, and running one to `Completed` would
     * report success for a goal on which nothing happened and nothing was traced.
     */
    @Test
    fun `an empty plan is not persisted`() = runTest {
        val started = StartAgentSessionUseCase(plannerOf(ExecutionPlan(emptyList())), store, ids, registry)
            .start(goal)

        assertNull((started as OperationResult.Success).value)
        assertTrue(store.saved.isEmpty())
    }

    /** Non-vacuity: the same harness persists a plan that IS runnable, so the two above mean something. */
    @Test
    fun `a runnable plan handed through the same port is persisted`() = runTest {
        val good = plannerOf(
            (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan,
        )

        val started = StartAgentSessionUseCase(good, store, ids, registry).start(goal)

        assertEquals(AgentSessionId("s1"), (started as OperationResult.Success).value)
        assertEquals(1, store.saved.size)
    }

    private fun plannerOf(plan: ExecutionPlan) = object : Planner {
        override suspend fun plan(goal: AgentGoal, registry: ToolRegistry) = PlanningResult.Planned(plan)
    }
}
