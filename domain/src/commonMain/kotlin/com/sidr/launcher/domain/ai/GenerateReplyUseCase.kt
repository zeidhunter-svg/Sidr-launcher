package com.sidr.launcher.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Entry point for the assistant feature (Block M, Fork P5-4). Builds the outbound request via the
 * Block-L [PromptContextBuilder] and delegates streaming to the [GenerativeAiEngine] — the router
 * at runtime, which is the single unqualified binding in `:app`.
 *
 * Pure `:domain`: no Android, no Ktor. The dependency on [engine] is the port only; the router
 * type is invisible here. [HandleUserCommandUseCase] is **untouched** — matching ≠ generation.
 */
class GenerateReplyUseCase(
    private val engine: GenerativeAiEngine,
    private val promptContextBuilder: PromptContextBuilder,
) {
    fun generate(userCommand: String): Flow<AiChunk> =
        engine.generate(promptContextBuilder.build(userCommand))
}
