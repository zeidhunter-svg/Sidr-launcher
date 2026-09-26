package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.agent.AgentSessionId
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

    /** Navigate to the assistant — the ViewModel maps this to its navigation Channel. */
    data object OpenAssistant : CommandOutcome

    /** Navigate to launcher settings — the ViewModel maps this to its navigation Channel. */
    data object OpenSettings : CommandOutcome

    /** Show the full app grid — handled by the ViewModel/UI, not the executor. */
    data object ShowApps : CommandOutcome

    /** Clear the command input — handled by the ViewModel, not the executor. */
    data object ClearInput : CommandOutcome

    /**
     * The LLM router (AIL-4) proposed a registered [action] for a low-confidence / natural-language
     * command. Per Fork R4 it is **never auto-executed**: [needsConfirmation] (`true` for a
     * [com.sidr.launcher.domain.action.ActionRiskLevel.CONFIRM] action, `false` for `SAFE`) tells the
     * UI whether to require an explicit confirm card or offer a one-tap suggestion. [confidence] is the
     * model's advisory 0..1. The confirmation card + execution wiring land in AIL-5; AIL-4 surfaces it
     * display-safely only.
     */
    data class RoutedAction(
        val action: LauncherAction,
        val confidence: Float,
        val needsConfirmation: Boolean,
    ) : CommandOutcome

    /**
     * A goal FastPath decided but did not achieve — the app does not exist — has been handed to the
     * agent, and [id] identifies the session now in flight. The command surface stops rendering a
     * message and starts rendering session state.
     */
    data class AgentSessionStarted(val id: AgentSessionId) : CommandOutcome
}
