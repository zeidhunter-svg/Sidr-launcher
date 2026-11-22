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
     * The device could not read [words] as the value a tool's argument needs — «полчасика» is not a
     * duration, «25:99» is not a clock time, an address with a space in it is not an address (A4′ phase
     * 3b, Task 6; spec §7.8, B-5). [words] are what was written for that one argument, never the whole
     * command. Said on the device, before any card; the model is not asked again.
     *
     * **Produced at the bridge** (`Binding.NotUnderstood`, step 7), where [words] are what the model wrote
     * for the argument — possibly the user's words, possibly the model's own: the sentence quotes them
     * with no model mark (controller ruling E6-R2, a named limit; a marked variant would be a sixth string
     * key, the owner's decision). At step 2b it is only `RouteCommandUseCase`'s fail-safe: since ruling
     * E6-R1 a value 2b cannot read is left to the model, and no production planner produces this there.
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

    /**
     * The model read the command and found no tool that fits (`ModelPlanning.Declined` at step 7), and FastPath had
     * yielded nothing the device could answer instead (A4′ acceptance tail, task 5; owner decision 2: б). Not one
     * of the four «understanding unavailable» states above: understanding happened, only acting is missing — the
     * line says «понял, но так пока не умею», where «неизвестная команда» blamed the command (ADR 1/4 rule 3 now
     * holds for an understood command too). Reached only past the local-only / provider / network gates, so it
     * is never said without the model having been asked.
     */
    data object UnderstoodNoAction : CommandMessage
}
