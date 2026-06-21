package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.model.InstalledApp

/**
 * Transient, UI-layer feedback for a submitted command — distinct from [LauncherUiState], which
 * holds the app-grid state. Carries only display-safe content (in the spirit of
 * `core.common.UiError`). The ViewModel maps each `CommandOutcome` to one of these via an
 * exhaustive `when`.
 */
sealed interface CommandFeedback {

    /** Nothing to show. */
    data object None : CommandFeedback

    /** A plain user-facing message: hint, examples, clarify, settings stub, "not found", error. */
    data class Message(val text: String) : CommandFeedback

    /** A medium-confidence suggestion the user can confirm by editing/re-submitting. */
    data class Suggestion(val text: String) : CommandFeedback

    /** An ambiguous query — render [candidates] as tappable suggestions (tap → launch). */
    data class Ambiguous(val candidates: List<InstalledApp>) : CommandFeedback
}
