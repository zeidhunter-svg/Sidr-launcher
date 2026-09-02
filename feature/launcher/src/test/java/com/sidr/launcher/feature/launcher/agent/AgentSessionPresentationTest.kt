package com.sidr.launcher.feature.launcher.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.feature.launcher.R
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What the plan list is allowed to claim** (review findings F3/F6, 2026-08-23).
 *
 * The step markers used to be derived from the cursor alone — `stepIndex < cursor` meant SUCCESS — and
 * the cursor moves for three different reasons: a step ran, a step was skipped by an unsatisfied
 * precondition, and a step failed. Both shapes this engine reaches most often therefore lied:
 *
 *  - the app **is** installed, so step 1 skips and the plan closes `Completed` — the store step showed
 *    a success marker over a store that was never opened (this is spec §12.8, the acceptance item);
 *  - step 0 **fails**, which under `RuntimeBudget.Default` is under the consecutive-failure limit, so
 *    step 1 skips on its precondition and the plan again closes `Completed` — two success markers over
 *    a plan in which nothing succeeded.
 *
 * These are pure functions over the session, so they are tested without Compose. The localized status
 * word each state maps to is `@Composable` and lives beside them; what matters here is that the five
 * states are distinguishable at all, which is what `R-ADL-2` needs and what a colour alone cannot give.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionPresentationTest {

    private val goal = AgentGoal("открой убер", GoalShape.AppNotInstalled("убер"))

    private suspend fun plan(): ExecutionPlan {
        val planned = TemplatePlanner().plan(goal, FakeToolRegistry.withA0Tools())
        check(planned is PlanningResult.Planned)
        return planned.plan
    }

    private suspend fun session(
        cursor: Int,
        state: ExecutionState = ExecutionState.Completed,
        observations: Map<Int, ToolResult> = emptyMap(),
        trace: List<TraceEvent> = emptyList(),
    ) = AgentSession(
        id = AgentSessionId("s1"),
        goal = goal,
        plan = plan(),
        cursor = cursor,
        state = state,
        observations = observations,
        consents = emptyMap(),
        trace = ExecutionTrace(trace),
    )

    private val launched = ToolResult.Effected(ToolOutput(mapOf("resolved_query" to "убер")))
    private val notInstalled = ToolResult.Observed(
        ObservedFact.APP_NOT_INSTALLED,
        ToolOutput(mapOf("resolved_query" to "убер")),
    )

    /** Spec §12.8's own shape: the app was there, so the store step was never needed. */
    @Test
    fun `a step skipped by its precondition is skipped, not done`() = runTest {
        val s = session(
            cursor = 2,
            observations = mapOf(0 to launched),
            trace = listOf(
                TraceEvent.StepSkipped(1, StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED)),
            ),
        )

        assertEquals(AgentStepState.DONE, s.stateOf(s.plan.steps[0]))
        assertEquals(AgentStepState.SKIPPED, s.stateOf(s.plan.steps[1]))
        assertEquals(SidrStatus.SUCCESS, s.stateOf(s.plan.steps[0]).marker())
        assertEquals(SidrStatus.INFO, s.stateOf(s.plan.steps[1]).marker())
    }

    /** The failure shape: one step failed, the next skipped, and the session still says `Completed`. */
    @Test
    fun `a failed step is failed, and the plan that carried it is not whole`() = runTest {
        val s = session(
            cursor = 2,
            observations = mapOf(0 to ToolResult.Failed(CommandFailure.Generic)),
            trace = listOf(
                TraceEvent.StepSkipped(1, StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED)),
            ),
        )

        assertEquals(AgentStepState.FAILED, s.stateOf(s.plan.steps[0]))
        assertEquals(SidrStatus.DANGER, s.stateOf(s.plan.steps[0]).marker())
        assertEquals(AgentStepState.SKIPPED, s.stateOf(s.plan.steps[1]))
        assertFalse("nothing ran successfully — this is not a whole plan", s.everyStepExecuted())
    }

    @Test
    fun `the step at the cursor is current and the ones after it are pending`() = runTest {
        val s = session(cursor = 1, state = ExecutionState.AwaitingConsent, observations = mapOf(0 to notInstalled))

        assertEquals(AgentStepState.DONE, s.stateOf(s.plan.steps[0]))
        assertEquals(AgentStepState.CURRENT, s.stateOf(s.plan.steps[1]))
        assertEquals(SidrStatus.ATTENTION, s.stateOf(s.plan.steps[1]).marker())
    }

    @Test
    fun `a plan not started yet marks its first step current and nothing done`() = runTest {
        val s = session(cursor = 0, state = ExecutionState.Running)

        assertEquals(AgentStepState.CURRENT, s.stateOf(s.plan.steps[0]))
        assertEquals(AgentStepState.PENDING, s.stateOf(s.plan.steps[1]))
        assertFalse(s.everyStepExecuted())
    }

    /**
     * The non-vacuity half: the run in which every step really did execute must still read as whole,
     * or the `Partial` tone would simply have replaced the `Completed` one everywhere.
     */
    @Test
    fun `a plan whose every step ran is whole`() = runTest {
        val s = session(
            cursor = 2,
            observations = mapOf(0 to notInstalled, 1 to ToolResult.Effected()),
        )

        assertTrue(s.everyStepExecuted())
        assertEquals(AgentStepState.DONE, s.stateOf(s.plan.steps[0]))
        assertEquals(AgentStepState.DONE, s.stateOf(s.plan.steps[1]))
    }

    /**
     * Every state must reach a distinguishable marker OR be told apart by its word — five states over
     * four markers, so `SKIPPED` and `PENDING` deliberately share `INFO` and are separated by the
     * label. This pins that no two states collapse to the *same pair*, which is the property
     * `R-ADL-2` actually needs.
     */
    @Test
    fun `no two step states are indistinguishable`() {
        val markers = AgentStepState.entries.associateWith { it.marker() }

        assertEquals(
            "SKIPPED and PENDING share a marker on purpose; anything else sharing one is a collision: $markers",
            4,
            markers.values.toSet().size,
        )
        assertEquals(markers[AgentStepState.SKIPPED], markers[AgentStepState.PENDING])
    }

    /**
     * The presentation's `Free` arm (fix round 2, 2026-08-23 — it shipped held by nothing and
     * survived a mutation that rendered a literal in place of the user's words).
     *
     * Unreachable on Android is not a reason an arm cannot be asserted: the shape is constructed
     * directly here. The goal text and the shape text are deliberately **different** so the assertion
     * pins which of the two the arm reads — it must be the shape's own text.
     */
    @Test
    fun `a free-text goal reads as the user's own words`() {
        val free = AgentGoal(
            text = "сделай конспект встречи",
            shape = GoalShape.Free("сделай конспект"),
        )

        assertEquals("сделай конспект", free.subject())
    }

    // ── Task 10 / A1': the step line names its TOOL, and an EXTERNAL tool discloses where it went ──
    //
    // These three are unit tests over the two mapping functions. They hold the mapping — including the
    // open-`ToolLevel` fallback that keeps a raw domain identifier away from a user — and they are NOT
    // what closes `DOC-ILM-2`: a mapping nothing calls stays green forever. The rule is closed by
    // [AgentSessionSurfaceProvenanceTest], which renders the real surface and goes red when the
    // rendering is removed. Keep both; they answer different questions.

    /**
     * `set_timer` and `open_system_settings` are `Tier0ToolIds` in `:data:repository`, which
     * `:feature:launcher` must not depend on (no feature -> data edge), so the ids are written out as
     * literals here exactly as they are in the production mapping. Drift between the two degrades to
     * the generic step line; it cannot render the wrong sentence.
     */
    private val setTimer = ToolId("set_timer")

    @Test
    fun `a step line names the tool rather than assuming a launch`() {
        val label = toolLabelFor(setTimer)

        // Before A1' every GOAL_DIRECT step rendered `launcher_agent_step_launch` ("Open %1$s"), so a
        // timer step would have read "Open set a timer for 10 minutes". The rationale says WHY the step
        // is in the plan; the tool says WHAT it does, and only the second belongs in this line.
        assertEquals(R.string.launcher_agent_step_timer, label)
    }

    @Test
    fun `an EXTERNAL tool shows provenance and a LOCAL one shows none`() {
        assertEquals(
            R.string.launcher_tool_level_system_intent,
            provenanceLabelFor(ToolLevels.SYSTEM_INTENT, ToolEffect.EXTERNAL),
        )
        assertNull(provenanceLabelFor(ToolLevels.SANDBOX, ToolEffect.LOCAL))
    }

    @Test
    fun `a level with no string resource falls back to a generic label, never to the raw value`() {
        // ToolLevel is an OPEN value class, so an unknown level is reachable by construction — that is
        // the whole point of the type. The surface must never print "mcp" at a user: invisible in
        // English, untranslated in ru/tr, which is exactly the bug DomainIdentifierLeakGuardTest exists
        // for. Fail closed to a generic label.
        assertEquals(
            R.string.launcher_tool_level_unknown,
            provenanceLabelFor(ToolLevel("mcp"), ToolEffect.EXTERNAL),
        )
    }
}
