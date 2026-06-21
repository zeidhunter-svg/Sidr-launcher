package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.ExecutableAction

/**
 * Configurable fake [ActionExecutor]. Records executed actions so tests can assert the executor
 * is NOT invoked for routing-only outcomes (navigation, ambiguity, clear, settings stub, etc.).
 * Not wired into any Hilt graph — use directly in tests.
 */
class FakeActionExecutor : ActionExecutor {

    /** Result returned by [execute]. Defaults to success. */
    var resultToReturn: ActionExecutionResult = ActionExecutionResult.Success

    /** Actions passed to [execute], in call order. Empty list ⇒ the executor was never invoked. */
    val executedActions = mutableListOf<ExecutableAction>()

    val callCount: Int get() = executedActions.size

    override suspend fun execute(action: ExecutableAction): ActionExecutionResult {
        executedActions += action
        return resultToReturn
    }

    fun reset() {
        resultToReturn = ActionExecutionResult.Success
        executedActions.clear()
    }
}
