package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.ai.AiError

/** One step as the transport read it — unvalidated, every field possibly absent. */
data class RawStep(val tool: String?, val args: Map<String, String?>)

/** The answer object as the transport read it: plain values, judged by `BridgeValidator`. */
data class RawAnswer(val kind: String?, val step: RawStep?, val question: String?, val destination: String?)

/**
 * What the provider reported about a reply, for the transport measurement (spec §7.12.2: «входят ли
 * скрытые рассуждения в лимит токенов»). Never user content.
 */
data class ReplyMeta(val finishReason: String?, val completionTokens: Int?, val reasoningTokens: Int?) {
    companion object {
        val NONE = ReplyMeta(finishReason = null, completionTokens = null, reasoningTokens = null)
    }
}

sealed interface TransportReply {
    data class Answer(val answer: RawAnswer, val meta: ReplyMeta) : TransportReply

    /** A reply that is not one JSON object in the expected place — prose, a cut answer, an error body. */
    data class Unparseable(val meta: ReplyMeta) : TransportReply

    /** No reply: the request failed, by the same vocabulary as the assistant's engine. */
    data class Failed(val error: AiError) : TransportReply
}

/**
 * Exactly what may leave the device (B-4): the system text (instruction + offering), the schema text
 * (sent only in the transport's schema forms), the user's command verbatim, an optional static retry
 * note, and the generation limit.
 */
data class BridgeRequest(
    val system: String,
    val schemaJson: String,
    val command: String,
    val retryNote: String?,
    val maxOutputTokens: Int,
)

/** The bridge's own transport — its own path, not `GenerativeAiEngine` (spec §7.12.2). Never throws. */
fun interface BridgeTransport {
    suspend fun ask(request: BridgeRequest): TransportReply
}
