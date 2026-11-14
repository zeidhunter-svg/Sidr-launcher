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
 * The bridge's first line (spec §7.12.2). Its checks, all before `startPlanned`:
 *  - **shape** — a known kind with what it needs; a plan has from one to
 *    [BridgeRendering.MAX_STEPS_OFFERED] steps — the engine's own cap, `RuntimeBudget.Default.maxSteps`,
 *    which the instruction and the schema also say (phase 3b Task 9) — and EVERY step names a tool and
 *    gives every argument it requires, and no argument it does not take;
 *  - **membership in the offering** — not in the federation: `set_app_alias` and `shortcut:…` are
 *    registered and `SAFE`, the engine would not stop them, and `B13` would rest on one render filter;
 *  - **derivability** — an argument the description marks as a mention, given as words, holds the user's
 *    own words, compared after the platform's [TextFolding] (exact after folding, phase 3b B-17);
 *  - **references** (phase 3b, B-18) — judged after every step, over the plan as the model wrote it: a
 *    reference stands only on an argument whose tool's description accepts one, names an EARLIER step,
 *    and names an output that step's tool describes. Any other is `BridgeError.SHAPE` (B-9: no fifth code).
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
            BridgeRendering.KIND_PLAN -> validatePlan(answer.steps, offering, registry, command, folding)
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
        steps: List<RawStep>?,
        offering: BridgeOffering,
        registry: ToolRegistry,
        command: String,
        folding: TextFolding,
    ): Verdict {
        if (steps.isNullOrEmpty()) return Verdict.Rejected(BridgeError.SHAPE)
        // More steps than the instruction and the schema offered — the engine's own cap — is not the
        // required form: `AgentExecutor` ends a session `Blocked` at that many steps, so a longer plan could
        // never finish, and the card never shows one.
        if (steps.size > BridgeRendering.MAX_STEPS_OFFERED) return Verdict.Rejected(BridgeError.SHAPE)

        // Every step as in 3a — shape, membership, derivability — in order; the first refusal is the plan's.
        val proposed = mutableListOf<ProposedStep>()
        for (step in steps) {
            when (val judged = judgeStep(step, offering, registry, command, folding)) {
                is Judged.Step -> proposed += judged.step
                is Judged.Refused -> return Verdict.Rejected(judged.error)
            }
        }
        // Then every reference, read off the steps as the model wrote them.
        steps.forEachIndexed { index, step ->
            if (!referencesHold(index, step, steps, offering)) return Verdict.Rejected(BridgeError.SHAPE)
        }
        return Verdict.Valid(ModelPlanning.Proposed(proposed))
    }

    private fun judgeStep(
        step: RawStep,
        offering: BridgeOffering,
        registry: ToolRegistry,
        command: String,
        folding: TextFolding,
    ): Judged {
        val toolText = step.tool?.trim().orEmpty()
        if (toolText.isEmpty()) return Judged.Refused(BridgeError.SHAPE)
        val id = ToolId(toolText)
        val offered = offering.find(id)
            ?: return Judged.Refused(if (registry.find(id) == null) BridgeError.UNKNOWN_TOOL else BridgeError.NOT_OFFERED)

        val declared = offered.description.args.map { it.name }.toSet()
        if (step.args.any { (name, value) -> name !in declared && value.isGiven() }) {
            return Judged.Refused(BridgeError.SHAPE)
        }

        val values = linkedMapOf<String, String>()
        val origins = linkedMapOf<String, ValueOrigin>()
        val refs = linkedMapOf<String, StepRef>()
        for (arg in offered.description.args) {
            val raw = step.args[arg.name]
            if (raw is RawArg.Ref) {
                // A value the device will have: it satisfies `required`, and it is not the user's words to
                // derive. Whether it may stand at all is judged over the whole plan, in [referencesHold].
                refs[arg.name] = StepRef(raw.step, raw.key)
                continue
            }
            val value = (raw as? RawArg.Text)?.value?.trim()
            if (value.isNullOrEmpty()) {
                if (arg.required) return Judged.Refused(BridgeError.SHAPE)
                continue
            }
            if (arg.mention && !Derivability.isDerivable(value, command, folding)) {
                return Judged.Refused(BridgeError.UNDERIVABLE_MENTION)
            }
            values[arg.name] = value
            origins[arg.name] = Derivability.originOf(value, command, folding)
        }
        return Judged.Step(ProposedStep(id, values, origins, offered.descriptor.risk, offered.descriptor.effect, refs))
    }

    /**
     * B-18, B-9: every reference in the step at [index] stands on an argument its tool's description says
     * accepts one, names an EARLIER step, and names an output that step's tool describes. Read off [steps]
     * as the model wrote them — not off what [judgeStep] kept — so the judgement covers every step.
     */
    private fun referencesHold(index: Int, step: RawStep, steps: List<RawStep>, offering: BridgeOffering): Boolean {
        step.args.forEach { (name, value) ->
            val ref = value as? RawArg.Ref ?: return@forEach
            val arg = offeredTool(step, offering)?.description?.args?.firstOrNull { it.name == name } ?: return false
            if (!arg.acceptsRef) return false
            if (ref.step < 0 || ref.step >= index) return false
            val source = offeredTool(steps[ref.step], offering) ?: return false
            if (source.description.outputs.none { it.name == ref.key }) return false
        }
        return true
    }

    private fun offeredTool(step: RawStep, offering: BridgeOffering): OfferedTool? =
        step.tool?.trim()?.takeIf { it.isNotEmpty() }?.let { offering.find(ToolId(it)) }

    /** Given — a reference, or words that are not blank; `null` and blank words are absent. */
    private fun RawArg?.isGiven(): Boolean = when (this) {
        null -> false
        is RawArg.Ref -> true
        is RawArg.Text -> value.isNotBlank()
    }

    private sealed interface Judged {
        data class Step(val step: ProposedStep) : Judged
        data class Refused(val error: BridgeError) : Judged
    }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
