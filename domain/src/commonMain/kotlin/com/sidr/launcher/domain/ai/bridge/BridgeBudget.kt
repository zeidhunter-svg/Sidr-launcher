package com.sidr.launcher.domain.ai.bridge

/**
 * The bridge's own budget (spec §7.12.2) — named, and not the router's 2 000 ms / 256 tokens, which a
 * reasoning model never fits. [ceilingMs] bounds the WHOLE planning, the one retry included;
 * [maxOutputTokens] bounds each answer. The transport's socket is bounded by the same [ceilingMs] per
 * attempt (`bridgeHttpClient`), so the socket never ends a wait the ceiling still allows.
 *
 * **Fixed on 2026-11-07 by the owner's measurement** (ADR «2026-10-05 — A4′ ф3a», the addendum «по
 * замеру»): seven models through OpenRouter, `PROMPT_ONLY`, largest p90 31 635 ms (`qwen/qwen3.7-flash`),
 * × 1.5 rounded up to 5 000 ms → 50 s; below the 90 s cap. 4 096 tokens because two `PROMPT_ONLY` lines
 * (`deepseek/deepseek-v4.1-flash`, `qwen/qwen3.7-flash`) ended `finish=length` with 2 048 completion
 * tokens, all of them hidden reasoning. That is the answer to the spec's question: hidden reasoning IS
 * counted inside the token limit, so [maxOutputTokens] is the one named limit that bounds reasoning and
 * output together. A separate reasoning cap (provider-specific, e.g. OpenRouter's `reasoning.max_tokens`)
 * is not built — unmeasured, named for 3b. No test pins these two numbers: reverting them would pass the
 * gate (the ADR addendum says so). The budget on ACTIONS (`RuntimeBudget`) is a different thing and does
 * not change.
 */
data class BridgeBudget(val ceilingMs: Long, val maxOutputTokens: Int) {
    companion object {
        val Default = BridgeBudget(ceilingMs = 50_000L, maxOutputTokens = 4_096)
    }
}
