package com.sidr.launcher.domain.intent

/**
 * A user-facing message named, not worded (I18N-1 spec §3.5).
 *
 * `domain` is pure Kotlin with no access to Android resources, so it must not decide wording - it says
 * *what happened* and the feature layer picks the sentence in the user's language. This is the doctrine
 * rule "user-facing text never originates in domain" made structural.
 */
sealed interface CommandMessage {

    /** Full help: the long variant that also lists `show apps, clear`. */
    data object Help : CommandMessage

    /** Short help: the variant `IntentActionResolver` produces. Kept distinct - the wording differs. */
    data object HelpBrief : CommandMessage

    /** No installed app matched [query]. */
    data class NoAppFound(val query: String) : CommandMessage

    data object ShowingAllApps : CommandMessage

    data object AssistantComingSoon : CommandMessage

    /**
     * Text from an external source - today only the LLM router's clarify question. Passed through
     * verbatim: it is model output, and its language is the model's, not ours.
     */
    data class Verbatim(val text: String) : CommandMessage
}
