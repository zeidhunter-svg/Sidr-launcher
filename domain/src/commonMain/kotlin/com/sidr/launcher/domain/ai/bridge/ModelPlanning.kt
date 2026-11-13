package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId

/**
 * Who said a value (spec §7.12.1, manifest §19). Today the principal is always the user; an open value
 * class so a machine principal later is a new value, not a rename across the core.
 */
@JvmInline
value class PrincipalId(val value: String) {
    companion object {
        val USER = PrincipalId("user")
    }
}

/**
 * The minimum of labels (spec §7.12.1): where a value on the card came from. **For the card's lifetime
 * only — never stored**: the plan on disk carries the bound values, as every plan does.
 */
sealed interface ValueOrigin {
    /** Words the principal typed — derivable from the command. */
    data class SaidBy(val principal: PrincipalId) : ValueOrigin

    /** The device's own determination — a package the binder resolved. */
    data object Ours : ValueOrigin

    /** Text the model produced. On an `EXTERNAL` step the card shows it verbatim (`DOC-MZN-2`). */
    data object ModelGenerated : ValueOrigin
}

/**
 * A validated reference (A4′ phase 3b, B-18): the value of an argument is the output [key] of the plan's
 * step at 0-based position [step], which is always an EARLIER step whose tool describes [key].
 */
data class StepRef(val step: Int, val key: String)

/**
 * One step the model proposed, validated (shape, membership, derivability, references) and not yet
 * bound. [args] are MODEL-facing names (an app mention under `app`); the device maps them onto the tool.
 * [refs] holds the arguments given as a reference to an earlier step's output — never also in [args],
 * and with no [origins] entry: their value is the device's, made by that earlier step.
 */
data class ProposedStep(
    val tool: ToolId,
    val args: Map<String, String>,
    val origins: Map<String, ValueOrigin>,
    val risk: ActionRiskLevel,
    val effect: ToolEffect,
    val refs: Map<String, StepRef> = emptyMap(),
)

/**
 * The model planner's CLOSED result (spec §7.12.2): five answers of the model and one outcome of the
 * port. A new variant is a compile error at `RouteCommandUseCase` step 7, where the decision belongs —
 * the lesson `PlanningResult` paid for (spec §3 0.0).
 */
sealed interface ModelPlanning {
    /**
     * The plan's steps, in order, every one validated (fork 3b-B). Never empty; at most
     * `BridgeRendering.MAX_STEPS_OFFERED` — one until Task 9 opens the cap, so `RouteCommandUseCase`
     * step 7 takes the single step.
     */
    data class Proposed(val steps: List<ProposedStep>) : ModelPlanning
    data class ToAssistant(val question: String) : ModelPlanning
    data class Navigate(val destination: SidrDestination) : ModelPlanning

    /** One verbatim question — today's single clarify, NOT a clarification protocol (spec §1, §3). */
    data class Clarify(val question: String) : ModelPlanning
    data object Declined : ModelPlanning

    /** Not an answer of the model: the port's honest "no usable answer, and why". */
    data class Unavailable(val reason: ModelUnavailableReason) : ModelPlanning
}

/**
 * The bridge's port (spec §7.12.2) — deliberately NOT `Planner`. `Planner` is asked at step 2b (above
 * the `localOnlyMode` gate) and on resume — exactly where a model may never be called (`DOC-ADL-3`).
 * This one is asked at `RouteCommandUseCase` step 7 only, below gates 3, 5 and 6.
 */
fun interface ModelPlanner {
    suspend fun plan(command: String): ModelPlanning
}
