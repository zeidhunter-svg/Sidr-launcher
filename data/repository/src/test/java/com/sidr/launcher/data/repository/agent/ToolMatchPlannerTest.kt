package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The planner never invokes anything; the federation just needs a worker to be constructible. */
private object NoopWorker : ToolWorker {
    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = ToolResult.Effected()
}

/**
 * A1' Task 8. **One generic arm, not a taxonomy** — every test here is about a tool the planner has
 * never heard of by name, reached through the vocabulary and the registry alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolMatchPlannerTest {

    private val planner = ToolMatchPlanner(ToolVocabulary())

    /** The production shape: the Tier-0 source seen through the federation, not directly. */
    private val registry = ToolFederation(
        listOf(ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), NoopWorker)),
    ).registry

    private fun free(text: String) = AgentGoal(text, GoalShape.Free(text))

    private fun registryWith(vararg descriptors: ToolDescriptor) = FakeToolRegistry(descriptors.toList())

    @Test
    fun `a matched tool becomes a one-step plan whose risk comes from the registry`() = runTest {
        val result = planner.plan(free("set a timer for 10 minutes"), registry)

        val plan = (result as PlanningResult.Planned).plan
        assertEquals(1, plan.steps.size)
        assertEquals(Tier0ToolIds.SET_TIMER, plan.steps[0].invocation.id)
        assertEquals(ArgSource.Literal("10 minutes"), plan.steps[0].invocation.args["duration"])
        assertEquals(ActionRiskLevel.SAFE, plan.steps[0].risk)
        assertEquals(StepRationale.GOAL_DIRECT, plan.steps[0].rationale)
        assertEquals(StepPrecondition.None, plan.steps[0].precondition)
    }

    @Test
    fun `a zero-argument tool plans a step that carries no arguments`() = runTest {
        val result = planner.plan(free("system settings"), registry)

        val plan = (result as PlanningResult.Planned).plan
        assertEquals(1, plan.steps.size)
        assertEquals(Tier0ToolIds.OPEN_SYSTEM_SETTINGS, plan.steps[0].invocation.id)
        assertEquals(emptyMap<String, ArgSource>(), plan.steps[0].invocation.args)
    }

    @Test
    fun `an unmatched goal is NoPlan, so routing falls through exactly as before`() = runTest {
        assertEquals(PlanningResult.NoPlan, planner.plan(free("what is the weather"), registry))
    }

    @Test
    fun `a tool the registry does not have is NoPlan even when the words match`() = runTest {
        assertEquals(
            PlanningResult.NoPlan,
            planner.plan(free("set a timer for 10 minutes"), FakeToolRegistry(emptyList())),
        )
    }

    /**
     * The *recognition* refusal. "set a timer" completes no trigger — the `en` forms are
     * "set a timer for" / "set timer for" / "timer for" and none of them is a prefix of it — so nothing
     * is recognised and the planner never reaches the argument question. Named for what it actually
     * exercises: the same input was in this task's brief under the argument-refusal name, where it
     * would have passed for the wrong reason.
     */
    @Test
    fun `words that complete no trigger are not recognised at all`() = runTest {
        assertEquals(null, ToolVocabulary().match("set a timer"))
        assertEquals(PlanningResult.NoPlan, planner.plan(free("set a timer"), registry))
    }

    /**
     * The *argument* refusal. "set a timer for" triggers the entry exactly and leaves nothing after it,
     * so the vocabulary declines rather than reporting a blank duration: an agent that silently starts
     * a zero-length timer is worse than one that declines. Same reasoning as
     * `InvocationValidator.resolve`'s `UNRESOLVED_ARG_SOURCE`.
     */
    @Test
    fun `a trigger with nothing after it is NoPlan, never a blank argument`() = runTest {
        assertEquals(PlanningResult.NoPlan, planner.plan(free("set a timer for"), registry))
        assertEquals(PlanningResult.NoPlan, planner.plan(free("  set a timer for   "), registry))
    }

    /**
     * The planner's own required-argument check, which the vocabulary cannot reach past: it fires when
     * the vocabulary and the tool disagree about the argument's *name*. Nothing pins those two together
     * today, so this is the branch that keeps a rename from producing a call with a missing argument.
     */
    @Test
    fun `a tool that renamed its required argument is NoPlan, not a call missing it`() = runTest {
        val renamed = registryWith(
            ToolDescriptor(
                id = Tier0ToolIds.SET_TIMER,
                level = ToolLevels.SYSTEM_INTENT,
                effect = ToolEffect.EXTERNAL,
                argSchema = listOf(ActionArg("length", description = "How long")),
                risk = ActionRiskLevel.SAFE,
                durability = ToolDurability.TRANSIENT,
            ),
        )

        assertEquals(
            PlanningResult.NoPlan,
            planner.plan(free("set a timer for 10 minutes"), renamed),
        )
    }

    /**
     * An argument the schema does not declare is dropped rather than passed:
     * `InvocationValidator.validate` rejects the whole invocation as `UNDECLARED_ARG`, and a plan that
     * cannot validate is worse than a plan without the argument.
     */
    @Test
    fun `an argument the schema does not declare is dropped rather than passed`() = runTest {
        val argless = registryWith(
            ToolDescriptor(
                id = Tier0ToolIds.SET_TIMER,
                level = ToolLevels.SYSTEM_INTENT,
                effect = ToolEffect.EXTERNAL,
                risk = ActionRiskLevel.SAFE,
                durability = ToolDurability.TRANSIENT,
            ),
        )

        val result = planner.plan(free("set a timer for 10 minutes"), argless)

        val plan = (result as PlanningResult.Planned).plan
        assertEquals(emptyMap<String, ArgSource>(), plan.steps[0].invocation.args)
    }

    /**
     * `AppNotInstalled` belongs to `TemplatePlanner`. Answering `NoPlan` rather than reading the text
     * anyway is what keeps the two planners disjoint, which is what makes `CompositePlanner`'s order a
     * tie-break rather than a priority.
     */
    @Test
    fun `the app-not-installed shape is not this planner's business`() = runTest {
        val goal = AgentGoal("set a timer for 10 minutes", GoalShape.AppNotInstalled("timer"))

        assertEquals(PlanningResult.NoPlan, planner.plan(goal, registry))
    }
}
