package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import javax.inject.Inject

/**
 * The `SYSTEM_INTENT` tier of the agent's world: the **unchanged**
 * `ExecuteActionUseCase -> IntentActionResolver -> ActionExecutor` chain, wearing the [ToolWorker]
 * contract — A1' federation's registered worker for this source, reachable only from
 * `ToolFederation`'s dispatcher, not a boundary of its own. No new executor surface is introduced,
 * which is why the launcher's existing behaviour is unaffected by construction rather than by testing.
 *
 * The mapping that matters is `Message(NoAppFound) -> Observed(APP_NOT_INSTALLED)`. To the command
 * pipeline "no such app" is a terminal message; to the agent it is an observation, and the whole
 * two-step plan hangs off that reinterpretation.
 *
 * [invoke] takes a [ResolvedInvocation] (F6, Task 5b): its arguments are already concrete values, so
 * this adapter never resolves an [com.sidr.launcher.domain.tool.ArgSource] itself.
 */
class SystemIntentToolWorker @Inject constructor(
    private val executeAction: ExecuteActionUseCase,
) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
        // The query is normalised once and the same value feeds both the action and the output, so
        // `resolved_query` is the string the launch actually resolved against rather than a second,
        // independently derived reading of the same argument. That is the invariant a later step binds
        // to; two reads could disagree, one cannot.
        val query = invocation.args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return ToolResult.Failed(CommandFailure.Generic)

        val action = actionFor(invocation.id, query) ?: return ToolResult.Failed(CommandFailure.Generic)
        return map(executeAction.execute(action), outputFor(invocation.id, query))
    }

    private fun actionFor(id: ToolId, query: String): LauncherAction? = when (id) {
        ToolIds.LAUNCH_APP -> LauncherAction.LaunchApp(query)
        ToolIds.PLAY_STORE_SEARCH -> LauncherAction.PlayStoreSearch(query)
        // An unregistered tool never reaches the action path. Tested with a query in hand, so the
        // blank-query guard above cannot answer for this branch.
        else -> null
    }

    /**
     * `launch_app` reports `resolved_query` on every result it can carry an output on (`Effected`,
     * `Observed`) — outputs are a property of the tool, not of the branch it took, so this is not gated
     * on which [CommandOutcome] came back. `play_store_search` declares no output
     * ([SystemIntentToolSource]) and stays the default empty [ToolOutput].
     *
     * The key is [SystemIntentToolSource.RESOLVED_QUERY], the same constant the descriptor is built
     * from: declaration and emission cannot drift on the spelling, and `SystemIntentToolContractTest`
     * checks they do not drift on the set either.
     */
    private fun outputFor(id: ToolId, query: String): ToolOutput =
        if (id == ToolIds.LAUNCH_APP) {
            ToolOutput(mapOf(SystemIntentToolSource.RESOLVED_QUERY to query))
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
