package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.UnresolvedTarget
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.model.InstalledApp

/**
 * The single outcome type returned by [HandleUserCommandUseCase] to the UI.
 *
 * It is the UI vocabulary — distinct from [ActionExecutionResult] (the executor vocabulary) and
 * from [com.sidr.launcher.domain.result.OperationResult] (technical success/failure). Normal
 * business outcomes (ambiguity, "not found", low confidence, unknown) are modeled here as
 * regular variants, NOT as failures. Only real technical failures map to [Failed].
 *
 * Android-free: navigation is expressed as [OpenAssistant] / [OpenSettings] domain intents,
 * never a :core:common route string — the ViewModel translates them to navigation events.
 */
sealed interface CommandOutcome {

    /** Empty input — show a hint, do nothing. */
    data object Empty : CommandOutcome

    /** A side-effecting action ran successfully — the UI should clear the command input. */
    data object Executed : CommandOutcome

    /** A safe action that intentionally does nothing — input is left untouched (no clear). */
    data object NoOp : CommandOutcome

    /** A user-facing message: HELP examples, "not found", or [ExecutableAction.ShowMessageAction]. */
    data class Message(val message: CommandMessage) : CommandOutcome

    /** Query matched 2+ apps — render [candidates] as tappable suggestions; nothing executed. */
    data class NeedsConfirmation(val candidates: List<InstalledApp>) : CommandOutcome

    /** Medium confidence (0.50..<0.85) — suggest/confirm, do not auto-execute. */
    data class Suggest(val intent: LauncherIntent, val confidence: Float) : CommandOutcome

    /** Below the suggest threshold and not an [LauncherIntent.UnknownIntent] — ask the user to be specific. */
    data object LowConfidence : CommandOutcome

    /** Unrecognized input — show usage examples. [input] is the original raw text. */
    data class Unknown(val input: String) : CommandOutcome

    /** A technical failure occurred; [failure] is safe to display (no PII/stack). */
    data class Failed(val failure: CommandFailure) : CommandOutcome

    /**
     * Navigate to the assistant — the ViewModel maps this to its navigation Channel. [question], when
     * present, is put into the assistant's input UNSENT (A4′ phase 3a, spec §7.12.3 condition 3): the
     * model judged the command a question, and the user's «отправить» is the confirmation of that
     * reading. `null` opens it empty, as FastPath's "assistant" always has.
     */
    data class OpenAssistant(val question: String?) : CommandOutcome

    /** Navigate to launcher settings — the ViewModel maps this to its navigation Channel. */
    data object OpenSettings : CommandOutcome

    /** Show the full app grid — handled by the ViewModel/UI, not the executor. */
    data object ShowApps : CommandOutcome

    /** Clear the command input — handled by the ViewModel, not the executor. */
    data object ClearInput : CommandOutcome

    /**
     * A goal FastPath decided but did not achieve — the app does not exist — has been handed to the
     * agent, and [id] identifies the session now in flight. The command surface stops rendering a
     * message and starts rendering session state.
     */
    data class AgentSessionStarted(val id: AgentSessionId) : CommandOutcome

    /**
     * The model's reading of a command, bound to this device and shown BEFORE anything runs (A4′ phase
     * 3a, spec §7.12.3 condition 1) — the interpretation card. Nothing here executes: only the user's
     * «Выполнить» starts a session, through the engine, which gates it by risk like any plan. It replaced
     * the deleted router's proposal outcome (A4′ phase 3a, D3).
     */
    data class Interpreted(val interpretation: Interpretation) : CommandOutcome

    /**
     * A registered tool claimed the command at routing step 2b and its target is the user's to pick
     * (A4′ phase 3b, fork 3b-E; plan §0.2 B-7): two or more apps answer the words exactly, or one or
     * more are only OFFERED (`UnresolvedTarget.confirm`) — a word form (an edge pair such as «Плинта» /
     * «Плинт» offers both), or a name found inside extra words. **Not an interpretation card** — the model read
     * nothing here — and not FastPath's launch list ([NeedsConfirmation], whose tap launches): a tap
     * binds the package through [UnresolvedTarget.bind] and starts the session directly, through the
     * engine, which gates the step by its risk. Never produced with an empty candidate list.
     */
    data class ChooseTarget(val target: UnresolvedTarget) : CommandOutcome
}
