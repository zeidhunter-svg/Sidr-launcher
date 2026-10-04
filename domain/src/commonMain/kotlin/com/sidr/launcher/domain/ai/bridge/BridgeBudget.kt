package com.sidr.launcher.domain.ai.bridge

/**
 * The bridge's own budget (spec §7.12.2) — named, and not the router's 2 000 ms / 256 tokens, which a
 * reasoning model never fits. [ceilingMs] bounds the WHOLE planning, the one retry included;
 * [maxOutputTokens] bounds each answer. The transport's socket is bounded by the same [ceilingMs] per
 * attempt (`bridgeHttpClient`), so the socket never ends a wait the ceiling still allows.
 *
 * **Provisional until the owner's transport measurement** (plan Task 12) and the ADR fix them: 45 s sits
 * inside the discussion memo's 30–60 s hypothesis; 2 048 tokens leaves room for hidden reasoning if the
 * provider counts it, which is one of the measurement's questions. The budget on ACTIONS
 * (`RuntimeBudget`) is a different thing and does not change.
 */
data class BridgeBudget(val ceilingMs: Long, val maxOutputTokens: Int) {
    companion object {
        val Default = BridgeBudget(ceilingMs = 45_000L, maxOutputTokens = 2_048)
    }
}
