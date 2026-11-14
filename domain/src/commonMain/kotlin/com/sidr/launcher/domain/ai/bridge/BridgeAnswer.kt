package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.ai.AiError

/**
 * One argument's value as the transport read it (A4′ phase 3b, Task 8). [Text] is words — a JSON string,
 * number or boolean, as text. [Ref] names an output of an earlier step: the step's 0-based position in
 * `steps` and the output's name — read, not judged; `BridgeValidator` decides whether it may stand (B-18).
 */
sealed interface RawArg {
    data class Text(val value: String) : RawArg
    data class Ref(val step: Int, val key: String) : RawArg
}

/** One step as the transport read it — unvalidated, every field possibly absent; `null` is an absent value. */
data class RawStep(val tool: String?, val args: Map<String, RawArg?>)

/**
 * The answer object as the transport read it: plain values, judged by `BridgeValidator`. [steps] is the
 * plan's steps in order (fork 3b-B) — `null` when the answer gave none, empty when it gave an empty list.
 */
data class RawAnswer(val kind: String?, val steps: List<RawStep>?, val question: String?, val destination: String?)

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
 * note, and the generation limits — [maxOutputTokens] and the optional [reasoningMaxTokens] (fork 3b-H),
 * numbers of ours (`GENERATION_LIMITS`), never anything of the user's world. The transport sends the
 * reasoning cap to OpenRouter only (B-16).
 */
data class BridgeRequest(
    val system: String,
    val schemaJson: String,
    val command: String,
    val retryNote: String?,
    val maxOutputTokens: Int,
    val reasoningMaxTokens: Int? = null,
)

/** The bridge's own transport — its own path, not `GenerativeAiEngine` (spec §7.12.2). Never throws. */
fun interface BridgeTransport {
    suspend fun ask(request: BridgeRequest): TransportReply
}
