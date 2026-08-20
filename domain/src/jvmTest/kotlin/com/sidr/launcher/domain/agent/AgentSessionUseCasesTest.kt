package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
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
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)

        val ran = runner(tools).run(store.active)

        assertEquals(ExecutionState.AwaitingConsent, (ran as OperationResult.Success).value.state)
        assertEquals(ExecutionState.AwaitingConsent, store.active.state)
        assertEquals(1, tools.invocations.size)
    }

    @Test
    fun `a completed session is deleted, leaving nothing at rest`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), ToolResult.Effected),
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
            listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), ToolResult.Effected),
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
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        runner(tools).run(store.active)

        CancelAgentSessionUseCase(store).cancel(AgentSessionId("s1"))

        assertNull(store.activeOrNull)
        assertEquals(1, tools.invocations.size)
    }

    @Test
    fun `a store failure is surfaced as a Failure and never thrown`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
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
            val invocations = mutableListOf<ToolInvocation>()
            override suspend fun invoke(invocation: ToolInvocation): ToolResult {
                snapshots += store.active
                invocations += invocation
                return ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)
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
        val tools = FakeToolExecutor(listOf(ToolResult.Effected))
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
    }
}
