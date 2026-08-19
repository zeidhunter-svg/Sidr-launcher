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

    // ── Understanding unavailable (Этап 4.0, ADR 1/4 clause 5) ───────────────
    // Three mutually exclusive states, produced by `RouteCommandUseCase`'s ordered early returns:
    // at most one can ever be reached for a single command. Before Этап 4.0 all three answered
    // "Unknown command", which was a claim about the *command* when the truth was about the
    // *system* — a FastPath miss is no longer grounds to call an understandable command unknown.

    /**
     * `localOnlyMode` is on: the user has opted understanding out of the cloud. Deliberately a
     * neutral statement with no call to action and no navigation (owner decision, Этап 4.0 F5) —
     * nagging someone on every miss to undo a choice they made on purpose is the manipulation
     * `DOC-HYA-1` forbids.
     */
    data object UnderstandingLocalOnly : CommandMessage

    /**
     * No AI provider is configured. The feature layer renders this one with a tap-through to the
     * provider setup screen (owner decision, Этап 4.0 F1) — it is the only one of the three whose
     * fix the user can apply right now, in one step.
     */
    data object UnderstandingNeedsProvider : CommandMessage

    /** A provider is configured but the device is offline. Nothing to fix, only to wait for. */
    data object UnderstandingNeedsNetwork : CommandMessage
}
