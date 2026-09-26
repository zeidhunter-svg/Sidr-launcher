package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.LauncherAction

/**
 * Pure, fail-closed validator (AIL-4, Fork R3 = b): turns an [ActionProposal] the LLM emitted into a
 * [PlanResult], validating it strictly against the registered [ActionCatalog]. Stdlib-only, no HTTP —
 * this is the security-critical seam that guarantees "we never execute on a hallucinated/free-text
 * reply", so it lives in `domain` and is unit-tested exhaustively.
 *
 * A proposal collapses to [PlanResult.NoPlan] unless it is fully well-formed:
 * - `action == "none"` → [PlanResult.NoPlan].
 * - `action == "clarify"` → [PlanResult.Clarify] iff a non-blank `question` is present, else `NoPlan`.
 * - otherwise `action` MUST be a registered [ActionId] in the [catalog]; every `required` arg in the
 *   descriptor's `argSchema` MUST be present and non-blank; and NO arg key outside the schema may be
 *   present (an invented/extra arg fails closed). Any breach → `NoPlan`.
 */
object ProposalValidator {

    /** Advisory confidence used when the model emitted none; the value is not a hard gate (R4). */
    private const val DEFAULT_CONFIDENCE = 0.5f

    fun validate(proposal: ActionProposal, catalog: ActionCatalog): PlanResult {
        val action = proposal.action.trim()
        when (action) {
            ActionProposal.ACTION_NONE -> return PlanResult.NoPlan
            ActionProposal.ACTION_CLARIFY -> {
                val question = proposal.question?.trim().orEmpty()
                return if (question.isNotEmpty()) PlanResult.Clarify(question) else PlanResult.NoPlan
            }
        }

        val descriptor = catalog.descriptor(ActionId(action)) ?: return PlanResult.NoPlan
        if (!argsSatisfySchema(proposal.args, descriptor)) return PlanResult.NoPlan

        val built = buildAction(descriptor.id, proposal.args) ?: return PlanResult.NoPlan
        val confidence = (proposal.confidence ?: DEFAULT_CONFIDENCE).coerceIn(0f, 1f)
        return PlanResult.RoutedAction(built, confidence)
    }

    /**
     * Strict arg check: no arg key outside the declared schema, and every required arg present and
     * non-blank. String-only in the MVP ([ArgType.STRING]), so the value type needs no further check.
     */
    private fun argsSatisfySchema(args: Map<String, String>, descriptor: ActionDescriptor): Boolean {
        val declared = descriptor.argSchema.map { it.name }.toSet()
        if (args.keys.any { it !in declared }) return false
        return descriptor.argSchema.all { arg ->
            if (!arg.required) true else args[arg.name]?.isNotBlank() == true
        }
    }

    /**
     * Builds the concrete [LauncherAction] for a validated ([id], [args]). Returns `null` (→ `NoPlan`)
     * for the theoretically-unreachable case of a registered descriptor with no matching variant, so
     * the mapping stays total and fail-closed. Args are already schema-valid here.
     */
    private fun buildAction(id: ActionId, args: Map<String, String>): LauncherAction? = when (id) {
        ActionIds.LAUNCH_APP -> LauncherAction.LaunchApp(args.getValue("query").trim())
        ActionIds.WEB_SEARCH -> LauncherAction.WebSearch(args.getValue("query").trim())
        ActionIds.OPEN_SETTINGS -> LauncherAction.OpenSettings
        ActionIds.OPEN_ASSISTANT -> LauncherAction.OpenAssistant(args["prompt"]?.trim()?.takeIf { it.isNotEmpty() })
        ActionIds.SHOW_APPS -> LauncherAction.ShowApps
        ActionIds.OPEN_URL -> LauncherAction.OpenUrl(args.getValue("url").trim())
        ActionIds.PLAY_STORE_SEARCH -> LauncherAction.PlayStoreSearch(args.getValue("query").trim())
        else -> null
    }
}
