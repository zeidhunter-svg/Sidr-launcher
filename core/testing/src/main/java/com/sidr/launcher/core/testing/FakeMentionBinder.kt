package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.toPlanStep

/**
 * A [MentionBinder] for tests that are not about binding: by default it binds every step exactly as the
 * model wrote it (no app lookup, no pick owed), through the domain's one conversion
 * ([toPlanStep], controller ruling E9-P3); [binding] scripts any other answer.
 */
class FakeMentionBinder(var binding: ((AgentGoal, List<ProposedStep>) -> Binding)? = null) : MentionBinder {

    var bindCallCount: Int = 0
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
}
