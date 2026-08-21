package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import javax.inject.Inject

/**
 * The `SYSTEM_INTENT` tier of the agent's world: the **unchanged**
 * `ExecuteActionUseCase -> IntentActionResolver -> ActionExecutor` chain, wearing the [ToolExecutor]
 * contract. No new executor surface is introduced, which is why the launcher's existing behaviour is
 * unaffected by construction rather than by testing.
 *
 * The mapping that matters is `Message(NoAppFound) -> Observed(APP_NOT_INSTALLED)`. To the command
 * pipeline "no such app" is a terminal message; to the agent it is an observation, and the whole
 * two-step plan hangs off that reinterpretation.
 *
 * [invoke] takes a [ResolvedInvocation] (F6, Task 5b): its arguments are already concrete values, so
 * this adapter never resolves an [com.sidr.launcher.domain.tool.ArgSource] itself.
 */
class SystemIntentToolExecutor @Inject constructor(
    private val executeAction: ExecuteActionUseCase,
) : ToolExecutor {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
        val action = actionFor(invocation) ?: return ToolResult.Failed(CommandFailure.Generic)
        return map(executeAction.execute(action), outputFor(invocation))
    }

    private fun actionFor(invocation: ResolvedInvocation): LauncherAction? {
        val query = invocation.args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return null
        return when (invocation.id) {
            ToolIds.LAUNCH_APP -> LauncherAction.LaunchApp(query)
            ToolIds.PLAY_STORE_SEARCH -> LauncherAction.PlayStoreSearch(query)
            else -> null
        }
    }

    /**
     * `launch_app` reports [resolved_query][ToolIds.LAUNCH_APP] on every result it can carry an output
     * on (`Effected`, `Observed`) — outputs are a property of the tool, not of the branch it took, so
     * this is not gated on which [CommandOutcome] came back. `play_store_search` declares no output
     * ([SystemIntentToolSource]) and stays the default empty [ToolOutput].
     */
    private fun outputFor(invocation: ResolvedInvocation): ToolOutput =
        if (invocation.id == ToolIds.LAUNCH_APP) {
            ToolOutput(mapOf("resolved_query" to invocation.args.getValue("query")))
        } else {
            ToolOutput()
        }

    private fun map(outcome: CommandOutcome, output: ToolOutput): ToolResult = when (outcome) {
        CommandOutcome.Executed -> ToolResult.Effected(output)
        is CommandOutcome.NeedsConfirmation -> ToolResult.Observed(ObservedFact.APP_AMBIGUOUS, output)
        is CommandOutcome.Message ->
            if (outcome.message is CommandMessage.NoAppFound) {
                ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED, output)
            } else {
                ToolResult.Failed(CommandFailure.Generic)
            }
        is CommandOutcome.Failed -> ToolResult.Failed(outcome.failure)
        // The two projected families cannot produce any other outcome. Failing closed rather than
        // adding branches keeps this adapter honest about what it actually maps.
        else -> ToolResult.Failed(CommandFailure.Generic)
    }
}
