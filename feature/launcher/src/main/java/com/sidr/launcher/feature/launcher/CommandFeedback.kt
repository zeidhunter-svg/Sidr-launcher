package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.voice.SpeechRecognitionError

/**
 * Transient, UI-layer feedback for a submitted command — distinct from [LauncherUiState], which
 * holds the app-grid state. The ViewModel maps each `CommandOutcome` to one of these via an
 * exhaustive `when`; `LauncherPresentation.feedbackText` maps each of these to a string resource +
 * args (I18N-1 spec §3.5 — the ViewModel names *what happened*, the mapper/screen pick the sentence).
 */
sealed interface CommandFeedback {

    /** Nothing to show. */
    data object None : CommandFeedback

    /** Dev-console output only — exempt from I18N-1 (spec §3.2). Never used by product surfaces. */
    data class Message(val text: String) : CommandFeedback

    /** Empty submit: show the "type a command" hint. */
    data object EmptyInput : CommandFeedback

    /** Below the suggest threshold: ask the user to be more specific. */
    data object LowConfidence : CommandFeedback

    /** Unrecognised input: show the usage examples. */
    data object UnknownCommand : CommandFeedback

    /** A message named by the domain (I18N-1 spec §3.5). */
    data class Domain(val message: CommandMessage) : CommandFeedback

    /** A technical failure named by the domain. */
    data class Failure(val failure: CommandFailure) : CommandFeedback

    /** Voice recognition failed — the enum is the message. */
    data class VoiceError(val error: SpeechRecognitionError) : CommandFeedback

    /** A medium-confidence suggestion the user can confirm by editing/re-submitting. */
    data class Suggestion(val intent: SuggestedIntent) : CommandFeedback

    /** An ambiguous query — render [candidates] as tappable suggestions (tap → launch). */
    data class Ambiguous(val candidates: List<InstalledApp>) : CommandFeedback
}

/**
 * What [LauncherViewModel]'s old `describe()` used to word, named instead (I18N-1 spec §3.5) — one
 * variant per [com.sidr.launcher.domain.intent.LauncherIntent] branch, so
 * `LauncherPresentation.feedbackText` stays exhaustive without losing any of the seven original
 * sentences.
 */
sealed interface SuggestedIntent {
    data class LaunchApp(val query: String) : SuggestedIntent
    data class Search(val query: String) : SuggestedIntent
    data object OpenSettings : SuggestedIntent
    data object SimpleCommand : SuggestedIntent
    data class OpenUrl(val url: String) : SuggestedIntent
    data class PlayStoreSearch(val query: String) : SuggestedIntent
    data object Unknown : SuggestedIntent
}
