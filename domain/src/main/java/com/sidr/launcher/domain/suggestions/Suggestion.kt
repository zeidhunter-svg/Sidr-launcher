package com.sidr.launcher.domain.suggestions

/**
 * A single ranked, **display-safe** launcher suggestion (Phase 7, Block S).
 *
 * This is the third concern's value type — distinct from `IntentMatchResult` (matching) and
 * `AiChunk` (generation). A [Suggestion] is what the suggestion row renders and what a tap routes
 * through the existing `ExecutableAction`/`HandleUserCommandUseCase` path.
 *
 * **Privacy:** carries only already-derived, display-safe fields. No raw query, no event title, no
 * coordinates, no timestamps, no provenance beyond the coarse [source]. Its two display fields are a
 * strict superset of `CachedSuggestion(label, actionId)` — so the existing `SuggestionsCacheRepository`
 * can cache engine output with no new port (verified by `SuggestionCompositionTest`).
 *
 * @property label display text (e.g. "Camera", "Join meeting").
 * @property actionId offline-resolvable target — a package name or a route constant.
 * @property source coarse provenance, used by the ranker for per-source weighting.
 * @property score the producing provider's pre-rank weight (for usage sources, recency/frequency are
 *   already folded in here). The ranker combines this with source/time weights; it does **not**
 *   require raw history.
 */
data class Suggestion(
    val label: String,
    val actionId: String,
    val source: SuggestionSource,
    val score: Double,
)

/**
 * Coarse provenance of a [Suggestion]. Drives per-source weighting in [HeuristicSuggestionRanker].
 *
 * `RECENT_USAGE`/`FREQUENT_USAGE`/`TIME_OF_DAY` are the always-on, zero-permission sources;
 * `CALENDAR`/`LOCATION` are opt-in, permission-gated (their providers emit empty when not granted —
 * Block U); `SEMANTIC` is the optional ONNX re-rank source (Block V). Adding a source is a
 * deliberate, reviewed change (the ranker's weight table must stay total).
 */
enum class SuggestionSource {
    RECENT_USAGE,
    FREQUENT_USAGE,
    TIME_OF_DAY,
    CALENDAR,
    LOCATION,
    SEMANTIC,
}
