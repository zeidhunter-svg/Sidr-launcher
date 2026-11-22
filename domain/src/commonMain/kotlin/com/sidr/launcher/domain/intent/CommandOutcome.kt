package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.UnresolvedTarget
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
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

    /**
     * FastPath's launch list — render [candidates] as tappable suggestions whose tap LAUNCHES; nothing
     * executed. The name matched 2+ apps, or [confirm] (A4′ phase 3b, Task 7): it is only a word FORM of
     * their labels — «Вы имели в виду…?». [query] is the app name the path extracted (FastPath's
     * `displayNameQuery`), which keys a learned choice (`LearnedChoiceKeys.launch`, B-4); a confirmation
     * is learned exactly like a choice (B-3).
     */
    data class NeedsConfirmation(val candidates: List<InstalledApp>, val query: String, val confirm: Boolean = false) : CommandOutcome

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
     * A goal has been handed to the agent, and [id] identifies the session now in flight: a goal FastPath
     * decided but did not achieve — the app does not exist — (routing step 2), a registered tool's match
     * (step 2b), a learned command replayed (step 2c, A4′ phase 4), or a card's «Выполнить». The command
     * surface stops rendering a message and starts rendering session state.
     *
     * [learned] is the form of the learned command this session replays (plan §0.2 B-7, B-10) — the mark the
     * launcher shows while that session is on screen — and `null` for every other start.
     *
     * [proposedByModel] (A4′ acceptance tail, task 4 — D3, second amendment; controller ruling K4): the session was
     * started by routing step 7 from a FRESH model reading that needed no interpretation card — no pick owed, no
     * model-written text going out ([com.sidr.launcher.domain.memory.plan.needsCard]) — so the user saw nothing
     * before it ran; the launcher marks its session card «Предложено моделью». `false` for every other start: a
     * card's «Выполнить», a learned command (it carries [learned] instead), FastPath's and 2b's deterministic plans.
     * Such a start teaches nothing (owner answer I5 «не засчитывать»): there is no `learnable` here on purpose.
     */
    data class AgentSessionStarted(
        val id: AgentSessionId,
        val learned: CommandForm? = null,
        val proposedByModel: Boolean = false,
    ) : CommandOutcome

    /**
     * The model's reading of a command, bound to this device and shown BEFORE anything runs (A4′ phase
     * 3a, spec §7.12.3 condition 1) — the interpretation card. Nothing here executes: only the user's
     * «Выполнить» starts a session, through the engine, which gates it by risk like any plan. It replaced
     * the deleted router's proposal outcome (A4′ phase 3a, D3).
     *
     * Since A4′ phase 4 the reading is a learned command replayed on the device (step 2c) that still owes a pick or
     * sends the model's own text off the phone (plan §0.2 B-7) — and, since D3's second amendment (A4′ acceptance
     * tail, task 4), a reading fresh from the model (routing step 7) is this card on exactly the same condition
     * (`needsCard`) or when its start without a card failed; any other fresh reading is started at once and comes
     * back as [AgentSessionStarted] with [AgentSessionStarted.proposedByModel]:
     *  - [learnable] — what a fresh, bound reading would teach if it runs (B-8), set by step 7 only; `null` for
     *    a reading with nothing to learn, a replay, and a remainder card;
     *  - [learned] — the form a replay came from, for the card's «Выполнить» to carry the mark on into
     *    [AgentSessionStarted.learned] (the launcher's half, phase 4 Task 7); `null` for a fresh reading.
     */
    data class Interpreted(
        val interpretation: Interpretation,
        val learnable: LearnedPlanShape? = null,
        val learned: CommandForm? = null,
    ) : CommandOutcome

    /**
     * A registered tool claimed the command at routing step 2b and its target is the user's to pick
     * (A4′ phase 3b, fork 3b-E; plan §0.2 B-7): two or more apps answer the words exactly, or one or
     * more are only OFFERED (`UnresolvedTarget.confirm`) — a word form (an edge pair such as «Плинта» /
     * «Плинт» offers both), a name found inside extra words, or — for `uninstall_app` — a name exact only
     * after folding (owner decision 2026-11-17). **Not an interpretation card** — the model read
     * nothing here — and not FastPath's launch list ([NeedsConfirmation], whose tap launches): a tap
     * binds the package through [UnresolvedTarget.bind] and starts the session directly, through the
     * engine, which gates the step by its risk. Never produced with an empty candidate list.
     */
    data class ChooseTarget(val target: UnresolvedTarget) : CommandOutcome
}
