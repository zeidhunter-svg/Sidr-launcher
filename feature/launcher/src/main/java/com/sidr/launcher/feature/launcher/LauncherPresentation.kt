package com.sidr.launcher.feature.launcher

import androidx.annotation.StringRes
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.voice.SpeechRecognitionError

/**
 * I18N-1 feature-local presentation mapping (spec §8), following the DS-10 `AssistantPresentation`
 * precedent: pure Kotlin, no Compose, so the *wording decision* is unit-testable without a Robolectric
 * host, and `LauncherScreen` stays a render.
 */
internal data class FeedbackText(@StringRes val id: Int, val args: List<String> = emptyList())

/**
 * The command grammar is English by construction - `RuleBasedIntentMatcher` parses English verbs - so
 * command examples are arguments from this object, never part of the translatable sentence. A
 * translated `open` would not parse (spec §4). [SHOW_APPS]/[CLEAR] complete the grammar so the whole
 * "Try: open <app>, search <query>, show apps, clear" help line stays out of translatable text too.
 */
internal object CommandExamples {
    const val OPEN_APP = "open <app>"
    const val SEARCH_QUERY = "search <query>"
    const val SHOW_APPS = "show apps"
    const val CLEAR = "clear"
    const val OPEN_TELEGRAM = "open telegram"
}

/**
 * `null` means "this branch is not resource-backed": [CommandFeedback.None] renders nothing, and
 * [CommandFeedback.Message] is dev-console output rendered verbatim (spec §3.2). Returning `null`
 * rather than throwing keeps a mis-routed dev string harmless.
 */
internal fun feedbackText(feedback: CommandFeedback): FeedbackText? = when (feedback) {
    CommandFeedback.None -> null
    is CommandFeedback.Message -> null
    CommandFeedback.EmptyInput ->
        FeedbackText(R.string.launcher_feedback_empty_input, listOf(CommandExamples.OPEN_TELEGRAM))
    CommandFeedback.LowConfidence -> FeedbackText(R.string.launcher_feedback_low_confidence)
    CommandFeedback.UnknownCommand -> FeedbackText(
        R.string.launcher_feedback_unknown_command,
        listOf(CommandExamples.OPEN_APP, CommandExamples.SEARCH_QUERY),
    )
    is CommandFeedback.Domain -> when (val m = feedback.message) {
        CommandMessage.Help -> FeedbackText(
            R.string.launcher_message_help,
            listOf(
                CommandExamples.OPEN_APP,
                CommandExamples.SEARCH_QUERY,
                CommandExamples.SHOW_APPS,
                CommandExamples.CLEAR,
            ),
        )
        CommandMessage.HelpBrief -> FeedbackText(
            R.string.launcher_message_help_brief,
            listOf(CommandExamples.OPEN_APP, CommandExamples.SEARCH_QUERY),
        )
        is CommandMessage.NoAppFound -> FeedbackText(R.string.launcher_feedback_no_app_found, listOf(m.query))
        CommandMessage.ShowingAllApps -> FeedbackText(R.string.launcher_message_showing_all_apps)
        CommandMessage.AssistantComingSoon -> FeedbackText(R.string.launcher_message_assistant_soon)
        is CommandMessage.Verbatim -> FeedbackText(R.string.launcher_message_verbatim, listOf(m.text))
    }
    is CommandFeedback.Failure -> when (feedback.failure) {
        CommandFailure.Generic -> FeedbackText(R.string.launcher_failure_generic)
        CommandFailure.CantOpenApp -> FeedbackText(R.string.launcher_failure_cant_open_app)
        CommandFailure.NoSearchApp -> FeedbackText(R.string.launcher_failure_no_search_app)
        CommandFailure.CantOpenUrl -> FeedbackText(R.string.launcher_failure_cant_open_url)
        CommandFailure.NoStoreApp -> FeedbackText(R.string.launcher_failure_no_store_app)
    }
    is CommandFeedback.VoiceError -> FeedbackText(
        when (feedback.error) {
            SpeechRecognitionError.PERMISSION_DENIED -> R.string.launcher_voice_permission_denied
            SpeechRecognitionError.UNAVAILABLE -> R.string.launcher_voice_unavailable
            SpeechRecognitionError.NO_MATCH -> R.string.launcher_voice_no_match
            SpeechRecognitionError.BUSY -> R.string.launcher_voice_busy
            SpeechRecognitionError.NETWORK -> R.string.launcher_voice_network
            SpeechRecognitionError.TIMEOUT -> R.string.launcher_voice_timeout
            SpeechRecognitionError.UNKNOWN -> R.string.launcher_voice_unknown
        },
    )
    is CommandFeedback.Suggestion -> when (val i = feedback.intent) {
        is SuggestedIntent.LaunchApp -> FeedbackText(R.string.launcher_suggest_launch_app, listOf(i.query))
        is SuggestedIntent.Search -> FeedbackText(R.string.launcher_suggest_search, listOf(i.query))
        SuggestedIntent.OpenSettings -> FeedbackText(R.string.launcher_suggest_open_settings)
        SuggestedIntent.SimpleCommand -> FeedbackText(R.string.launcher_suggest_simple_command)
        is SuggestedIntent.OpenUrl -> FeedbackText(R.string.launcher_suggest_open_url, listOf(i.url))
        is SuggestedIntent.PlayStoreSearch -> FeedbackText(R.string.launcher_suggest_play_store, listOf(i.query))
        SuggestedIntent.Unknown -> FeedbackText(R.string.launcher_suggest_unknown)
    }
    is CommandFeedback.Ambiguous -> FeedbackText(R.string.launcher_feedback_ambiguous)
}

/**
 * I18N-1 (spec §3.5): named, not worded — mirrors domain's `CommandMessage`/`CommandFailure` split
 * for the two [com.sidr.launcher.domain.result.OperationError] branches `AppDrawerViewModel` maps into
 * `UiState.Error`. `core.common.UiError` has no typed-argument slot (a cross-feature type this task
 * must not modify) and `sidrString` only resolves inside a `@Composable` (spec §6), so this typed
 * value cannot yet reach the render side through `UiState.Error` -
 * `AppDrawerScreen.toDisplayMessage()` is untouched by I18N-1 Task 12/13. `AppDrawerViewModel` still
 * assembles the (byte-identical) English sentence locally until then; [appDrawerErrorText] is
 * exercised directly by `LauncherPresentationTest` and is ready for a follow-up once `UiState`/
 * `UiError` gain a typed slot, or this surface migrates off `core.common.UiState` (mirrors DS-10's
 * `AssistantError` replacing `UiError` for the Assistant screen).
 */
internal sealed interface AppDrawerError {
    data class PermissionDenied(val permission: String) : AppDrawerError
    data class DeviceNotCapable(val feature: String) : AppDrawerError
}

internal fun appDrawerErrorText(error: AppDrawerError): FeedbackText = when (error) {
    is AppDrawerError.PermissionDenied ->
        FeedbackText(R.string.launcher_drawer_permission_denied, listOf(error.permission))
    is AppDrawerError.DeviceNotCapable ->
        FeedbackText(R.string.launcher_drawer_not_supported, listOf(error.feature))
}
