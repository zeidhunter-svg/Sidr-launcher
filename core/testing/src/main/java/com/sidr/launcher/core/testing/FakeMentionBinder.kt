package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolInvocation

/**
 * A [MentionBinder] for tests that are not about binding: by default it binds the step exactly as the
 * model wrote it (no app lookup); [binding] scripts any other answer.
 */
class FakeMentionBinder(var binding: ((AgentGoal, ProposedStep) -> Binding)? = null) : MentionBinder {

    var bindCallCount: Int = 0
        private set

    override suspend fun bind(goal: AgentGoal, proposed: ProposedStep): Binding {
        bindCallCount++
        return binding?.invoke(goal, proposed) ?: Binding.Bound(
            Interpretation.Act(
                goal = goal,
                step = PlanStep(
                    index = 0,
                    invocation = ToolInvocation(proposed.tool, proposed.args.mapValues { (_, value) -> ArgSource.Literal(value) }),
                    risk = proposed.risk,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.MODEL_PROPOSED,
                ),
                origins = proposed.origins,
                effect = proposed.effect,
                choice = null,
            ),
        )
    }
}
