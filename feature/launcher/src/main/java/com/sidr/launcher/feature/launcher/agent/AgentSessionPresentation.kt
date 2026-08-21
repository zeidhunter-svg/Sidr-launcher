package com.sidr.launcher.feature.launcher.agent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.sidr.launcher.core.ui.component.SidrActionGateType
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.StepRationale
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
 */
internal fun AgentGoal.subject(): String = when (val shape = shape) {
    is GoalShape.AppNotInstalled -> shape.query
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
 * Where one step stands relative to the cursor. Position, not colour, is what the caller pairs this
 * with — `SidrStatusMarker` shows the dot *and* the step's own line, per `R-ADL-2`.
 */
internal fun stepStatus(stepIndex: Int, cursor: Int): SidrStatus = when {
    stepIndex < cursor -> SidrStatus.SUCCESS
    stepIndex == cursor -> SidrStatus.ATTENTION
    else -> SidrStatus.INFO
}
