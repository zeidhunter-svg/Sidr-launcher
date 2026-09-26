package com.sidr.launcher.domain.suggestions

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

/**
 * Port: the suggestion concern's engine — the **third** port, distinct from `IntentMatcher`
 * (→ `IntentMatchResult`) and `GenerativeAiEngine` (→ `Flow<AiChunk>`) (Phase 7, Block S).
 *
 * The implementation (Block U) aggregates the enabled [SuggestionProvider]s, ranks via a
 * [SuggestionRanker], and bounds the result. It is **port-only here** — no aggregation logic ships in
 * Block S.
 *
 * **Single-source / surface ownership (Fork F7-8):** [suggestions] is designed to be collected by the
 * **one** host `LauncherViewModel` into `LauncherUiState.suggestions` — never a parallel
 * `SuggestionsViewModel`/`StateFlow`. The cold-start reconciliation (first-paint from
 * `SuggestionsCacheRepository`, then a fresh load **supersedes — never merges**, per
 * `architecture.md:254-256`) is that host VM's responsibility, wired in Block W2. The form composes
 * here without a new port: [Suggestion] is a superset of `CachedSuggestion(label, actionId)`.
 *
 * Never throws expected failures: [refresh] returns [OperationResult]; [suggestions] is a cold,
 * value-bearing stream.
 */
interface SuggestionEngine {
    /** Observable, ranked, bounded suggestions for the home surface. */
    fun suggestions(): Flow<List<Suggestion>>

    /** Force a fresh aggregate-rank-bound pass; returns the new list or a failure (never throws). */
    suspend fun refresh(): OperationResult<List<Suggestion>>
}
