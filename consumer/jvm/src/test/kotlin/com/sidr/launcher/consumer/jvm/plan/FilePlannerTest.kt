package com.sidr.launcher.consumer.jvm.plan

import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePlannerTest {

    private val planner = FilePlanner()
    private val registry = SandboxToolSource()

    private fun goal(text: String) = AgentGoal(text = text, shape = GoalShape.Free(text))

    @Test
    fun `a recognised goal plans three steps with two step-to-step bindings`() = runTest {
        val planned = planner.plan(goal("remove stale.lock"), registry) as PlanningResult.Planned
        val steps = planned.plan.steps

        assertEquals(3, steps.size)
        assertEquals(
            listOf(SandboxToolIds.WORKSPACE_INFO, SandboxToolIds.FIND_FILE, SandboxToolIds.DELETE_FILE),
            steps.map { it.invocation.id },
        )

        assertTrue("step 0 takes no arguments", steps[0].invocation.args.isEmpty())

        assertEquals(
            mapOf(
                SandboxKeys.QUERY to ArgSource.Literal("stale.lock"),
                SandboxKeys.ROOT to ArgSource.FromStep(0, SandboxKeys.ROOT),
            ),
            steps[1].invocation.args,
        )

        assertEquals(
            mapOf(SandboxKeys.PATH to ArgSource.FromStep(1, SandboxKeys.RESOLVED_PATH)),
            steps[2].invocation.args,
        )
    }

    @Test
    fun `risk comes from the registry and rises only at the last step`() = runTest {
        val planned = planner.plan(goal("remove stale.lock"), registry) as PlanningResult.Planned
        assertEquals(
            listOf(ActionRiskLevel.SAFE, ActionRiskLevel.SAFE, ActionRiskLevel.DANGEROUS),
            planned.plan.steps.map { it.risk },
        )
    }

    /**
     * `StepRationale` has two values and the other one is `APP_NOT_INSTALLED_FALLBACK` — dead
     * vocabulary for any non-launcher consumer. `GOAL_DIRECT` is honest for all three steps here:
     * each directly serves the goal. Recorded as a finding against A1' (spec §11.2), not worked around.
     */
    @Test
    fun `every step is GOAL_DIRECT and unconditional`() = runTest {
        val planned = planner.plan(goal("remove stale.lock"), registry) as PlanningResult.Planned
        assertTrue(planned.plan.steps.all { it.rationale == StepRationale.GOAL_DIRECT })
        assertTrue(planned.plan.steps.all { it.precondition == StepPrecondition.None })
    }

    @Test
    fun `an unreadable goal is NoPlan, not a guess`() = runTest {
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("what is the weather"), registry))
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("remove"), registry))
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("remove   "), registry))
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("removestale.lock"), registry))
    }

    @Test
    fun `the Android goal shape is not this planner's business`() = runTest {
        val android = AgentGoal(text = "открой убер", shape = GoalShape.AppNotInstalled("убер"))
        assertEquals(PlanningResult.NoPlan, planner.plan(android, registry))
    }

    @Test
    fun `a registry missing a tool yields NoPlan rather than a half plan`() = runTest {
        val partial = object : ToolRegistry {
            private val kept = registry.all().filterNot { it.id == SandboxToolIds.DELETE_FILE }
            override fun all(): List<ToolDescriptor> = kept
            override fun find(id: ToolId): ToolDescriptor? = kept.firstOrNull { it.id == id }
        }
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("remove stale.lock"), partial))
    }
}
