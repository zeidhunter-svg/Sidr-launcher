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
 * Every `when` here that dispatches on a **closed sum** is exhaustive with no `else`, so adding a value
 * to that sum breaks this build rather than silently rendering a blank. Three functions are outside
 * that rule, and each for a stated reason — the count is deliberate, because the previous wording named
 * two and `stateOf` made it false (final whole-branch review, finding 5):
 *  - [toolLabelFor] and [provenanceLabelFor] key on [ToolId]/[ToolLevel]'s open `String` value rather
 *    than a closed sum, so a new domain constant cannot be caught at compile time and the `else` arm is
 *    load-bearing: an unrecognised value falls through to a generic/unknown resource (see each
 *    function's own KDoc), never to a build failure or a blank line.
 *  - [stateOf] is two halves since A4′ phase 0, and only the second is outside the rule. A step with a
 *    **recorded observation** is a dispatch on a sum — `ToolResult.stepState()`, exhaustive over
 *    [ToolResult] with no `else`, so a fifth `ToolResult` value breaks this build. Only a step with
 *    **no record** reaches the subject-less `when` that remains: it reads the trace, the cursor and the
 *    session's own state, it is not a dispatch on a sum, so the language requires its `else` — and
 *    that arm reads [AgentStepState.WAITING]: a step at the cursor of a session that is neither
 *    running nor over. See that function's own KDoc for why its branches are ordered as they are.
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
 * on (no feature -> data edge), so its ids are written out here as literals — the same shape of
 * duplication `ToolIds`' own KDoc records for `SystemIntentToolSource`. **Deliberately not counted:**
 * this comment read "**four** of them since A1″ Phase 3a" and was false from the first tool of phase
 * 3b onward. A count in prose beside a list that grows is a claim nobody re-reads (R14-41); the
 * authoritative set is the `toolLabelFor` arms below, and `DoctrineGuardTest`'s literal scan is what
 * holds them against the registry.
 *
 * **Corrected 2026-09-19 (fix round 2, finding 3).** This comment used to say that nothing pinned these
 * strings to their declaration and that drift was merely *fail-soft*. That is no longer true and had
 * already stopped being true when `uninstall_app` landed: `DoctrineGuardTest > every tool in the
 * production federation has a non-generic label on the surface` textually scans **this file** for each
 * registered id's literal and goes RED on drift, so a renamed id is a build failure rather than a
 * quietly generic line. The fail-soft fallback below is still the runtime behaviour — an unrecognised id
 * reaches [R.string.launcher_agent_step_generic], so the surface says less and never something false —
 * but it is now the second line of defence, not the only one.
 * `launch_app` / `play_store_search` need no literal — they are `ToolIds` in `:domain`.
 */
private const val TIER0_SET_TIMER = "set_timer"
private const val TIER0_OPEN_SYSTEM_SETTINGS = "open_system_settings"
private const val TIER0_SET_ALARM = "set_alarm"

/**
 * Task 7. A **string literal**, like its three siblings above and for the same reason: the real
 * constant is `Tier0ToolIds.UNINSTALL_APP` in `:data:repository`, and `:feature:launcher` has no edge
 * to that module. `DoctrineGuardTest` is what holds the two spellings together.
 */
private const val TIER0_UNINSTALL_APP = "uninstall_app"

/**
 * A1″ Phase 3b, Task 2 (Slice A). More string literals, same reason and the same idiom as those above:
 * the real constants are `Tier0ToolIds.*` in `:data:repository`, and `:feature:launcher` has no edge
 * to that module. `DoctrineGuardTest` holds the two spellings together.
 */
private const val TIER0_SHOW_ALARMS = "show_alarms"
private const val TIER0_OPEN_CAMERA = "open_camera"
private const val TIER0_OPEN_WIFI_SETTINGS = "open_wifi_settings"
private const val TIER0_OPEN_BLUETOOTH_SETTINGS = "open_bluetooth_settings"

/**
 * A1″ Phase 3b, Task 3 (Slice B). More string literals, same reason and the same idiom as those above:
 * the real constants are `Tier0ToolIds.*` in `:data:repository`, and `:feature:launcher` has no edge
 * to that module. `DoctrineGuardTest` holds the two spellings together.
 */
private const val TIER0_OPEN_BATTERY_SETTINGS = "open_battery_settings"
private const val TIER0_OPEN_DATA_USAGE_SETTINGS = "open_data_usage_settings"
private const val TIER0_OPEN_DISPLAY_SETTINGS = "open_display_settings"
private const val TIER0_OPEN_SOUND_SETTINGS = "open_sound_settings"

/**
 * A1″ Phase 3b, Task 4 (Slice C). The last three string literals of the eleven, same reason and the
 * same idiom as the eight above: the real constants are `Tier0ToolIds.*` in `:data:repository`, and
 * `:feature:launcher` has no edge to that module. `DoctrineGuardTest` holds the two spellings
 * together, over a scan that strips comments — so only the declarations below satisfy it.
 */
private const val TIER0_OPEN_LOCATION_SETTINGS = "open_location_settings"
private const val TIER0_OPEN_NOTIFICATION_SETTINGS = "open_notification_settings"
private const val TIER0_OPEN_APP_INFO = "open_app_info"

/**
 * Task 9, A1″ Phase 3a. Three more string literals, same reason as the Tier-0 ones above: the real
 * constants are `MemoryToolIds.*` in `:data:repository`'s `agent/memory` package, and `:feature:launcher`
 * has no edge to that module. `DoctrineGuardTest` holds the two spellings together the same way it does
 * for the Tier-0 four.
 */
private const val MEMORY_SET_APP_ALIAS = "set_app_alias"
private const val MEMORY_FORGET_APP_ALIAS = "forget_app_alias"
private const val MEMORY_FORGET_LEARNED_CHOICE = "forget_learned_choice"

/**
 * Task 11 (A1″ Phase 3a). Argument NAMES read by [line]'s two-argument branches — never by position and
 * never by "the single literal" (controller rulings R14-18/R14-19). `Tier0IntentToolSource`'s
 * `uninstall_app` declares `app` (the resolved package) and `app_label` (optional — see that
 * descriptor's KDoc for why); `MemoryToolSource`'s `set_app_alias` declares all three. Written out as
 * literals for the same reason the ids above are: the real `ActionArg` names live in
 * `:data:repository`, which this module has no edge to.
 */
private const val APP_ARG = "app"
private const val APP_LABEL_ARG = "app_label"
private const val PHRASE_ARG = "phrase"

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
    TIER0_SET_ALARM -> R.string.launcher_agent_step_alarm
    TIER0_UNINSTALL_APP -> R.string.launcher_agent_step_uninstall
    MEMORY_SET_APP_ALIAS -> R.string.launcher_agent_step_set_alias
    MEMORY_FORGET_APP_ALIAS -> R.string.launcher_agent_step_forget_alias
    MEMORY_FORGET_LEARNED_CHOICE -> R.string.launcher_agent_step_forget_choice
    TIER0_SHOW_ALARMS -> R.string.launcher_agent_step_show_alarms
    TIER0_OPEN_CAMERA -> R.string.launcher_agent_step_open_camera
    TIER0_OPEN_WIFI_SETTINGS -> R.string.launcher_agent_step_open_wifi_settings
    TIER0_OPEN_BLUETOOTH_SETTINGS -> R.string.launcher_agent_step_open_bluetooth_settings
    TIER0_OPEN_BATTERY_SETTINGS -> R.string.launcher_agent_step_open_battery_settings
    TIER0_OPEN_DATA_USAGE_SETTINGS -> R.string.launcher_agent_step_open_data_usage_settings
    TIER0_OPEN_DISPLAY_SETTINGS -> R.string.launcher_agent_step_open_display_settings
    TIER0_OPEN_SOUND_SETTINGS -> R.string.launcher_agent_step_open_sound_settings
    TIER0_OPEN_LOCATION_SETTINGS -> R.string.launcher_agent_step_open_location_settings
    TIER0_OPEN_NOTIFICATION_SETTINGS -> R.string.launcher_agent_step_open_notification_settings
    // The only one of the eleven whose resource takes a placeholder: `open_app_info` carries exactly
    // one literal argument, so `line()`'s single-literal path fills it — with the RESOLVED package,
    // because resolution happens above the consent gate. See that descriptor's KDoc: for a `SAFE`
    // tool that is a legibility limit, not a safety one, and it is not softened by adding `app_label`.
    TIER0_OPEN_APP_INFO -> R.string.launcher_agent_step_open_app_info
    else -> R.string.launcher_agent_step_generic
}

