package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.tool.ToolRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
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

        val result = CompositePlanner(planners).plan(goal, registry)

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

        val result = CompositePlanner(planners).plan(goal, registry)

        assertEquals(PlanningResult.NoPlan, result)
        assertEquals(listOf("first", "second"), asked)
    }
}
