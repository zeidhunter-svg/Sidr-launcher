package com.sidr.launcher.domain.suggestions

/**
 * Port: one source of suggestion candidates (Phase 7, Block S).
 *
 * Implementations (Block U) are the offline-first providers (time-of-day, recent/frequent usage) and
 * the opt-in, permission-gated providers (calendar, location). A provider **returns an empty list**
 * when its signal or permission is absent — it **never throws** and never blocks the pipeline. Each
 * candidate it emits is already display-safe (see [Suggestion]); no raw sensitive value leaves the
 * provider.
 */
interface SuggestionProvider {
    /** Candidates for the given [context]; empty when this source has nothing (or no permission). */
    suspend fun provide(context: SuggestionContext): List<Suggestion>
}