/**
 * One dynamic tool's name, kept in the two halves it was published in: [qualifier] is the app that
 * published the shortcut, [name] is what the shortcut does.
 *
 * **They are not pre-joined, and that is the hard rule rather than a style.** The join is punctuation
 * between two nouns — copy — and copy is chosen by the feature layer from a resource, never
 * concatenated by a ViewModel or by `:data:repository`. See [line], which passes both halves to
 * `launcher_agent_step_shortcut` as two arguments.
 *
 * The two strings themselves are **not** copy: they were authored by another app, and the measured
 * device shows them already localized and mixed (`ru` and `tr` labels side by side in one list —
 * `docs/superpowers/plans/2026-09-12-a1-device-measurements.md`, row 11). They do not pass through
 * `sidrString` and they are not ours to translate.
 */
data class DynamicToolLabel(val qualifier: String, val name: String)

/**
 * Port: the tools whose names are **data**.
 *
 * It is declared here, in `:feature:launcher`, and **not** consumed from `:data:repository`'s
 * `DynamicToolNames` — there is no `feature -> data` edge (`:feature:launcher` depends on `:domain`,
 * `:core:ui` and `:core:common` only), so the ViewModel cannot hold that port. `:app` is the one module
 * that sees both sides, so the projection between them lives there — `AgentProvidesModule
 * .provideDynamicToolLabels`, held by `DynamicToolLabelWiringTest`.
 *
 * This is the first port in this file that needs such a hop. [StepProvenance] below is the same shape
 * of feature-layer mirror and needs none, because the ViewModel fills it from a `:domain` port it *can*
 * hold.
 *
 * A function rather than a value: unlike the registry A0 and A1′ wired, this set **moves while the
 * process lives** — an app installed or removed changes it — so a snapshot taken once at graph
 * construction would be wrong for the rest of the process.
 */
