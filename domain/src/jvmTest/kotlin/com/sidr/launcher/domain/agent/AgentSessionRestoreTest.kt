package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Task 12 / A0. The two restart transitions on [AgentSession].
 *
 * They exist so the trace keeps its 1:1 property (`DOC-ILM-3`) across a process restart: a state
 * change with no event would leave a persisted trace reading as if the plan had simply run on. These
 * tests pin both halves — the state AND the event — and that nothing else about the session moves.
 */
class AgentSessionRestoreTest {

    private val session = AgentSession(
        id = AgentSessionId("s1"),
        goal = AgentGoal("открой убер", GoalShape.AppNotInstalled("убер")),
        plan = ExecutionPlan(
            listOf(
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("убер"))),
                    risk = ActionRiskLevel.SAFE,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.GOAL_DIRECT,
                ),
            ),
        ),
        cursor = 1,
        state = ExecutionState.Running,
        observations = emptyMap(),
        consents = emptyMap(),
        trace = ExecutionTrace(listOf(TraceEvent.PlanCreated(1))),
    )

    @Test
    fun `pausedForRestore sets Paused and records the pause`() {
        val paused = session.pausedForRestore()

        assertEquals(ExecutionState.Paused, paused.state)
        assertEquals(
            listOf(TraceEvent.PlanCreated(1), TraceEvent.SessionPaused),
            paused.trace.events,
        )
    }

    @Test
    fun `resumed sets Running and records the resume`() {
        val resumed = session.pausedForRestore().resumed()

        assertEquals(ExecutionState.Running, resumed.state)
        assertEquals(
            listOf(TraceEvent.PlanCreated(1), TraceEvent.SessionPaused, TraceEvent.SessionResumed),
            resumed.trace.events,
        )
    }

    /**
     * The pair moves the state and appends to the trace — and touches NOTHING else. The cursor in
     * particular must survive: it is where `continueSession()` picks the plan back up, and a helper
     * that quietly reset it would re-run an already-performed side effect on the next launch.
     */
    @Test
    fun `neither helper moves the cursor, the plan, the observations or the consents`() {
        val roundTripped = session.pausedForRestore().resumed()

        assertEquals(session, roundTripped.copy(trace = session.trace))
    }
}
