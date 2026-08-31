package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.Planner
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/**
 * **One generic arm, not a taxonomy** (spec §8.4). A recognised tool becomes a one-step plan, whatever
 * the tool is — so `A1"`'s twelve tools cost twelve vocabulary entries and **zero** planner branches.
 * The rejected alternative was one `GoalShape` value and one arm per tool, which is linear per tool
 * and contradicts in code the claim this whole block exists to establish.
 *
 * `GoalShape` gains nothing: this plans the `Free` value A0.5 already added, which until now answered
 * `NoPlan` on Android. `StepRationale` gains nothing either — a one-step plan is `GOAL_DIRECT`, and
 * the surface names the tool rather than assuming a launch.
 *
 * **Risk comes from the registry, never from here.** The planner records what the registry declares at
 * plan time, and `AgentExecutor` gates on `maxOf(step.risk, registry.find(...)?.risk ?: DANGEROUS)`
 * (A0 finding F2), so a tool that becomes riskier between planning and execution still stops.
 *
 * **It recognises; it does not validate the value.** [ToolVocabulary] hands the remainder over
 * **normalized** — lower-cased and whitespace-collapsed, never the raw span; see its KDoc for the
 * free-text limitation that implies — and this planner only checks that a required argument is
 * *present*. Whether "10 minutes" is
 * a readable duration is the worker's question, and a tool whose value cannot be read fails closed
 * there (`Tier0IntentToolWorker.parseSeconds`). The consequence, named rather than implied: a goal like
 * "timer for the meeting" produces a plan that fails at the worker instead of falling through to the
 * model. Closing that needs an argument type richer than `ArgType.STRING` — the A0.5 finding spec
 * §10.4 already records — not a second parser here.
 */
class ToolMatchPlanner @Inject constructor(
    private val vocabulary: ToolVocabulary,
) : Planner {

    override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
        // Exhaustive with no `else`: a third GoalShape must make a deliberate decision here.
        val text = when (val shape = goal.shape) {
            is GoalShape.Free -> shape.text
            is GoalShape.AppNotInstalled -> return PlanningResult.NoPlan
        }

        val match = vocabulary.match(text) ?: return PlanningResult.NoPlan
        val descriptor = registry.find(match.id) ?: return PlanningResult.NoPlan

        // Every required argument must have a non-blank value. Nothing is defaulted or substituted:
        // an agent that silently runs a half-filled tool is worse than one that declines. The
        // vocabulary already refuses a trigger with nothing after it, so what this catches is the
        // vocabulary and the tool disagreeing about the argument's NAME — nothing pins those together.
        val required = descriptor.argSchema.filter { it.required }.map { it.name }
        if (required.any { match.args[it].isNullOrBlank() }) return PlanningResult.NoPlan

        // An argument the schema does not declare is dropped rather than passed: `InvocationValidator`
        // would reject the whole invocation as UNDECLARED_ARG, and a plan that cannot validate is
        // worse than no plan.
        val args = match.args
            .filterKeys { key -> descriptor.argSchema.any { it.name == key } }
            .mapValues { (_, value) -> ArgSource.Literal(value) }

        return PlanningResult.Planned(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(descriptor.id, args),
                        risk = descriptor.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
        )
    }
}