fun interface DynamicToolLabels {
    fun current(): Map<ToolId, DynamicToolLabel>
}

/**
 * One localized line per step: the tool's own sentence, filled with the value that sentence is about —
 * or, for a tool whose name is **data**, that name.
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
 *
 * **[dynamicLabels] is empty for every authored tool** (A1″), so A0's and A1′'s lines are byte-for-byte
 * what they were. A shortcut has no string resource to map to, and [toolLabelFor] would fall through to
 * the generic line forever — a tool the user can reach and cannot identify. What IS ours is the sentence
 * around the name, which is why the resource takes both halves as arguments rather than this function
 * joining them.
 *
 * **Two tools are read BY ARGUMENT NAME instead of falling through to [subject]** (owner condition 2 for
 * `uninstall_app`, controller rulings R14-18/R14-19, Task 11). `uninstall_app`'s target is the product of
 * a **fuzzy** resolution (`AppTargetResolver`, above the consent checkpoint), so the consent card must
 * show BOTH the label the user said and the package that will actually be removed — the human has to be
 * able to check them before tapping Confirm. The single-literal fallback below cannot do this: a step
 * with two or three literals fails `singleOrNull()` and the line falls back to the raw goal text, which
 * names neither. `set_app_alias` binds three literals (`app`, `app_label`, `phrase`) for the identical
 * reason and would fall through the same way.
 *
 * Both branches key the tool id against a **string literal** (`TIER0_UNINSTALL_APP` /
 * `MEMORY_SET_APP_ALIAS` above) — never against `Tier0ToolIds` / `MemoryToolIds`, which live in
 * `:data:repository` and which this module has no edge to — and read `invocation.args` by the
 * argument's declared **name** (`APP_ARG` / `APP_LABEL_ARG` / `PHRASE_ARG`), never by position and never
 * by a literal *count*: after Task 6b `set_app_alias` carries three literals and `uninstall_app` two, so
 * a count check (`literals.size == 2`) cannot tell them apart, and reading positionally risks printing
 * the package where the label belongs — the exact defect owner condition 2 was raised about.
 *
 * `set_app_alias`'s line names the phrase the user coined for the name they actually said —
 * `app_label` ("telegram") if it is bound, `app` (the resolved package) only if some future producer
 * omits it — because `app_label` is what the user typed and `Call "some.arbitrary.package" "телега"`
 * would be a worse sentence than naming what they said. `uninstall_app`'s line always leads with the
 * label (what the user said) and follows with the package in parentheses (what will actually be
 * removed), matching the order `launcher_agent_step_uninstall`'s two placeholders are declared in.
 *
 * **Both branches require every one of their own arguments before returning early**, so an
 * incompletely-bound step (a future producer that leaves `app_label` unset for `uninstall_app`, say)
 * falls through to the SAME single-literal path every other tool uses — the goal text, not a crash from
 * calling a two-placeholder resource with one argument. `ToolMatchPlanner` always binds `app_label`
 * whenever a descriptor declares it and `app` resolves (see its own KDoc), so this fallback is not
 * reachable via the one producer of these invocations today; it exists so a future one degrades to
 * "says less" rather than to an unhandled `MissingFormatArgumentException`.
 *
 * `literalSubject()`'s `singleOrNull()` fallback is **UNCHANGED** below and still governs every other
 * tool. CLAUDE.md's named limitation (5) is therefore closed **for these two tools by name**, not in
 * general.
 */
