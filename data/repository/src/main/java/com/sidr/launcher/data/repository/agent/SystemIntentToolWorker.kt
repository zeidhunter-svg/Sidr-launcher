package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.sort.UrlSort
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
        val action = actionFor(invocation) ?: return ToolResult.Failed(CommandFailure.Generic)
        return map(executeAction.execute(action), outputFor(invocation.id, action))
    }

    /**
     * Each tool reads its OWN argument (`query`, or `url` for `open_url`), trimmed once; a blank or
     * missing one fails closed before the action path. An unregistered tool never reaches it either.
     *
     * `open_url` reads its address through [UrlSort.normalized] (phase 3b, B-5): any address that is
     * not `http://` or `https://` fails closed (final review A3 of phase 3a), as does one with whitespace
     * inside it, and an upper-case scheme reaches the action path lower-cased — Android's filter
     * matching is case-sensitive, so `HTTPS://…` would open nothing. The address is the MODEL's, and
     * the action path ends in `ACTION_VIEW`, which opens whatever app claims the scheme —
     * `quillpay://…` is a payment or a deep link, not a page. FastPath's `UrlDetector` guards only the
     * typed path and never sees this one, so the tool path holds the rule here, in the execution layer
     * below the consent card.
     */
    private fun actionFor(invocation: ResolvedInvocation): LauncherAction? {
        fun arg(name: String): String? = invocation.args[name]?.trim()?.takeIf { it.isNotEmpty() }
        return when (invocation.id) {
            ToolIds.LAUNCH_APP -> arg("query")?.let {
                LauncherAction.LaunchApp(it, packageName = arg(SystemIntentToolSource.PIN_ARG))
            }
            ToolIds.PLAY_STORE_SEARCH -> arg("query")?.let { LauncherAction.PlayStoreSearch(it) }
            InAppToolIds.WEB_SEARCH -> arg("query")?.let { LauncherAction.WebSearch(it) }
            InAppToolIds.OPEN_URL -> arg("url")?.let(UrlSort::normalized)?.let { LauncherAction.OpenUrl(it) }
            // An unregistered tool never reaches the action path. Tested with a query in hand, so the
            // blank-argument guard above cannot answer for this branch.
            else -> null
        }
    }

    /**
     * `launch_app` reports `resolved_query` on every result it can carry an output on — the query the
     * action was BUILT from, so the output and the launch can never disagree (one read, not two). The
     * other three declare no output ([SystemIntentToolSource]) and stay the default empty [ToolOutput].
     */
    private fun outputFor(id: ToolId, action: LauncherAction): ToolOutput =
        if (id == ToolIds.LAUNCH_APP && action is LauncherAction.LaunchApp) {
            ToolOutput(mapOf(SystemIntentToolSource.RESOLVED_QUERY to action.query))
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
