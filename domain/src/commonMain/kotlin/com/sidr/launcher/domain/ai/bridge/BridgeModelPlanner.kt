package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.tool.ToolRegistry
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The bridge's [ModelPlanner] (spec §7.12.2): one ask; on a rejected or unreadable answer exactly one
 * more, carrying ONLY the closed code's static note; a transport failure is never retried — it is
 * reported by its reason. The offering is computed per call (the federation moves while the process
 * lives), and the prompt, the schema and the validator all read that one offering.
 *
 * It binds nothing and knows nothing of the device: binding runs after it returns
 * (`RouteCommandUseCase` step 7, `MentionBinder`), so nothing binding finds can reach a retry.
 */
class BridgeModelPlanner(
    private val registry: ToolRegistry,
    private val descriptions: ToolDescriptions,
    private val transport: BridgeTransport,
    private val budget: BridgeBudget = BridgeBudget.Default,
) : ModelPlanner {

    override suspend fun plan(command: String): ModelPlanning =
        withTimeoutOrNull(budget.ceilingMs) { attempt(command) }
            ?: ModelPlanning.Unavailable(ModelUnavailableReason.TIMED_OUT)

    private suspend fun attempt(command: String): ModelPlanning {
        val offering = BridgeFilter.offeringOf(registry, descriptions)
        val first = BridgeRequest(
            system = BridgeRendering.systemText(offering),
            schemaJson = BridgeRendering.schemaJson(offering),
            command = command,
            retryNote = null,
            maxOutputTokens = budget.maxOutputTokens,
            reasoningMaxTokens = budget.reasoningMaxTokens,
        )
        val firstReply = transport.ask(first)
        val error = when (val judged = judge(firstReply, offering, command)) {
            is Judged.Done -> return judged.planning
            is Judged.Retry -> judged.error
        }
        // The first ask plus the closed code's note: both limits, the reasoning cap included, travel unchanged.
        val retry = first.copy(retryNote = BridgeRendering.retryNote(error))
        return when (val judged = judge(transport.ask(retry), offering, command)) {
            is Judged.Done -> judged.planning
            is Judged.Retry -> ModelPlanning.Unavailable(ModelUnavailableReason.UNREADABLE)
        }
    }

    private fun judge(reply: TransportReply, offering: BridgeOffering, command: String): Judged = when (reply) {
        is TransportReply.Failed -> Judged.Done(ModelPlanning.Unavailable(reasonOf(reply.error)))
        is TransportReply.Unparseable -> Judged.Retry(BridgeError.SHAPE)
        is TransportReply.Answer -> when (val verdict = BridgeValidator.validate(reply.answer, offering, registry, command)) {
            is Verdict.Valid -> Judged.Done(verdict.planning)
            is Verdict.Rejected -> Judged.Retry(verdict.error)
        }
    }

    private sealed interface Judged {
        data class Done(val planning: ModelPlanning) : Judged
        data class Retry(val error: BridgeError) : Judged
    }

    companion object {
        /** The assistant's transport vocabulary, folded to what the user can act on. */
        fun reasonOf(error: AiError): ModelUnavailableReason = when (error) {
            AiError.MissingCredentials, AiError.Unauthorized -> ModelUnavailableReason.CREDENTIALS
            is AiError.InvalidRequest -> ModelUnavailableReason.REQUEST_REJECTED
            is AiError.RateLimited, is AiError.ServerError, is AiError.Unknown -> ModelUnavailableReason.PROVIDER_FAILING
            AiError.Timeout -> ModelUnavailableReason.TIMED_OUT
            AiError.Offline, is AiError.Network -> ModelUnavailableReason.NETWORK
        }
    }
}
