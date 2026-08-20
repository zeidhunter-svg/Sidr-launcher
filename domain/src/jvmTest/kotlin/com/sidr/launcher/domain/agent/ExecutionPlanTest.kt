package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `PlanStep.index` is the step's stable identity — the key of `AgentSession.observations`, of
 * `consents`, and of every `TraceEvent` — and A0 additionally requires it to equal the step's position
 * in [ExecutionPlan.steps].
 *
 * That is not a tidiness rule. `AgentExecutor` assumes position ≡ index in three independent places:
 * the cursor (`prepare` reads `steps.getOrNull(session.cursor)` as a position while `perform` writes
 * `cursor = invoked.index + 1` as an index), `isSatisfied`'s `observations[index - 1]` meaning "the
 * previous step", and `checkpointFor`'s `it.index < step.index` meaning "steps that already ran". A
 * plan violating the invariant made all three quietly wrong at once: a re-review probe ran the step
 * with `index = 1` on a 2-step plan, which set `cursor = 2`, and the engine reported `Completed` with
 * the step at `index = 0` never executed and never traced — a truncation the caller would surface to
 * the user as success.
 *
 * These tests pin the fail-fast that replaces that fail-silent: the offending plan is unconstructible,
 * so the value that produced silent truncation cannot reach the engine at all.
 */
class ExecutionPlanTest {

    private fun step(index: Int) = PlanStep(
        index = index,
        invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "x")),
        risk = ActionRiskLevel.SAFE,
        precondition = StepPrecondition.None,
        rationale = StepRationale.GOAL_DIRECT,
    )

    @Test
    fun `a plan whose indices match their positions is accepted`() {
        val plan = ExecutionPlan(listOf(step(0), step(1), step(2)))

        assertEquals(listOf(0, 1, 2), plan.steps.map { it.index })
    }

    @Test
    fun `an empty plan is accepted`() {
        assertEquals(emptyList<PlanStep>(), ExecutionPlan(emptyList()).steps)
    }

    @Test
    fun `out-of-order indices are rejected`() {
        // The exact shape the re-review probe used: position 0 carries index 1.
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionPlan(listOf(step(1), step(0)))
        }
    }

    @Test
    fun `a gapped index sequence is rejected`() {
        // Position 1 carries index 2: `isSatisfied` would look up observations[1] for "the previous
        // step" and find the observation of a step that is not the previous one.
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionPlan(listOf(step(0), step(2)))
        }
    }

    @Test
    fun `duplicate indices are rejected`() {
        // Two steps sharing an index collide in `observations`, `consents`, and the trace.
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionPlan(listOf(step(0), step(0)))
        }
    }

    @Test
    fun `a plan that does not start at zero is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExecutionPlan(listOf(step(1), step(2)))
        }
    }

    @Test
    fun `copy re-checks the invariant rather than trusting the original`() {
        val valid = ExecutionPlan(listOf(step(0), step(1)))

        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(steps = listOf(step(1), step(0)))
        }
    }

    @Test
    fun `the rejection message names the offending position and index`() {
        // A bare IllegalArgumentException thrown at a restore site (A4' rehydrates a persisted plan)
        // is very hard to diagnose, so the message must carry both numbers that disagree.
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            ExecutionPlan(listOf(step(0), step(7)))
        }

        val message = thrown.message.orEmpty()
        assertTrue("message should name position 1, was: $message", message.contains("position 1"))
        assertTrue("message should name index 7, was: $message", message.contains("index 7"))
    }
}
