package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.text.TextFolding
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolRegistry

sealed interface Verdict {
    data class Valid(val planning: ModelPlanning) : Verdict
    data class Rejected(val error: BridgeError) : Verdict
}

/**
 * The bridge's first line (spec §7.12.2). Three checks, all before `startPlanned`:
 *  - **shape** — a known kind with what it needs; a plan's step names a tool and gives every argument
 *    it requires, and no argument it does not take;
 *  - **membership in the offering** — not in the federation: `set_app_alias` and `shortcut:…` are
 *    registered and `SAFE`, the engine would not stop them, and `B13` would rest on one render filter;
 *  - **derivability** — an argument the description marks as a mention holds the user's own words,
 *    compared after the platform's [TextFolding] (exact after folding, phase 3b B-17).
 * The verdict tells `UNKNOWN_TOOL` from `NOT_OFFERED` for the device's own use; the retry text that goes
 * out deliberately does NOT (`BridgeRendering.retryNote`), since a differing note would tell the model
 * whether an id it proposed is registered on this phone. Binding happens after this, and its outcome
 * never returns here.
 */
object BridgeValidator {

    fun validate(
        answer: RawAnswer,
        offering: BridgeOffering,
        registry: ToolRegistry,
        command: String,
        folding: TextFolding = PlainTextFolding,
    ): Verdict =
        when (answer.kind?.trim()) {
            BridgeRendering.KIND_PLAN -> validatePlan(answer.step, offering, registry, command, folding)
            BridgeRendering.KIND_ASSISTANT -> answer.question.nonBlank()
                ?.let { Verdict.Valid(ModelPlanning.ToAssistant(it)) }
                ?: Verdict.Rejected(BridgeError.SHAPE)
            BridgeRendering.KIND_NAVIGATE -> SidrDestination.entries.firstOrNull { it.wire == answer.destination?.trim() }
                ?.let { Verdict.Valid(ModelPlanning.Navigate(it)) }
                ?: Verdict.Rejected(BridgeError.SHAPE)
            BridgeRendering.KIND_CLARIFY -> answer.question.nonBlank()
                ?.let { Verdict.Valid(ModelPlanning.Clarify(it)) }
                ?: Verdict.Rejected(BridgeError.SHAPE)
            BridgeRendering.KIND_DECLINE -> Verdict.Valid(ModelPlanning.Declined)
            else -> Verdict.Rejected(BridgeError.SHAPE)
        }

    private fun validatePlan(
        step: RawStep?,
        offering: BridgeOffering,
        registry: ToolRegistry,
        command: String,
        folding: TextFolding,
    ): Verdict {
        val toolText = step?.tool?.trim().orEmpty()
        if (step == null || toolText.isEmpty()) return Verdict.Rejected(BridgeError.SHAPE)
        val id = ToolId(toolText)
        val offered = offering.find(id)
            ?: return if (registry.find(id) == null) Verdict.Rejected(BridgeError.UNKNOWN_TOOL) else Verdict.Rejected(BridgeError.NOT_OFFERED)

        val declared = offered.description.args.map { it.name }.toSet()
        if (step.args.any { (name, value) -> name !in declared && !value.isNullOrBlank() }) {
            return Verdict.Rejected(BridgeError.SHAPE)
        }

        val values = linkedMapOf<String, String>()
        val origins = linkedMapOf<String, ValueOrigin>()
        for (arg in offered.description.args) {
            val value = step.args[arg.name]?.trim()
            if (value.isNullOrEmpty()) {
                if (arg.required) return Verdict.Rejected(BridgeError.SHAPE)
                continue
            }
            if (arg.mention && !Derivability.isDerivable(value, command, folding)) {
                return Verdict.Rejected(BridgeError.UNDERIVABLE_MENTION)
            }
            values[arg.name] = value
            origins[arg.name] = Derivability.originOf(value, command, folding)
        }
        return Verdict.Valid(
            ModelPlanning.Proposed(ProposedStep(id, values, origins, offered.descriptor.risk, offered.descriptor.effect)),
        )
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
