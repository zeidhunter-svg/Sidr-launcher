package com.sidr.launcher.domain.suggestions

/**
 * The shared, **display-safe** input passed to every [SuggestionProvider] and to the
 * [SuggestionRanker] (Phase 7, Block S).
 *
 * The shape is fixed here so the Block-U providers and the Block-V semantic decorator compile against
 * one context type that never changes under them. It carries **only derived, non-sensitive** values:
 * a time bucket, the current wall-clock (so usage providers compute recency deterministically and
 * testably rather than reading the clock themselves), and the in-progress typed prefix (the user's own
 * transient input, for the optional semantic re-rank). It must **never** carry raw sensitive signals
 * (event titles, coordinates) — those stay inside their permission-gated provider and leave only as a
 * derived [Suggestion].
 *
 * @property timeOfDay coarse current bucket; drives the ranker's time-of-day prior.
 * @property nowEpochMs current wall-clock in epoch millis; used by usage providers (Block U) to
 *   compute recency. Not sensitive (it is "now", not a stored query timestamp).
 * @property typedPrefix the in-progress command-input text, if any; consumed by the Block-V semantic
 *   re-rank. Null when the surface is idle.
 */
data class SuggestionContext(
    val timeOfDay: TimeOfDay,
    val nowEpochMs: Long,
    val typedPrefix: String? = null,
)

/**
 * Coarse time-of-day bucket (Fork F7-4: morning / work / evening / night).
 *
 * [WORK] is the working-hours / midday bucket. Bucketing from a real clock is a `:core:android`
 * concern (Block U/W); the domain only reasons over the bucket.
 */
enum class TimeOfDay { MORNING, WORK, EVENING, NIGHT }
