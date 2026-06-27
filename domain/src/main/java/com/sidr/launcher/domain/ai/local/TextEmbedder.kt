package com.sidr.launcher.domain.ai.local

import com.sidr.launcher.domain.result.OperationResult

/**
 * Port: on-device text embedding. Returns a float vector for semantic similarity use-cases.
 *
 * Implementation deferred to Phase 7 (Fork P6-6): no ranking consumer exists in Phase 6.
 * The port is defined now so callers can depend on the contract; the ONNX embedder impl
 * will land behind this interface with zero domain change when Phase 7 ships.
 *
 * [embed] is suspend and returns [OperationResult] — never throws to callers.
 */
interface TextEmbedder {
    suspend fun embed(text: String): OperationResult<FloatArray>
}
