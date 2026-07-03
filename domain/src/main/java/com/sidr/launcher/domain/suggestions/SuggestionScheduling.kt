package com.sidr.launcher.domain.suggestions

/**
 * Re-syncs the background suggestion / usage work with the current feature-flag + device state.
 *
 * The concrete scheduler is a WorkManager wrapper that lives in `:app` (the composition root that
 * owns `androidx.work`). Exposing it as a `:domain` port lets `:feature:settings` re-apply the
 * gate-before-enqueue contract when the AI-suggestions toggle flips — without a forbidden
 * `feature → :app` edge (Block X5, Fork X5-A).
 *
 * Idempotent by contract: safe to call repeatedly (both underlying jobs use stable unique names +
 * UPDATE policy). The implementation itself re-applies the `aiSuggestionsEnabled` / `LOW_END` /
 * battery-saver gate, so callers only need to write the flag first, then call this.
 */
interface SuggestionScheduling {
    suspend fun ensureScheduled()
}
