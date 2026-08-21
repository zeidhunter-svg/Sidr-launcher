package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.InvocationCheck
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A0's planner is the seed of the learned-plan cache (rule 2 of the new rule): it replays an
 * already-understood goal **shape** deterministically and offline. The model planner is A4'.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TemplatePlannerTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val goal = AgentGoal(text = "открой убер", shape = GoalShape.AppNotInstalled("убер"))

    @Test
    fun `a missing app plans launch then store, in that order`() = runTest {
        val result = TemplatePlanner().plan(goal, registry)

        val plan = (result as PlanningResult.Planned).plan
        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.PLAY_STORE_SEARCH), plan.steps.map { it.invocation.id })
        assertEquals(listOf(0, 1), plan.steps.map { it.index })
    }

    @Test
    fun `the goal's query is a literal on the first step only`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        assertEquals(mapOf("query" to ArgSource.Literal("убер")), plan.steps[0].invocation.args)
    }

    /**
     * F6. The store step does not carry a second copy of the goal text — it **binds** to what the
     * launch step reported. Before the amendment the planner wrote the same literal into both steps,
     * so the two could silently disagree about what was being searched for.
     */
    @Test
    fun `the second step binds to what the first step resolved, rather than repeating the literal`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        assertEquals(
            mapOf("query" to ArgSource.FromStep(0, "resolved_query")),
            plan.steps[1].invocation.args,
        )
    }

    /** The binding the planner writes must survive the validator it will meet on every step. */
    @Test
    fun `the plan the planner produces validates against the registry it planned over`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        plan.steps.forEach { step ->
            val preceding = plan.steps.filter { it.index < step.index }.map { it.invocation.id }
            assertEquals(
                "step ${step.index} does not validate",
                InvocationCheck.Valid,
                InvocationValidator.validate(step.invocation, preceding, registry),
            )
        }
    }

    @Test
    fun `the second step is gated on the first having observed that the app is missing`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        assertEquals(StepPrecondition.None, plan.steps[0].precondition)
        assertEquals(
            StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
            plan.steps[1].precondition,
        )
    }

    @Test
    fun `risk is copied from the registry, never invented by the planner`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        assertEquals(ActionRiskLevel.SAFE, plan.steps[0].risk)
        assertEquals(ActionRiskLevel.CONFIRM, plan.steps[1].risk)
    }

    @Test
    fun `each step carries typed provenance rather than a sentence`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        assertEquals(StepRationale.GOAL_DIRECT, plan.steps[0].rationale)
        assertEquals(StepRationale.APP_NOT_INSTALLED_FALLBACK, plan.steps[1].rationale)
    }

    @Test
    fun `an incomplete registry yields NoPlan instead of a half plan`() = runTest {
        val launchOnly = FakeToolRegistry(
            FakeToolRegistry.withA0Tools().all().filter { it.id == ToolIds.LAUNCH_APP },
        )

        assertEquals(PlanningResult.NoPlan, TemplatePlanner().plan(goal, launchOnly))
    }

    @Test
    fun `a blank query yields NoPlan`() = runTest {
        val blank = AgentGoal(text = "открой", shape = GoalShape.AppNotInstalled("   "))

        assertEquals(PlanningResult.NoPlan, TemplatePlanner().plan(blank, registry))
    }
}
