package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolInvocation

/**
 * What the card shows BEFORE anything runs (spec §7.12.3 condition 1). Transient: held by the command
 * session in memory, never written anywhere (plan §0.2 B-12). Only the user's «Выполнить» turns it into
 * a session, through `StartAgentSessionUseCase.startPlanned` and the unchanged engine — except a learned
 * command at step 2c (D3 as amended, B-15), which starts through the same engine with every consent gate.
 */
sealed interface Interpretation {

    /**
     * The model's steps over the offering, in plan order (fork 3b-B), every app mention bound on this
     * device — or awaiting the user's pick: [choices] holds every pick still owed, in step order, and only
     * its HEAD is answered ([choose]). [origins] and [effects] are keyed on the step's position in [steps];
     * each step's origins on the TOOL's argument names after binding.
     */
    data class Act(
        val goal: AgentGoal,
        val steps: List<PlanStep>,
        val origins: Map<Int, Map<String, ValueOrigin>>,
        val effects: Map<Int, ToolEffect>,
        val choices: List<PendingChoice>,
    ) : Interpretation

    /** A screen of Sidr itself (plan §0.2 B-9) — opened only by «Выполнить», like everything else here. */
    data class Navigate(val destination: SidrDestination) : Interpretation
}

/**
 * The model's word for an app names more than one installed app (spec §7.12.3 condition 2), or names one
 * only as a word FORM (phase 3b, fork 3b-G: [confirm] = `true`, «Вы имели в виду…?») — or, for a tool whose
 * act cannot be undone (`uninstall_app`), names one exactly only after folding (owner decision 2026-11-17;
 * [confirm] = `true` too). A pick writes the
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

/**
 * A pick owed on the step at plan position [step] (A4′ phase 3b, Task 9). Each launch step's pick carries
 * its own learning token in [AppChoice.learning] (B-19, the binder's half).
 */
data class PendingChoice(val step: Int, val choice: AppChoice)

/**
 * The pick. It answers the HEAD of [Interpretation.Act.choices] only — picks are owed one at a time, in
 * step order. [packageName] must be one of the head's candidates; anything else changes nothing (`null`).
 * The package goes into that step's argument, that step's origin for it becomes [ValueOrigin.Ours], and
 * the head is taken off; the next owed pick, if any, is the new head.
 */
fun Interpretation.Act.choose(packageName: String): Interpretation.Act? {
    val head = choices.firstOrNull() ?: return null
    val pending = head.choice
    if (pending.candidates.none { it.packageName == packageName }) return null
    return copy(
        steps = steps.map { step ->
            if (step.index != head.step) step
            else step.copy(invocation = step.invocation.copy(args = step.invocation.args + (pending.arg to ArgSource.Literal(packageName))))
        },
        origins = origins + (head.step to (origins[head.step].orEmpty() + (pending.arg to ValueOrigin.Ours))),
        choices = choices.drop(1),
    )
}

/** The plan «Выполнить» starts — only once no pick is owed on any step. */
fun Interpretation.Act.plan(): ExecutionPlan? = if (choices.isNotEmpty()) null else ExecutionPlan(steps)

/**
 * The ONE conversion of a validated model step into the plan's step (controller ruling E9-P3) — the
 * device's binder and the tests' fake both build their steps here. [index] is the step's position in the
 * plan (`ExecutionPlan` pins `index == position`); [words] are the device's bound literal values for the
 * tool's arguments (a package where an app was bound, the user's words where the tool shows them), each
 * an [ArgSource.Literal]; every reference in [ProposedStep.refs] becomes [ArgSource.FromStep] on the
 * earlier step's output, which the engine resolves at run time (B-18). The validator keeps a reference out
 * of [ProposedStep.args], and no binder writes one of its names into [words].
 */
fun ProposedStep.toPlanStep(index: Int, words: Map<String, String>): PlanStep = PlanStep(
    index = index,
    invocation = ToolInvocation(
        tool,
        words.mapValues { (_, value) -> ArgSource.Literal(value) } +
            refs.mapValues { (_, ref) -> ArgSource.FromStep(stepIndex = ref.step, key = ref.key) },
    ),
    risk = risk,
    precondition = StepPrecondition.None,
    rationale = StepRationale.MODEL_PROPOSED,
)

/**
 * Device-side binding of the model's words (spec §7.12.1, D1): a mention becomes a package on this
 * phone, or the user is asked; a value the tool's argument sort reads (a duration, a clock time, an
 * address) is checked — or, for an address, normalised — before any card (A4′ phase 3b, Task 6). **Its
 * outcome never goes back to the model** — it goes to the user (a pick, an honest "no such app", «не
 * понял»); `RouteCommandUseCase` calls it after the model has answered.
 *
 * Since phase 3b Task 9 it binds the WHOLE reading — [steps] in plan order, each as one step always was —
 * and the first step it cannot bind answers for all of them: there is no card for part of a reading.
 */
interface MentionBinder {
    suspend fun bind(goal: AgentGoal, steps: List<ProposedStep>): Binding

    /**
     * The REMAINDER of a model plan that acted, bound again on this device before its card (A4′ phase 3b,
     * Task 11; fork 3b-A, plan §0.2 B-10) — after a pause («Продолжить», `Resumption.NeedsCard`) or a
     * failure («Повторить остаток»), when the apps the plan named may be gone or renamed. [plan] is
     * `ResumeAgentSessionUseCase.remainder`'s: steps re-indexed from 0, each argument a literal value or
     * a reference to an earlier step of [plan]. Every app argument is resolved AGAIN — from the user's
     * words the step kept for it where it kept them, otherwise by checking that its package is still
     * there — through the same search, learned choices and picks as [bind]; never the stale package
     * kept. Each step's effect is the CURRENT registry's, and a step whose tool is no longer registered
     * answers [Binding.Unavailable] for the whole remainder (controller ruling E11-P1). No model is
     * asked; the answer goes to the user, exactly as [bind]'s.
     */
    suspend fun rebind(goal: AgentGoal, plan: ExecutionPlan): Binding
}

/**
 * What the device made of the model's step. [NotFound] is about an APP mention («нет такого
 * приложения»); [NotUnderstood] is about a VALUE whose sort could not read [NotUnderstood.words] (phase
 * 3b, Task 6) — the words the model wrote for that one argument, said to the user and never sent back;
 * [Unavailable] — the device could not answer: its app list or alias store could not be read, a value
 * sort had no usable resolver, an app a remainder named could not be checked again, or a remainder's tool
 * is no longer registered (phase 3b, Tasks 6 and 11). Never a reason to ask the model; nothing runs.
 */
sealed interface Binding {
    data class Bound(val interpretation: Interpretation.Act) : Binding
    data class NotFound(val mention: String) : Binding
    data class NotUnderstood(val words: String) : Binding
    data object Unavailable : Binding
}
