package com.sidr.launcher.feature.launcher.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.sidr.launcher.core.ui.component.SidrActionGateType
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.TraceEvent
import com.sidr.launcher.feature.launcher.R

/*
 * Task 12 / A0. The seam the hard rule requires: user-facing text never originates in `:domain` and
 * never in a ViewModel, so the engine reports typed values and THIS file — the feature layer — picks
 * the string. It is also the reason `StepRationale`, `ConsentReason` and `ObservedFact` are enums
 * rather than sentences: a sentence born in the domain cannot be translated, and a sentence born in a
 * ViewModel cannot be tested against a locale.
 *
 * Every `when` here is exhaustive with no `else`, so a new domain constant fails the build at this
 * seam instead of silently rendering as a blank or as English.
 */

/**
 * What the plan is about, in the user's own words. Exhaustive over [GoalShape] on purpose: A0 has one
 * shape, and a second one must make a deliberate decision about how it reads rather than fall into a
 * default.
 *
 * The [GoalShape.Free] arm is that deliberate decision, and it is **unreachable on this surface**:
 * `TemplatePlanner` answers `NoPlan` for a free-text goal, so no Android session is ever built from
 * one, and Android's only production `GoalShape` construction site (`RouteCommandUseCase`) builds
 * [GoalShape.AppNotInstalled]. It renders the raw goal text rather than throwing because this is the
 * UI seam: a shape that should not be here must degrade to the user's own words, never to a crash.
 */
internal fun AgentGoal.subject(): String = when (val shape = shape) {
    is GoalShape.AppNotInstalled -> shape.query
    is GoalShape.Free -> shape.text
}

/** Typed provenance -> one localized line per step. [subject] is the goal the plan is serving. */
@Composable
@ReadOnlyComposable
internal fun StepRationale.label(subject: String): String = when (this) {
    StepRationale.GOAL_DIRECT -> sidrString(R.string.launcher_agent_step_launch, subject)
    StepRationale.APP_NOT_INSTALLED_FALLBACK -> sidrString(R.string.launcher_agent_step_store, subject)
}

/**
 * The title of the whole surface for the state it is in, or `null` for the one state that renders
 * nothing at all: a cancelled session hands the screen back to the ordinary command surface.
 */
@Composable
@ReadOnlyComposable
internal fun ExecutionState.title(): String? = when (this) {
    ExecutionState.Planning -> sidrString(R.string.launcher_agent_title)
    ExecutionState.Running -> sidrString(R.string.launcher_agent_running_title)
    ExecutionState.AwaitingConsent -> sidrString(R.string.launcher_agent_gate_title)
    ExecutionState.Paused -> sidrString(R.string.launcher_agent_paused_title)
    ExecutionState.Completed -> sidrString(R.string.launcher_agent_completed_title)
    ExecutionState.Failed -> sidrString(R.string.launcher_agent_failed_title)
    ExecutionState.Blocked -> sidrString(R.string.launcher_agent_blocked_title)
    ExecutionState.Cancelled -> null
}

/**
 * Why the engine stopped -> which kind of boundary the gate announces. This is a mapping of typed
 * values, NOT of copy: `SidrActionGate` renders the type as its own already-translated risk chip
 * (`ui_gate_type_*`), so the reason survives translation without this task inventing four more keys.
 *
 * It is also why the gate's type is not hardcoded to `Confirmation`: a `MISSING_PERMISSION`
 * checkpoint labelled "confirmation" would understate what is being asked for.
 */
internal fun ConsentReason.gateType(): SidrActionGateType = when (this) {
    ConsentReason.RISK_LEVEL -> SidrActionGateType.Confirmation
    ConsentReason.RISK_RAISED -> SidrActionGateType.Confirmation
    ConsentReason.MISSING_PERMISSION -> SidrActionGateType.Permission
    ConsentReason.DURABLE_EFFECT -> SidrActionGateType.ExternalHandoff
}

/**
 * Where one step actually stands — **read from what the session recorded, not from the cursor**
 * (review findings F3/F6, 2026-08-23).
 *
 * The cursor moves for three different reasons: a step ran, a step was skipped by an unsatisfied
 * precondition, and a step failed. Reading `stepIndex < cursor` collapsed all three into
 * [SidrStatus.SUCCESS], so the two shapes this engine reaches most often both lied on screen:
 *  - the app **is** installed, so step 1 is skipped on its precondition and the plan closes
 *    `Completed` — the store step showed a success marker and the store was never opened;
 *  - step 0 **fails**, which under `RuntimeBudget.Default` is under the consecutive-failure limit,
 *    so step 1 skips on its precondition and the plan again closes `Completed` — two success markers
 *    over a plan in which nothing succeeded at all.
 *
 * Everything needed to tell them apart was already in the session: the observation for an executed
 * step, and `TraceEvent.StepSkipped` for a skipped one.
 */
internal enum class AgentStepState { DONE, SKIPPED, FAILED, CURRENT, PENDING }

internal fun AgentSession.stateOf(step: PlanStep): AgentStepState = when {
    observations[step.index] is ToolResult.Failed -> AgentStepState.FAILED
    observations[step.index] != null -> AgentStepState.DONE
    trace.events.any { it is TraceEvent.StepSkipped && it.index == step.index } -> AgentStepState.SKIPPED
    step.index == cursor -> AgentStepState.CURRENT
    else -> AgentStepState.PENDING
}

/** The dot. Decorative by `R-ADL-2` — [AgentStepState.word] is what actually carries the state. */
internal fun AgentStepState.marker(): SidrStatus = when (this) {
    AgentStepState.DONE -> SidrStatus.SUCCESS
    AgentStepState.SKIPPED -> SidrStatus.INFO
    AgentStepState.FAILED -> SidrStatus.DANGER
    AgentStepState.CURRENT -> SidrStatus.ATTENTION
    AgentStepState.PENDING -> SidrStatus.INFO
}

/**
 * The state as a word, because `SidrStatusMarker`'s own contract is that the dot is decorative and
 * **the label carries meaning** (`R-ADL-2`: status is never colour alone). The label used to be the
 * step's rationale and nothing else, so done / skipped / failed / pending were distinguishable only
 * by the colour of the dot — invisible in greyscale and to TalkBack.
 */
@Composable
@ReadOnlyComposable
internal fun AgentStepState.word(): String = when (this) {
    AgentStepState.DONE -> sidrString(R.string.launcher_agent_step_state_done)
    AgentStepState.SKIPPED -> sidrString(R.string.launcher_agent_step_state_skipped)
    AgentStepState.FAILED -> sidrString(R.string.launcher_agent_step_state_failed)
    AgentStepState.CURRENT -> sidrString(R.string.launcher_agent_step_state_current)
    AgentStepState.PENDING -> sidrString(R.string.launcher_agent_step_state_pending)
}

/**
 * Every step of the plan ran, and none of them failed.
 *
 * A skipped step is a legitimate part of a `Completed` run (spec §3), but it is **not** a step that
 * ran, and «План выполнен» over a plan half of which was skipped or failed is the exact shape
 * `DOC-ILM-4` forbids — a partial result presented as success. The surface pairs this with
 * `SidrResultTone.Partial`, which is the primitive already built for saying so.
 */
internal fun AgentSession.everyStepExecuted(): Boolean =
    plan.steps.all { step ->
        val observation = observations[step.index]
        observation != null && observation !is ToolResult.Failed
    }
