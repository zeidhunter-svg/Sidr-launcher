package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.result.OperationResult

/**
 * Use case: turns raw user command text into a single [CommandOutcome] for the UI.
 *
 * Pipeline: normalize → (empty?) → match → confidence gate → route → execute-when-safe.
 *
 * Invariants:
 * - Never throws to the UI. Technical failures from the resolver/executor become
 *   [CommandOutcome.Failed] with a safe message — they do NOT bubble up as exceptions or
 *   [OperationResult.Failure].
 * - Not everything is executed: navigation ([CommandOutcome.OpenAssistant]), input clearing
 *   ([CommandOutcome.ClearInput]), grid display ([CommandOutcome.ShowApps]), ambiguity and
 *   "not found" never reach the [ActionExecutor].
 * - Low/medium confidence never auto-executes.
 */
class HandleUserCommandUseCase(
    private val matcher: IntentMatcher,
    private val resolver: IntentActionResolver,
    private val executor: ActionExecutor,
    private val confidencePolicy: IntentConfidencePolicy,
) {

    suspend fun handle(rawInput: String): CommandOutcome {
        val normalized = CommandNormalizer.normalize(rawInput)
        if (normalized.isEmpty()) return CommandOutcome.Empty

        val match = matcher.match(normalized)
        val intent = match.best.intent
        val confidence = match.best.confidence

        // ── Confidence gate — runs before any resolution or execution ────────
        if (confidencePolicy.isLowConfidence(confidence)) {
            return when (intent) {
                is LauncherIntent.UnknownIntent -> CommandOutcome.Unknown(intent.originalInput)
                else -> CommandOutcome.LowConfidence
            }
        }
        if (!confidencePolicy.shouldAutoExecute(confidence)) {
            return CommandOutcome.Suggest(intent, confidence)
        }

        // ── High confidence — route by intent ────────────────────────────────
        // SimpleCommandIntent bypasses the resolver: the resolver collapses the command
        // identity into a generic action, and these routes (navigate / clear / show apps)
        // are UI/navigation concerns, not execution.
        if (intent is LauncherIntent.SimpleCommandIntent) {
            return routeSimpleCommand(intent.command)
        }

        return when (val resolved = resolver.resolve(intent)) {
            is OperationResult.Failure -> CommandOutcome.Failed(SAFE_FAILURE_MESSAGE)
            is OperationResult.Success -> routeAction(resolved.value)
        }
    }

    private fun routeSimpleCommand(command: SimpleCommand): CommandOutcome = when (command) {
        SimpleCommand.OPEN_ASSISTANT -> CommandOutcome.OpenAssistant
        SimpleCommand.SHOW_APPS -> CommandOutcome.ShowApps
        SimpleCommand.CLEAR -> CommandOutcome.ClearInput
        SimpleCommand.HELP -> CommandOutcome.Message(HELP_MESSAGE)
    }

    private suspend fun routeAction(action: ExecutableAction): CommandOutcome = when (action) {
        // Side-effecting actions go through the executor.
        is ExecutableAction.LaunchAppAction,
        is ExecutableAction.OpenSearchAction,
        -> mapExecution(executor.execute(action))

        // Routing outcomes — never executed.
        is ExecutableAction.AmbiguousAppAction -> CommandOutcome.NeedsConfirmation(action.candidates)
        is ExecutableAction.ShowMessageAction -> CommandOutcome.Message(action.message)
        ExecutableAction.OpenLauncherSettingsAction -> CommandOutcome.Message(SETTINGS_STUB_MESSAGE)
        ExecutableAction.NoOpAction -> CommandOutcome.NoOp
    }

    private fun mapExecution(result: ActionExecutionResult): CommandOutcome = when (result) {
        is ActionExecutionResult.Success -> CommandOutcome.Executed
        is ActionExecutionResult.Failure -> CommandOutcome.Failed(result.safeMessage)
        is ActionExecutionResult.Unsupported -> CommandOutcome.Failed(SAFE_FAILURE_MESSAGE)
    }

    private companion object {
        const val SAFE_FAILURE_MESSAGE = "Something went wrong. Please try again."
        const val SETTINGS_STUB_MESSAGE = "Settings are not available yet."
        const val HELP_MESSAGE = "Try: open <app>, search <query>, show apps, clear"
    }
}
