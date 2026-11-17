package com.sidr.launcher.domain.ai.bridge

/**
 * The bridge's own budget (spec §7.12.2) — named, and not the router's 2 000 ms / 256 tokens, which a
 * reasoning model never fits. The ceiling bounds the WHOLE planning, the one retry included, and is the
 * HOST's ([ceilingFor]): [cappedCeilingMs] where the reasoning cap reaches the provider, [ceilingMs]
 * everywhere else; [maxOutputTokens] bounds each answer, hidden reasoning included (shown for two models on
 * 2026-11-07); [reasoningMaxTokens], when set, caps the reasoning alone as well. Each consumer's socket is
 * bounded per attempt by [longestCeilingMs] (`bridgeHttpClient`, `JdkHttpSender`), so the socket never ends
 * a wait a ceiling still allows.
 *
 * **Re-measured on 2026-11-07b at the shipped 4 096 tokens** (the 45 s that held until phase 3b's own
 * measurement, below) — the pair the first run (2026-11-07, which
 * set 50 s at 2 048 tokens) never measured together. ADR «2026-10-05 — A4′ ф3a: один модельный путь…»,
 * addendum «Повторный замер «50 с × 4 096» (2026-11-07b)». Seven models through OpenRouter, `PROMPT_ONLY`:
 * largest p90 27 342 ms (`qwen/qwen3.7-flash`) × 1.5 = 41 013 → rounded up to 5 000 ms → **45 s**. One
 * line ran past both 45 s and 50 s (qwen, 91 751 ms over two attempts) and is cut by either; none fell
 * between them. [maxOutputTokens] stays 4 096: the truncation rule П2 fired (qwen hit the limit once;
 * deepseek used 4 044 of 4 096, 4 020 of them reasoning), and П2 answers with a reasoning cap, never with
 * a blind raise.
 *
 * **Phase 3b's in-phase measurement (2026-11-15, plan Task 14, rules R1–R5 written before the run)** set
 * both numbers that ship now. ADR «…один модельный путь…», addendum «Замер фазы 3b». Same seven models
 * through OpenRouter, `PROMPT_ONLY`, 40 lines (32 single + 8 `multi`), reasoning off and on, 560 lines:
 * R1 224 / 224 single lines usable (rule ≥ 220); R3 — for every model zero `REQUEST_REJECTED` and usable
 * 40 / 40 under both settings, truncations 1 → 0 and the largest p90 32 916 → 24 307 ms → the cap ON;
 * R4 — 24 307 ms (`qwen/qwen3.7-flash`, on) × 1.5 = 36 460 → rounded up to 5 000 ms → **40 s**. With
 * reasoning ON, one line ran past 40, 45 and 50 s alike (qwen, 51 536 ms over two attempts) and is cut by any
 * of them; none fell between 40 and 45 s. **That is the ON half only** (final review F3, phase 3b close): the
 * cap reaches OpenRouter alone (B-16), so every other provider runs as the OFF half did — and there the same
 * formula gives 32 916 × 1.5 = 49 374 → 50 s, three lines ran past 40 s (qwen 40 365, 41 429 and 86 687 ms)
 * and two of them fell between 40 and 45 s. **Built per host** (owner decision 2026-11-17, «1. b»): 40 s
 * ([cappedCeilingMs]) where the cap reaches OpenRouter, 50 s ([ceilingMs]) for every other host — each
 * ceiling the R4 of the half its host runs as. Which one applies is the transport's reading of the host,
 * the same predicate its body uses (B-16), so the ceiling and the body cannot disagree.
 *
 * **The reasoning cap is ON by default since then**, [maxOutputTokens] / 2 = 2 048 (derived, not a new
 * number; fork 3b-H). `null` would send nothing; a value is sent as OpenRouter's `reasoning.max_tokens`,
 * and only to the host `openrouter.ai` (B-16) — another endpoint may refuse an unknown field with 400, and
 * no other provider was measured. `BridgePayloadGuardTest > an OpenRouter body adds only the budget's
 * reasoning cap and carries no private source` reads that body.
 *
 * Pinned — both ceilings, the socket bound, the limits — by `BridgeModelPlannerTest > the shipped budget is
 * the re-measured pair`, which a legitimate re-measure edits (Task 14 did; the per-host split did). The budget on ACTIONS (`RuntimeBudget`) is a different thing and does not change.
 */
data class BridgeBudget(
    val ceilingMs: Long,
    val maxOutputTokens: Int,
    val reasoningMaxTokens: Int? = null,
    val cappedCeilingMs: Long = ceilingMs,
) {
    /**
     * The ceiling of one planning: [cappedCeilingMs] when this budget carries a reasoning cap AND the
     * transport says the cap reaches its host (`BridgeTransport.sendsReasoningCap`); [ceilingMs] otherwise.
     */
    fun ceilingFor(capReachesHost: Boolean): Long =
        if (capReachesHost && reasoningMaxTokens != null) cappedCeilingMs else ceilingMs

    /** The longest wait any host is allowed — the socket bound of both consumers' HTTP clients. */
    val longestCeilingMs: Long get() = maxOf(ceilingMs, cappedCeilingMs)

    companion object {
        val Default = BridgeBudget(
            ceilingMs = 50_000L,
            maxOutputTokens = 4_096,
            reasoningMaxTokens = 2_048,
            cappedCeilingMs = 40_000L,
        )
    }
}
