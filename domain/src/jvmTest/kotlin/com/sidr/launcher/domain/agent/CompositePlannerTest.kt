package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1' Task 8. The composite is the seam that lets a second planner exist at all: A0 bound
 * [TemplatePlanner] alone, and A4' adds the model planner to the same list.
 *
 * Both tests read the *order of questions asked*, not the returned plan alone — "returns a plan" would
 * stay green if the composite asked every planner and kept the last answer, which is a different
 * contract from the one this class claims.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompositePlannerTest {

    private val goal = AgentGoal("x", GoalShape.Free("x"))
    private val registry = FakeToolRegistry.withA0Tools()

    private fun recording(name: String, log: MutableList<String>, result: PlanningResult) =
        object : Planner {
            override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
                log += name
                return result
            }
        }

    @Test
    fun `the composite returns the first plan and stops asking`() = runTest {
        val asked = mutableListOf<String>()
        val planners = listOf(
            recording("silent", asked, PlanningResult.NoPlan),
            recording("speaking", asked, PlanningResult.Planned(ExecutionPlan(emptyList()))),
            recording("never", asked, PlanningResult.NoPlan),
        )

        val result = CompositePlanner(planners).plan(PlanningRequest(goal), registry)

        assertEquals(true, result is PlanningResult.Planned)
        assertEquals(listOf("silent", "speaking"), asked)
    }

    @Test
    fun `when nobody plans, every planner was asked and the answer is NoPlan`() = runTest {
        val asked = mutableListOf<String>()
        val planners = listOf(
            recording("first", asked, PlanningResult.NoPlan),
            recording("second", asked, PlanningResult.NoPlan),
        )

        val result = CompositePlanner(planners).plan(PlanningRequest(goal), registry)

        assertEquals(PlanningResult.NoPlan, result)
        assertEquals(listOf("first", "second"), asked)
    }

    /**
     * A4′ phase 3b, B-6 (R14-39 closed): a planner that CLAIMED the goal and could not bind its target
     * has answered, and the answer is final. Asking on would let a later planner replace it — with a
     * plan for a goal the first planner had already read differently, or, at the end of the list, with
     * the loop's closing `NoPlan`, which is exactly the fall-through to the cloud model `Unresolved`
     * exists to end. The third planner here would PLAN, so a composite that asked on returns a
     * different answer and a longer question list, never the same one by accident.
     */
    @Test
    fun `an unresolved answer stops the composite`() = runTest {
        val asked = mutableListOf<String>()
        val unresolved = PlanningResult.Unresolved(
            UnresolvedTarget(
                goal = goal,
                pending = PlanStep(0, ToolInvocation(ToolIds.LAUNCH_APP), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT),
                arg = "app",
                labelArg = null,
                sort = ArgSorts.APP,
                words = "x",
                candidates = emptyList(),
                confirm = false,
            ),
        )
        val planners = listOf(
            recording("silent", asked, PlanningResult.NoPlan),
            recording("claiming", asked, unresolved),
            recording("never", asked, PlanningResult.Planned(ExecutionPlan(emptyList()))),
        )

        val result = CompositePlanner(planners).plan(PlanningRequest(goal), registry)

        assertEquals(unresolved, result)
        assertEquals(listOf("silent", "claiming"), asked)
    }

    /**
     * The property the new shape exists for, and the only one a test can hold today: **every planner
     * is asked the same question**. Phase 2 puts re-planning state in `PlanningRequest`
     * (spec §6.4 — "второе поле получает здесь своего потребителя"), so a composite that rebuilt
     * the request per planner, or passed `PlanningRequest(goal)` while holding a richer one, would
     * silently drop that state for every planner after the first. Today that reads as a pedantic
     * assertion; on the day re-planning lands it is the difference between a second attempt that
     * knows what the first observed and one that does not.
     */
    @Test
    fun `every planner is asked with the same request instance`() = runTest {
        val seen = mutableListOf<PlanningRequest>()
        val recording = object : Planner {
            override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
                seen += request
                return PlanningResult.NoPlan
            }
        }
        val request = PlanningRequest(
            goal = AgentGoal("зефир", GoalShape.Free("зефир")),
            priorObservations = mapOf(0 to ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)),
        )

        CompositePlanner(listOf(recording, recording, recording))
            .plan(request, FakeToolRegistry.withA0Tools())

        assertEquals(3, seen.size)
        assertTrue("a composite must not rebuild the request it was given", seen.all { it === request })
    }
}
