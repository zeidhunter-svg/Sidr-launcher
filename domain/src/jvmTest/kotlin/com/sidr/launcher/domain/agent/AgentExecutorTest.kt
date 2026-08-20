package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The A0 engine. `advance` performs exactly one transition, which is what lets cancellation be checked
 * *between* transitions and lets every test drive the machine without coroutine timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentExecutorTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val budget = RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2)

    private fun planForMissingApp(query: String) = ExecutionPlan(
        listOf(
            PlanStep(
                index = 0,
                invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to query)),
                risk = ActionRiskLevel.SAFE,
                precondition = StepPrecondition.None,
                rationale = StepRationale.GOAL_DIRECT,
            ),
            PlanStep(
                index = 1,
                invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to query)),
                risk = ActionRiskLevel.CONFIRM,
                precondition = StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
                rationale = StepRationale.APP_NOT_INSTALLED_FALLBACK,
            ),
        ),
    )

    private fun session(query: String = "убер") = AgentSession(
        id = AgentSessionId("s1"),
        goal = AgentGoal(text = "открой $query", shape = GoalShape.AppNotInstalled(query)),
        plan = planForMissingApp(query),
        cursor = 0,
        state = ExecutionState.Running,
        observations = emptyMap(),
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    @Test
    fun `a SAFE first step runs without consent and records the observation`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val after = AgentExecutor(registry, tools, budget).advance(session())

        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
        assertEquals(1, after.cursor)
        assertEquals(ExecutionState.Running, after.state)
        assertEquals(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), after.observations[0])
    }

    @Test
    fun `the risk transition stops the loop before the second tool is ever called`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val executor = AgentExecutor(registry, tools, budget)

        val afterFirst = executor.advance(session())
        val afterSecond = executor.advance(afterFirst)

        assertEquals(ExecutionState.AwaitingConsent, afterSecond.state)
        assertEquals(1, tools.invocations.size)          // the store was NOT called
        assertTrue(
            afterSecond.trace.events.any {
                it is TraceEvent.ConsentRequested && it.reason == ConsentReason.RISK_LEVEL
            },
        )
    }

    @Test
    fun `granted consent runs the second step and completes`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), ToolResult.Effected),
        )
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s)
        s = executor.advance(s.copy(state = ExecutionState.Running, consents = mapOf(1 to true)))
        s = executor.advance(s)

        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.PLAY_STORE_SEARCH), tools.invocations.map { it.id })
        assertEquals(ExecutionState.Completed, s.state)
    }

    @Test
    fun `denied consent cancels and never invokes the tool`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s.copy(consents = mapOf(1 to false)))

        assertEquals(ExecutionState.Cancelled, s.state)
        assertEquals(1, tools.invocations.size)
    }

    @Test
    fun `an installed app skips the fallback step and still completes`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected))
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())      // step 0 launches the app
        s = executor.advance(s)                  // step 1's precondition is unsatisfied -> skipped
        s = executor.advance(s)                  // end of plan -> Completed

        assertEquals(1, tools.invocations.size)
        assertEquals(ExecutionState.Completed, s.state)
        assertTrue(s.trace.events.any { it is TraceEvent.StepSkipped && it.index == 1 })
    }

    @Test
    fun `an invocation the validator rejects fails the session and is traced`() = runTest {
        val tools = FakeToolExecutor(emptyList())
        val broken = session().let {
            it.copy(
                plan = ExecutionPlan(
                    listOf(
                        it.plan.steps[0].copy(
                            invocation = ToolInvocation(ToolId("not_registered"), mapOf("query" to "x")),
                        ),
                    ),
                ),
            )
        }

        val after = AgentExecutor(registry, tools, budget).advance(broken)

        assertEquals(ExecutionState.Failed, after.state)
        assertEquals(0, tools.invocations.size)
        assertTrue(after.trace.events.any { it is TraceEvent.StepRejected })
    }

    @Test
    fun `exhausting the step budget blocks instead of stopping silently`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected, ToolResult.Effected))
        val tight = AgentExecutor(registry, tools, RuntimeBudget(maxSteps = 1, maxConsecutiveFailures = 2))

        var s = tight.advance(session())
        s = tight.advance(s.copy(consents = mapOf(1 to true)))

        assertEquals(ExecutionState.Blocked, s.state)
    }

    @Test
    fun `repeated tool failures fail the session rather than looping`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Failed(CommandFailure.Generic), ToolResult.Failed(CommandFailure.Generic)),
        )
        val executor = AgentExecutor(registry, tools, RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 1))

        val after = executor.advance(session())

        assertEquals(ExecutionState.Failed, after.state)
    }

    @Test
    fun `advance on a terminal or awaiting session is a no-op`() = runTest {
        val tools = FakeToolExecutor(emptyList())
        val executor = AgentExecutor(registry, tools, budget)

        for (state in listOf(
            ExecutionState.AwaitingConsent, ExecutionState.Paused, ExecutionState.Completed,
            ExecutionState.Cancelled, ExecutionState.Failed, ExecutionState.Blocked,
        )) {
            val frozen = session().copy(state = state)
            assertEquals(frozen, executor.advance(frozen))
        }
        assertEquals(0, tools.invocations.size)
    }

    @Test
    fun `every executed step carries both a ToolInvoked and a ToolObserved event`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), ToolResult.Effected),
        )
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s)
        s = executor.advance(s.copy(state = ExecutionState.Running, consents = mapOf(1 to true)))

        val invoked = s.trace.events.filterIsInstance<TraceEvent.ToolInvoked>().map { it.index }
        val observed = s.trace.events.filterIsInstance<TraceEvent.ToolObserved>().map { it.index }
        assertEquals(invoked, observed)
        assertEquals(listOf(0, 1), invoked)
    }

    @Test
    fun `prepare records ToolInvoked without calling the tool`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val executor = AgentExecutor(registry, tools, budget)

        val prepared = executor.prepare(session())

        assertEquals(0, tools.invocations.size)
        assertTrue(prepared.trace.events.last() is TraceEvent.ToolInvoked)
    }

    @Test
    fun `perform on a session that is not mid-step is a no-op`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val executor = AgentExecutor(registry, tools, budget)

        val untouched = session()
        val after = executor.perform(untouched)

        assertEquals(0, tools.invocations.size)
        assertEquals(untouched, after)
    }

    // --- Fix round 1 regression + coverage tests -----------------------------------------------

    @Test
    fun `prepare does not advance the cursor`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val executor = AgentExecutor(registry, tools, budget)

        val prepared = executor.prepare(session())

        assertEquals(0, prepared.cursor)
    }

    @Test
    fun `perform does not re-invoke a step that already has a matching ToolObserved`() = runTest {
        // Last event IS ToolInvoked(0), but an earlier ToolObserved(0) already exists in the trace —
        // this is exactly the shape the "alreadyObserved" half of the mid-step predicate must catch;
        // without it, this reads as mid-step and the tool fires a second time.
        val tools = FakeToolExecutor(emptyList())
        val executor = AgentExecutor(registry, tools, budget)

        val corrupted = session().copy(
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
                    TraceEvent.ToolObserved(0, ToolResult.Effected),
                    TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
                ),
            ),
        )

        val after = executor.perform(corrupted)

        assertEquals(0, tools.invocations.size)
        assertEquals(corrupted, after)
    }

    @Test
    fun `advance never invokes the tool for a Cancelled or Paused mid-step session`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected))
        val executor = AgentExecutor(registry, tools, budget)
        val midStepTrace = ExecutionTrace(
            listOf(TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP)),
        )

        for (state in listOf(ExecutionState.Cancelled, ExecutionState.Paused)) {
            val frozen = session().copy(state = state, trace = midStepTrace)
            assertEquals(frozen, executor.advance(frozen))
        }
        assertEquals(0, tools.invocations.size)
    }

    @Test
    fun `perform resolves the step by PlanStep index, not list position`() = runTest {
        // Positions are shuffled relative to `.index`: position 0 carries index 1 (SAFE, LAUNCH_APP),
        // position 1 carries index 0 (CONFIRM, PLAY_STORE_SEARCH). A position-based lookup keyed off the
        // trace's recorded index would grab the CONFIRM step and run it with no consent checkpoint ever
        // evaluated for it. The index-based lookup must invoke only the step `prepare` actually cleared.
        val shuffled = ExecutionPlan(
            listOf(
                PlanStep(
                    index = 1,
                    invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "x")),
                    risk = ActionRiskLevel.SAFE,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.GOAL_DIRECT,
                ),
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to "x")),
                    risk = ActionRiskLevel.CONFIRM,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.APP_NOT_INSTALLED_FALLBACK,
                ),
            ),
        )
        val tools = FakeToolExecutor(listOf(ToolResult.Effected))
        val executor = AgentExecutor(registry, tools, budget)

        val after = executor.advance(session().copy(plan = shuffled))

        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
        assertTrue(after.trace.events.none { it is TraceEvent.ConsentRequested })
    }

    @Test
    fun `perform refuses to invoke when the resolved step's tool does not match the trace`() = runTest {
        // Step 0's real invocation is LAUNCH_APP; a corrupted trace claims PLAY_STORE_SEARCH was the one
        // invoked for it. The id-match assertion must refuse to invoke rather than run either tool.
        val tools = FakeToolExecutor(listOf(ToolResult.Effected))
        val executor = AgentExecutor(registry, tools, budget)

        val tampered = session().copy(
            trace = ExecutionTrace(
                listOf(TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.PLAY_STORE_SEARCH)),
            ),
        )

        val after = executor.perform(tampered)

        assertEquals(0, tools.invocations.size)
        assertEquals(tampered, after)
    }

    @Test
    fun `resuming through advance after persisting the prepared step does not duplicate ToolInvoked`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val executor = AgentExecutor(registry, tools, budget)

        val prepared = executor.prepare(session())    // simulates: persisted right after prepare
        val resumed = executor.advance(prepared)       // simulates: process restarts, resumes via advance()

        val invoked = resumed.trace.events.filterIsInstance<TraceEvent.ToolInvoked>()
        assertEquals(1, invoked.size)
        assertEquals(1, tools.invocations.size)
    }
}
