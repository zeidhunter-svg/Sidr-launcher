package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect

/**
 * What the card shows BEFORE anything runs (spec §7.12.3 condition 1). Transient: held by the command
 * session in memory, never written anywhere (plan §0.2 B-12). Only the user's «Выполнить» turns it into
 * a session, through `StartAgentSessionUseCase.startPlanned` and the unchanged engine.
 */
sealed interface Interpretation {

    /**
     * One step over the offering, its app mention bound on this device — or awaiting the user's pick
     * ([choice] non-null). [origins] are keyed on the TOOL's argument names after binding.
     */
    data class Act(
        val goal: AgentGoal,
        val step: PlanStep,
        val origins: Map<String, ValueOrigin>,
        val effect: ToolEffect,
        val choice: AppChoice?,
    ) : Interpretation

    /** A screen of Sidr itself (plan §0.2 B-9) — opened only by «Выполнить», like everything else here. */
    data class Navigate(val destination: SidrDestination) : Interpretation
}

/**
 * The model's word for an app names more than one installed app (spec §7.12.3 condition 2), or names one
 * only as a word FORM (phase 3b, fork 3b-G: [confirm] = `true`, «Вы имели в виду…?»). A pick writes the
 * package into [arg] — it binds; it launches nothing. A form is never bound without this pick. [learning]
 * is the learned-choice token for `launch_app` only (the same act as a launch); the command session
 * records the choice at «Выполнить», through the one gated record call
 * (`LauncherAppLaunch.recordChoiceIfPending`) — so a confirmed form is learned like any other choice.
 */
data class AppChoice(
    val arg: String,
    val mention: String,
    val candidates: List<InstalledApp>,
    val learning: ResolutionLearningToken?,
    val confirm: Boolean = false,
)

/** The pick. [packageName] must be one of the candidates; anything else changes nothing (`null`). */
fun Interpretation.Act.choose(packageName: String): Interpretation.Act? {
    val pending = choice ?: return null
    if (pending.candidates.none { it.packageName == packageName }) return null
    return copy(
        step = step.copy(
            invocation = step.invocation.copy(args = step.invocation.args + (pending.arg to ArgSource.Literal(packageName))),
        ),
        origins = origins + (pending.arg to ValueOrigin.Ours),
        choice = null,
    )
}

/** The plan «Выполнить» starts — only once nothing is left to choose. One step in 3a (plan §0.2 B-1). */
fun Interpretation.Act.plan(): ExecutionPlan? = if (choice != null) null else ExecutionPlan(listOf(step))

/**
 * Device-side binding of the model's words (spec §7.12.1, D1): a mention becomes a package on this
 * phone, or the user is asked. **Its outcome never goes back to the model** — it goes to the user (a
 * pick, an honest "no such app"); `RouteCommandUseCase` calls it after the model has answered.
 */
interface MentionBinder {
    suspend fun bind(goal: AgentGoal, proposed: ProposedStep): Binding
}

sealed interface Binding {
    data class Bound(val interpretation: Interpretation.Act) : Binding
    data class NotFound(val mention: String) : Binding
    data object Unavailable : Binding
}