@Composable
@ReadOnlyComposable
internal fun PlanStep.line(subject: String, dynamicLabels: Map<ToolId, DynamicToolLabel>): String {
    val dynamic = dynamicLabels[invocation.id]
    if (dynamic != null) {
        return sidrString(R.string.launcher_agent_step_shortcut, dynamic.qualifier, dynamic.name)
    }

    fun literal(name: String): String? = (invocation.args[name] as? ArgSource.Literal)?.value

    when (invocation.id.value) {
        MEMORY_SET_APP_ALIAS -> {
            val app = literal(APP_LABEL_ARG) ?: literal(APP_ARG)
            val phrase = literal(PHRASE_ARG)
            if (app != null && phrase != null) {
                return sidrString(R.string.launcher_agent_step_set_alias, app, phrase)
            }
        }
        TIER0_UNINSTALL_APP -> {
            // Owner condition 2: the card names BOTH the label and the package, because resolution is
            // fuzzy and the user must see what will actually be removed before tapping Confirm.
            val pkg = literal(APP_ARG)
            val label = literal(APP_LABEL_ARG)
            if (pkg != null && label != null) {
                return sidrString(R.string.launcher_agent_step_uninstall, label, pkg)
            }
        }
    }

    return sidrString(toolLabelFor(invocation.id), invocation.literalSubject() ?: subject)
}

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
 *
 * **`ToolLevels.LAUNCHER_MEMORY` is mapped for completeness, not because it is ever reached.** Every
 * `launcher_memory` tool is [ToolEffect.LOCAL] (see that level's own KDoc — the effect never leaves the
 * device), and the guard clause above returns `null` before this `when` runs for any `LOCAL` tool. A
 * memory step therefore shows **no** provenance chip, which is the truthful line: claiming a boundary
 * crossing that did not happen would be `DOC-ILM-2` lying in the opposite direction. The arm exists so
 * the mapping stays total over every declared [ToolLevels] value rather than silently omitting one.
 */
