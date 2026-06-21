package com.sidr.launcher.domain.intent

/**
 * Port: performs a concrete, side-effecting [ExecutableAction] on the device.
 *
 * Scope: only actions that produce a real side effect belong here — currently
 * [ExecutableAction.LaunchAppAction] and [ExecutableAction.OpenSearchAction]. Routing
 * decisions (confidence gating, ambiguity, "not found", navigation, clear-input) are made by
 * [HandleUserCommandUseCase] BEFORE execution and never reach this port.
 *
 * Android-free: the implementation (PackageManager / Intent) lives in :data:repository.
 */
interface ActionExecutor {
    suspend fun execute(action: ExecutableAction): ActionExecutionResult
}

/**
 * Narrow result vocabulary of the executor — the outcome of performing one action.
 *
 * Deliberately does NOT model needs-confirmation / no-match: those are routing outcomes owned
 * by [CommandOutcome], decided before execution. Keeping them out avoids two overlapping
 * result types (domain invariant from Block D).
 */
sealed interface ActionExecutionResult {

    /** The action's side effect was performed successfully. */
    data object Success : ActionExecutionResult

    /**
     * A technical failure was caught while executing. [safeMessage] is safe to show the user —
     * no stack traces, no PII; the implementation must not leak system exception details.
     */
    data class Failure(val safeMessage: String) : ActionExecutionResult

    /**
     * The executor was handed an action it does not perform. Defensive branch — the use case
     * only routes [ExecutableAction.LaunchAppAction] / [ExecutableAction.OpenSearchAction] here,
     * so this should not normally occur.
     */
    data class Unsupported(val action: ExecutableAction) : ActionExecutionResult
}
