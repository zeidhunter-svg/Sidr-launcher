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

    /**
     * A tool claimed the command and the device could not read [words] as the value that tool's argument
     * needs — «полчасика» is not a duration, «25:99» is not a clock time, an address with a space in it
     * is not an address (A4′ phase 3b, Task 6; spec §7.8, B-5). [words] are what was given for that one
     * argument — at step 2b the user's own words, at the bridge the words the model wrote for it — never
     * the whole command. Said on the device, before any card; the model is not asked again.
     */
    data class ValueNotUnderstood(val words: String) : CommandMessage

    data object ShowingAllApps : CommandMessage

    data object AssistantComingSoon : CommandMessage

    /**
     * Text from an external source - the bridge's one clarify question (A4′ phase 3a) - model text,
     * so the sentence around it says so. Passed through verbatim: it is model output, and its language
     * is the model's, not ours.
     */
    data class Verbatim(val text: String) : CommandMessage

    // ── Understanding unavailable (Этап 4.0, ADR 1/4 clause 5) ───────────────
    // Four mutually exclusive states (three from Этап 4.0, a fourth from A4′ phase 3a), produced by
    // `RouteCommandUseCase`'s ordered early returns: at most one can ever be reached for a single
    // command. Before Этап 4.0 all three answered
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
     * provider setup screen (owner decision, Этап 4.0 F1) — of the three original states it is the only
     * one whose fix the user can apply right now, in one step. Since A4′ phase 3a
     * [UnderstandingModelUnavailable] carries the same tap-through for the two causes the provider
     * screen can fix (`LauncherPresentation.offersProviderSetup`).
     */
    data object UnderstandingNeedsProvider : CommandMessage

    /** A provider is configured but the device is offline. Nothing to fix, only to wait for. */
    data object UnderstandingNeedsNetwork : CommandMessage

    /**
     * A provider is configured and the device is online, but the model gave no usable answer, and
     * [reason] says why (A4′ phase 3a; finding (c) of the A1″ acceptance - a broken key, a refused model,
     * a dead endpoint and "the model answered prose" all used to read «Неизвестная команда»). A fourth
     * state beside the three above, and reached only past them: it comes from step 7 itself.
     */
    data class UnderstandingModelUnavailable(val reason: ModelUnavailableReason) : CommandMessage
}
