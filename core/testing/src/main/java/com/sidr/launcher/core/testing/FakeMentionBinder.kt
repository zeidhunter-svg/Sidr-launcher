package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.toPlanStep
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect

/**
 * A [MentionBinder] for tests that are not about binding: by default it binds every step exactly as the
 * model wrote it (no app lookup, no pick owed), through the domain's one conversion
 * ([toPlanStep], controller ruling E9-P3); [binding] scripts any other answer.
 *
 * [rebind] (A4′ phase 3b, Task 11) by default hands the remainder's steps back unchanged — no app lookup,
 * no pick owed — with every literal marked [ValueOrigin.ModelGenerated] and every step [ToolEffect.EXTERNAL]:
 * the fake has no registry, and those are the two labels that show the most on a card (over-shown, never
 * under-shown — the production rule E11-P1 leans the same way). [rebinding] scripts any other answer.
 */
class FakeMentionBinder(
    var binding: ((AgentGoal, List<ProposedStep>) -> Binding)? = null,
    var rebinding: ((AgentGoal, ExecutionPlan) -> Binding)? = null,
) : MentionBinder {

    var bindCallCount: Int = 0
        private set

    var rebindCallCount: Int = 0
        private set

    override suspend fun bind(goal: AgentGoal, steps: List<ProposedStep>): Binding {
        bindCallCount++
        return binding?.invoke(goal, steps) ?: Binding.Bound(
            Interpretation.Act(
                goal = goal,
                steps = steps.mapIndexed { index, proposed -> proposed.toPlanStep(index, proposed.args) },
                origins = steps.withIndex().associate { (index, proposed) -> index to proposed.origins },
                effects = steps.withIndex().associate { (index, proposed) -> index to proposed.effect },
                choices = emptyList(),
            ),
        )
    }

    override suspend fun rebind(goal: AgentGoal, plan: ExecutionPlan): Binding {
        rebindCallCount++
        return rebinding?.invoke(goal, plan) ?: Binding.Bound(
            Interpretation.Act(
                goal = goal,
                steps = plan.steps,
                origins = plan.steps.associate { step ->
                    step.index to step.invocation.args.filterValues { it is ArgSource.Literal }.mapValues { ValueOrigin.ModelGenerated }
                },
                effects = plan.steps.associate { it.index to ToolEffect.EXTERNAL },
                choices = emptyList(),
            ),
        )
    }
}
