package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A4′ phase 3b, Task 11 (fork 3b-A, plan §0.2 B-10): the remainder of a model plan that acted — the steps
 * after the last step whose result is `Effected` or `HandedOff`, re-indexed from 0, a reference into a
 * finished step replaced by the value that step recorded. Nothing is asked of a model, and nothing is
 * read from a registry: every case plans against an EMPTY one. Fixtures are invented (R14-43) and every
 * assertion is an equality.
 */
class RemainderPlannerTest {

    private val planner = RemainderPlanner()
    private val nothingRegistered = FakeToolRegistry(emptyList())
    private val goal = AgentGoal("fixture reading", GoalShape.Free("fixture reading"))
    private val find = ToolId("fixture_find")
    private val delete = ToolId("fixture_delete")

    private fun step(
        index: Int,
        tool: ToolId,
        args: Map<String, ArgSource>,
        risk: ActionRiskLevel = ActionRiskLevel.SAFE,
        rationale: StepRationale = StepRationale.MODEL_PROPOSED,
    ) = PlanStep(index, ToolInvocation(tool, args), risk, StepPrecondition.None, rationale)

    private suspend fun remainderOf(previous: ExecutionPlan, observations: Map<Int, ToolResult>): PlanningResult =
        planner.plan(PlanningRequest(goal, priorObservations = observations, previous = previous), nothingRegistered)

    /**
     * Step 0 acted; step 1 only LOOKED (the app was not there), so the cursor stands at 2, one past the cut:
     * the remainder starts at step 1, the first step after the last act — not at the cursor. Step 2's
     * reference into step 1, a step inside the remainder, moves with it (controller ruling E11-P2).
     */
    @Test
    fun `the remainder starts after the last step that acted`() = runTest {
        val previous = ExecutionPlan(
            listOf(
                step(0, ToolIds.LAUNCH_APP, mapOf("app" to ArgSource.Literal("net.fixture.lumora"), "query" to ArgSource.Literal("lumora"))),
                step(1, ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("тирвен"))),
                step(2, ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(1, "resolved_query")), risk = ActionRiskLevel.CONFIRM),
            ),
        )
        val observations = mapOf(
            0 to ToolResult.Effected(),
            1 to ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED, ToolOutput(mapOf("resolved_query" to "тирвен"))),
        )

        assertEquals(
            PlanningResult.Planned(
                ExecutionPlan(
                    listOf(
                        step(0, ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("тирвен"))),
                        step(1, ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query")), risk = ActionRiskLevel.CONFIRM),
                    ),
                ),
            ),
            remainderOf(previous, observations),
        )
    }

    /** B-10: the finished step is not run again, so what it recorded is the value — a literal now. */
    @Test
    fun `a reference into a finished step becomes its value`() = runTest {
        val previous = ExecutionPlan(
            listOf(
                step(0, find, mapOf("name" to ArgSource.Literal("orvelle.txt"))),
                step(1, delete, mapOf("path" to ArgSource.FromStep(0, "resolved_path")), risk = ActionRiskLevel.CONFIRM),
            ),
        )
        val observations = mapOf(0 to ToolResult.Effected(ToolOutput(mapOf("resolved_path" to "sandbox/orvelle.txt"))))

        assertEquals(
            PlanningResult.Planned(
                ExecutionPlan(listOf(step(0, delete, mapOf("path" to ArgSource.Literal("sandbox/orvelle.txt")), risk = ActionRiskLevel.CONFIRM))),
            ),
            remainderOf(previous, observations),
        )
    }

    /**
     * The finished step recorded no value under the key: the remainder cannot be written honestly — a
     * guessed or blank value would be worse than none — so there is none (`NoPlan`; on resume the stale
     * session then ends `Failed`, owner decision M1(b)).
     */
    @Test
    fun `a reference into a finished step with no value leaves no remainder`() = runTest {
        val previous = ExecutionPlan(
            listOf(
                step(0, find, mapOf("name" to ArgSource.Literal("orvelle.txt"))),
                step(1, delete, mapOf("path" to ArgSource.FromStep(0, "resolved_path")), risk = ActionRiskLevel.CONFIRM),
            ),
        )

        assertEquals(PlanningResult.NoPlan, remainderOf(previous, mapOf(0 to ToolResult.Effected(ToolOutput()))))
    }

    /** An undo's remainder would be planned from the ORIGINAL goal's words — a redo of what it undoes (§6.4). */
    @Test
    fun `an undo plan has no remainder`() = runTest {
        val unnote = ToolId("fixture_unnote")
        val undo = ExecutionPlan(
            listOf(
                step(0, unnote, mapOf("name" to ArgSource.Literal("кельмар")), rationale = StepRationale.COMPENSATION),
                step(1, unnote, mapOf("name" to ArgSource.Literal("хольдас")), rationale = StepRationale.COMPENSATION),
            ),
        )

        assertEquals(PlanningResult.NoPlan, remainderOf(undo, mapOf(0 to ToolResult.Effected())))
    }
}
