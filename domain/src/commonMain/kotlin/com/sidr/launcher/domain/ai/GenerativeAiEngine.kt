package com.sidr.launcher.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Generative text engine — the port for AI **generation**, deliberately separate from
 * `IntentMatcher` (which does **matching**). Matching ≠ generation: do not fold one into the other.
 *
 * Provider-neutral by construction. Implementations (the first cloud adapter in Block K, a second
 * cloud adapter later, a static fallback in Block M) live in `:data:*`; `:domain` knows no vendor or
 * wire format. The generate-reply use-case (Block M) depends only on this interface.
 */
interface GenerativeAiEngine {
    /**
     * Cold flow: collecting it starts the generation; cancelling the collection cancels the
     * underlying request. Expected failures are emitted as a terminal [AiChunk.Failed] and the flow
     * then completes normally — implementations must NOT throw expected errors to the collector.
     */
    fun generate(request: AiRequest): Flow<AiChunk>
}

/**
 * The ordered composite engine the use-case talks to at runtime (cloud → … → static fallback, with
 * a reserved slot for local ONNX in Phase 6). It is a [GenerativeAiEngine] so callers stay unaware
 * of routing. The implementation lands in Block M; this marker only fixes the type now.
 */
interface GenerativeRouter : GenerativeAiEngine
