package com.sidr.launcher.domain.ai

/**
 * Provider-neutral terminal reason for a finished generation.
 *
 * Note [REFUSAL] is a **successful** terminal state, not an error: the model was reached and chose
 * to decline (e.g. a safety/content stop). Transport/credential/parse failures are [AiError]s
 * carried by [AiChunk.Failed], not stop reasons.
 */
enum class AiStopReason {
    /** Natural end of the response. (Each adapter maps its provider's natural-end code to this.) */
    COMPLETE,

    /** Output hit the length limit. */
    MAX_TOKENS,

    /** A configured stop sequence was emitted. */
    STOP_SEQUENCE,

    /** The model declined — a refusal / content-filter / safety stop. */
    REFUSAL,

    /** Any provider-specific reason not mapped above. */
    OTHER,
}

/** Optional token accounting reported by the provider; any field may be absent. */
data class AiUsage(val inputTokens: Int? = null, val outputTokens: Int? = null)

/**
 * One unit of a streamed generation, the unified streaming contract `Flow<AiChunk>`.
 *
 * Terminal events are **values, not exceptions**: a stream ends with exactly one of [Completed] or
 * [Failed], after which the flow completes normally. Implementations must NOT throw expected errors
 * to the collector — this is the `Flow` analog of "repositories return `OperationResult`, never
 * throw to UI".
 */
sealed interface AiChunk {
    /** Incremental output text. Concatenate [Text.delta] in arrival order to rebuild the message. */
    data class Text(val delta: String) : AiChunk

    /** Terminal: the stream finished successfully. */
    data class Completed(val stopReason: AiStopReason, val usage: AiUsage? = null) : AiChunk

    /** Terminal: the stream ended due to a failure. The collector keeps any [Text] already emitted. */
    data class Failed(val error: AiError) : AiChunk
}
