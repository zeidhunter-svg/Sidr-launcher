package com.sidr.launcher.domain.intent

/**
 * Port: rule-based (or future NLU-based) intent classification from normalized text input.
 *
 * IMPORTANT — two separate ports:
 * This interface covers input → structured intent (one result + confidence).
 * The future [GenerativeAiEngine] (→ Flow<AiChunk>) is a wholly separate port for text
 * generation. Do NOT fold generation into this contract or combine these two ports.
 */
interface IntentMatcher {
    suspend fun match(normalizedInput: String): IntentMatchResult
}

/**
 * Source of a match result. AI generation is NOT a valid source here —
 * [MatcherSource.AI] is intentionally absent to prevent the two-port invariant from
 * being silently violated. The local ONNX NLU classifier (formerly `NLU`) was removed in the
 * agentic-restart Этап 0.3 (ADR 2/4 — OQ#1/#2 closed); understanding past FastPath now routes to
 * the bridge's model planner (`ModelPlanner`, A4′ phase 3a), a different port, not a matcher source.
 */
enum class MatcherSource { RULE_BASED }

data class IntentMatchResult(
    val normalizedInput: String,
    val best: IntentCandidate,
    val alternatives: List<IntentCandidate> = emptyList(),
    val source: MatcherSource = MatcherSource.RULE_BASED,
    val debugReason: String? = null,
)
