package com.sidr.launcher.domain.ai.router

/**
 * The **parsed-but-unvalidated** structured reply from the LLM router (AIL-4) — the portable JSON
 * shape decoded by the data-layer planner impl, handed to [ProposalValidator] to be strict-validated
 * against the [com.sidr.launcher.domain.action.ActionCatalog] and turned into a [PlanResult].
 *
 * Keeping this a plain domain value (not a serialization DTO) keeps the fail-closed validation logic
 * pure and unit-testable without HTTP or a JSON library: the impl owns "wire → [ActionProposal]", the
 * pure [ProposalValidator] owns "[ActionProposal] → [PlanResult]".
 *
 * @property action the proposed action id string, or the sentinels `"none"` / `"clarify"`.
 * @property args the model's proposed arguments (all string values in the MVP schema).
 * @property confidence the model's advisory 0..1 self-estimate; `null` when it emitted none.
 * @property question the disambiguating question, only meaningful when [action] == `"clarify"`.
 */
data class ActionProposal(
    val action: String,
    val args: Map<String, String> = emptyMap(),
    val confidence: Float? = null,
    val question: String? = null,
) {
    companion object {
        /** Sentinel [action] meaning "no registered action fits" → [PlanResult.NoPlan]. */
        const val ACTION_NONE = "none"

        /** Sentinel [action] meaning "ask the user [question]" → [PlanResult.Clarify]. */
        const val ACTION_CLARIFY = "clarify"
    }
}
