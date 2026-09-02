package com.sidr.launcher.feature.launcher.agent

import androidx.annotation.StringRes
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
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
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
 * The [GoalShape.Free] arm is that deliberate decision, and since A1' Task 9 it is the **common** one
 * rather than an unreachable branch — `RouteCommandUseCase` step 2b builds a free-text goal for every
 * undecided command, `ToolMatchPlanner` plans it, and `AgentSessionMappers` persists it. (Until then
 * the arm was genuinely dead: `TemplatePlanner` answered `NoPlan` to a free-text goal and the Android
 * mapper refused to write one. Both premises are gone.) It renders the shape's own text rather than
 * throwing for the reason it always did — this is the UI seam, and a shape it cannot place must
 * degrade to the user's own words, never to a crash.
 */
internal fun AgentGoal.subject(): String = when (val shape = shape) {
    is GoalShape.AppNotInstalled -> shape.query
    is GoalShape.Free -> shape.text
}

/*
 * Task 10 / A1'. `Tier0ToolIds` lives in `:data:repository`, which `:feature:launcher` must not depend
 * on (no feature -> data edge), so its two ids are written out here as literals. Nothing pins these
 * two strings to their declaration — the same shape of duplication `ToolIds`' own KDoc records for
 * `SystemIntentToolSource`, and the same one `CLAUDE.md` names for `FakeToolRegistry.withA0Tools()`.
 * What keeps it honest is that drift is **fail-soft**: an id this file does not recognise falls
 * through to [R.string.launcher_agent_step_generic], so the surface says less, never something false.
 * `launch_app` / `play_store_search` need no literal — they are `ToolIds` in `:domain`.
 */
private const val TIER0_SET_TIMER = "set_timer"
private const val TIER0_OPEN_SYSTEM_SETTINGS = "open_system_settings"

/**
 * `ToolId -> string resource`. This is the mapping [com.sidr.launcher.domain.tool.ToolDescriptor]'s own
 * KDoc prescribes ("carries no user-facing copy: the surface maps `id` to a string resource in the
 * feature layer"), and it is what lets a one-step plan for any tool render correctly without
 * `StepRationale` growing a value per tool (spec §10.3).
 *
 * It replaced `StepRationale.label`, which keyed the line on WHY the step is in the plan. That was
 * indistinguishable from keying it on WHAT the step does only while the single A0 plan existed: since
 * Task 9 a matched tool is a one-step `GOAL_DIRECT` plan, and `GOAL_DIRECT -> "Open %1$s"` would have
 * rendered a timer as "Open set a timer for 10 minutes". The A0 plan is unaffected — `launch_app` maps
 * to the resource `GOAL_DIRECT` mapped to and `play_store_search` to the one
 * `APP_NOT_INSTALLED_FALLBACK` mapped to, which `AgentSessionSurfaceProvenanceTest` pins by rendering.
 *
 * An unregistered id falls back to the generic step line rather than throwing: this is the UI seam,
 * and the hard rule forbids exceptions here.
 */
@StringRes
internal fun toolLabelFor(id: ToolId): Int = when (id.value) {
    ToolIds.LAUNCH_APP.value -> R.string.launcher_agent_step_launch
    ToolIds.PLAY_STORE_SEARCH.value -> R.string.launcher_agent_step_store
    TIER0_SET_TIMER -> R.string.launcher_agent_step_timer
    TIER0_OPEN_SYSTEM_SETTINGS -> R.string.launcher_agent_step_settings
    else -> R.string.launcher_agent_step_generic
}

/**
 * One localized line per step: the tool's own sentence, filled with the value that sentence is about.
 *
 * [subject] is the goal the plan is serving, which is the right filler only while the step's argument
 * IS the goal — true for every A0 step and false the moment a tool takes an argument of its own. A
 * free-text goal carries the whole typed command (`RouteCommandUseCase` builds `GoalShape.Free` from
 * it), so a timer step filled with [subject] reads "Set a timer for set a timer for 10 minutes".
 *
 * So a step with **exactly one** literal argument speaks about that argument instead. `singleOrNull`
 * rather than `first`: with one literal there is no choice to get wrong, and with two there is no
 * principled way to pick, so the line falls back to the goal — saying less rather than guessing. A
 * bound argument ([ArgSource.FromStep]) is not a literal and is deliberately not read here: the plan
 * holds a reference, not a value, and only the engine ever resolves one.
 *
 * Both A0 steps are unaffected, which is why the rendering baseline holds: step 0's single literal IS
 * the goal query, and step 1 binds rather than repeats, so it has no literal at all.
 */
@Composable
@ReadOnlyComposable
internal fun PlanStep.line(subject: String): String =
    sidrString(toolLabelFor(invocation.id), invocation.literalSubject() ?: subject)

private fun ToolInvocation.literalSubject(): String? =
    args.values.filterIsInstance<ArgSource.Literal>().singleOrNull()?.value

/**
 * The two fields of a [com.sidr.launcher.domain.tool.ToolDescriptor] the surface is allowed to see.
 *
 * The registry is a domain port and the ViewModel is the layer that holds it; this type is what the
 * ViewModel hands down, so the surface renders provenance without ever performing a registry lookup
 * and without the descriptor's risk, schemas or permission gate coming along for the ride.
 */
internal data class StepProvenance(val level: ToolLevel, val effect: ToolEffect)

/**
 * Provenance for one step, or `null` when there is nothing to disclose.
 *
 * `DOC-ILM-2` is about the `EXTERNAL` case, and the interesting half of it is an `EXTERNAL` tool that
 * is also `SAFE`: a `CONFIRM` tool is already stopped by the consent gate, so for a `SAFE` one this
 * line is the only thing telling the user the effect left the launcher. Both tier-0 tools are exactly
 * that pair.
 *
 * **The raw [ToolLevel.value] never reaches a sink.** [ToolLevel] is an open value class, so an
 * unmapped level is reachable by construction and falls back to a generic label — the alternative is
 * printing a domain identifier at a user, which is precisely what `DomainIdentifierLeakGuardTest` was
 * written after.
 *
 * The unit tests over this function hold the mapping; they do **not** close `DOC-ILM-2` — a mapping
 * nothing calls stays green forever (spec §6.3). That is `AgentSessionSurfaceProvenanceTest`'s job.
 */
@StringRes
internal fun provenanceLabelFor(level: ToolLevel, effect: ToolEffect): Int? {
    if (effect != ToolEffect.EXTERNAL) return null
    return when (level.value) {
        ToolLevels.IN_APP.value -> R.string.launcher_tool_level_in_app
        ToolLevels.SYSTEM_INTENT.value -> R.string.launcher_tool_level_system_intent
        else -> R.string.launcher_tool_level_unknown
    }
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
