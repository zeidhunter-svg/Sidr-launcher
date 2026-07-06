package com.sidr.launcher.domain.intent

import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.result.OperationResult

/**
 * Use case (AIL-5): executes a **confirmed** router-proposed [LauncherAction], returning a
 * [CommandOutcome] for the UI.
 *
 * This is the execution counterpart to AIL-4's routing: the LLM router surfaces a proposal
 * ([CommandOutcome.RoutedAction]) that is **never** auto-executed (Fork R4); once the user confirms
 * (or one-taps a SAFE proposal), the ViewModel calls here to actually run it.
 *
 * It maps each [LauncherAction] family to a [LauncherIntent] and resolves + executes it through the
 * **unchanged** [IntentActionResolver] + [ActionExecutor] — the same proven path the rule pipeline
 * uses ([HandleUserCommandUseCase]); the routing/execution semantics are intentionally identical
 * (ambiguity → [CommandOutcome.NeedsConfirmation], not-found → [CommandOutcome.Message], nav families
 * never touch the executor). No new executor surface is introduced. Never throws to the UI: a
 * resolver/executor technical failure becomes [CommandOutcome.Failed] with a safe message.
 */
class ExecuteActionUseCase(
    private val resolver: IntentActionResolver,
    private val executor: ActionExecutor,
) {

    suspend fun execute(action: LauncherAction): CommandOutcome = when (action) {
        // Navigation / UI-only families — resolved by the ViewModel, never the executor.
        LauncherAction.OpenSettings -> CommandOutcome.OpenSettings
        is LauncherAction.OpenAssistant -> CommandOutcome.OpenAssistant
        LauncherAction.ShowApps -> CommandOutcome.ShowApps

        // Side-effecting families — build the semantic intent, resolve (query → executable), execute.
        is LauncherAction.LaunchApp ->
            resolveAndRoute(LauncherIntent.LaunchAppIntent(action.query))
        is LauncherAction.WebSearch ->
            resolveAndRoute(LauncherIntent.SearchIntent(action.query))
        is LauncherAction.OpenUrl ->
            resolveAndRoute(LauncherIntent.OpenUrlIntent(action.url))
        is LauncherAction.PlayStoreSearch ->
            resolveAndRoute(LauncherIntent.PlayStoreSearchIntent(action.query))
    }

    private suspend fun resolveAndRoute(intent: LauncherIntent): CommandOutcome =
        when (val resolved = resolver.resolve(intent)) {
            is OperationResult.Failure -> CommandOutcome.Failed(SAFE_FAILURE_MESSAGE)
            is OperationResult.Success -> routeAction(resolved.value)
        }

    // Mirrors HandleUserCommandUseCase.routeAction: side-effecting actions go through the executor;
    // ambiguity / not-found / no-op are routing outcomes that never execute.
    private suspend fun routeAction(action: ExecutableAction): CommandOutcome = when (action) {
        is ExecutableAction.LaunchAppAction,
        is ExecutableAction.OpenSearchAction,
        is ExecutableAction.OpenUrlAction,
        is ExecutableAction.PlayStoreSearchAction,
        -> mapExecution(executor.execute(action))

        is ExecutableAction.AmbiguousAppAction -> CommandOutcome.NeedsConfirmation(action.candidates)
        is ExecutableAction.ShowMessageAction -> CommandOutcome.Message(action.message)
        ExecutableAction.OpenLauncherSettingsAction -> CommandOutcome.OpenSettings
        ExecutableAction.NoOpAction -> CommandOutcome.NoOp
    }

    private fun mapExecution(result: ActionExecutionResult): CommandOutcome = when (result) {
        is ActionExecutionResult.Success -> CommandOutcome.Executed
        is ActionExecutionResult.Failure -> CommandOutcome.Failed(result.safeMessage)
        is ActionExecutionResult.Unsupported -> CommandOutcome.Failed(SAFE_FAILURE_MESSAGE)
    }

    private companion object {
        const val SAFE_FAILURE_MESSAGE = "Something went wrong. Please try again."
    }
}