@StringRes
internal fun provenanceLabelFor(level: ToolLevel, effect: ToolEffect): Int? {
    if (effect != ToolEffect.EXTERNAL) return null
    return when (level.value) {
        ToolLevels.IN_APP.value -> R.string.launcher_tool_level_in_app
        ToolLevels.SYSTEM_INTENT.value -> R.string.launcher_tool_level_system_intent
        ToolLevels.APP_SHORTCUT.value -> R.string.launcher_tool_level_app_shortcut
        ToolLevels.LAUNCHER_MEMORY.value -> R.string.launcher_tool_level_launcher_memory
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
internal enum class AgentStepState { DONE, OBSERVED, HANDED_OFF, SKIPPED, FAILED, CURRENT, WAITING, PENDING }

/**
 * **The observation decides, exhaustively; only when there is none does the session decide.**
 *
 * `when (this)` over [ToolResult] with no `else` is the point of the split: this function had a
 * `!= null -> DONE` catch-all, and that catch-all is precisely how `Observed` came to be drawn as
 * «выполнено». A fifth [ToolResult] value is now a compile error here instead of a fifth silent
 * success marker.
 */
private fun ToolResult.stepState(): AgentStepState = when (this) {
    is ToolResult.Effected -> AgentStepState.DONE
    is ToolResult.Observed -> AgentStepState.OBSERVED
    is ToolResult.HandedOff -> AgentStepState.HANDED_OFF
    is ToolResult.Failed -> AgentStepState.FAILED
}

/**
 * Where one step actually stands — read from what the session recorded, and, for a step with no
 * record yet, **from the session's own state** (A4' phase 0, spec §3 0.2(1б)(1в)).
 *
 * The 2026-08-23 round corrected the first half of this function (a step's marker had been derived
 * from the cursor, collapsing ran / skipped / failed into SUCCESS) and left the second half
 * uncorrected in three ways, all of which this rule closes at once:
 *  - `Observed` had **no branch at all** and fell into `!= null -> DONE`, so the most common shape
 *    A0 produces — "the app is not installed" — was drawn as «выполнено» (D1);
 *  - `step.index == cursor -> CURRENT` never consulted `state`, so a step the engine had stopped
 *    on for **consent** was drawn as «выполняется» beneath the button asking to continue (D2);
 *  - the same line drew the cursor step of a **terminal** session as «выполняется», because
 *    `AgentExecutor.perform` advances the cursor before `ended(...)` (found 2026-09-22, named in no
 *    document before this one).
 *
 * The order is load-bearing and is not alphabetical: a recorded observation outranks any state, a
 * skipped step outranks the cursor, and only a step that is *at* the cursor is allowed to ask what
 * the session is doing.
 */
internal fun AgentSession.stateOf(step: PlanStep): AgentStepState =
    observations[step.index]?.stepState()
        ?: when {
            trace.events.any { it is TraceEvent.StepSkipped && it.index == step.index } -> AgentStepState.SKIPPED
            step.index != cursor -> AgentStepState.PENDING
            state == ExecutionState.Running -> AgentStepState.CURRENT
            state.isTerminal -> AgentStepState.PENDING
            else -> AgentStepState.WAITING
        }

/** The dot. Decorative by `R-ADL-2` — [AgentStepState.word] is what actually carries the state. */
internal fun AgentStepState.marker(): SidrStatus = when (this) {
    AgentStepState.DONE -> SidrStatus.SUCCESS
    AgentStepState.OBSERVED -> SidrStatus.INFO
    AgentStepState.HANDED_OFF -> SidrStatus.ATTENTION
    AgentStepState.SKIPPED -> SidrStatus.INFO
    AgentStepState.FAILED -> SidrStatus.DANGER
    AgentStepState.CURRENT -> SidrStatus.ATTENTION
    AgentStepState.WAITING -> SidrStatus.ATTENTION
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
    AgentStepState.OBSERVED -> sidrString(R.string.launcher_agent_step_state_observed)
    AgentStepState.HANDED_OFF -> sidrString(R.string.launcher_agent_step_state_handed_off)
    AgentStepState.SKIPPED -> sidrString(R.string.launcher_agent_step_state_skipped)
    AgentStepState.FAILED -> sidrString(R.string.launcher_agent_step_state_failed)
    AgentStepState.CURRENT -> sidrString(R.string.launcher_agent_step_state_current)
    AgentStepState.WAITING -> sidrString(R.string.launcher_agent_step_state_waiting)
    AgentStepState.PENDING -> sidrString(R.string.launcher_agent_step_state_pending)
}

/**
 * Every step of the plan ran, and none of them failed.
 *
 * A skipped step is a legitimate part of a `Completed` run (spec §3), but it is **not** a step that
 * ran, and «План выполнен» over a plan half of which was skipped or failed is the exact shape
 * `DOC-ILM-4` forbids — a partial result presented as success. The surface pairs this with
 * `SidrResultTone.Partial`, which is the primitive already built for saying so.
 *
 * A [ToolResult.HandedOff] step **counts as executed here**, on purpose (A4′ phase 0): it did run.
 * Whether its outcome is in sight is a different question, asked by [anyStepHandedOff], and the one
 * place the two are joined is the `Completed` branch of `AgentSessionSurface`.
 */
internal fun AgentSession.everyStepExecuted(): Boolean =
    plan.steps.all { step ->
        val observation = observations[step.index]
        observation != null && observation !is ToolResult.Failed
    }

/**
 * A step whose act left the launcher and whose outcome the launcher cannot see.
 *
 * **Deliberately a second predicate rather than a clause inside [everyStepExecuted]**, because the
 * two ask different questions and folding them would make one name false: a handed-off step *did*
 * execute. What it did not do is finish where we can see it. Keeping them apart also keeps each
 * falsifiable on its own — one mutation per property, which is the rule this block pays for.
 */
internal fun AgentSession.anyStepHandedOff(): Boolean =
    plan.steps.any { observations[it.index] is ToolResult.HandedOff }
