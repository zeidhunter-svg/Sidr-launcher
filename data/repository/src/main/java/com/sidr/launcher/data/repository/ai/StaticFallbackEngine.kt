package com.sidr.launcher.data.repository.ai

import com.sidr.launcher.domain.ai.AiChunk
import com.sidr.launcher.domain.ai.AiRequest
import com.sidr.launcher.domain.ai.AiStopReason
import com.sidr.launcher.domain.ai.GenerativeAiEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Canned-reply fallback engine (Block M, Fork P5-6). No network, no key, always succeeds.
 *
 * Emits a single static [AiChunk.Text] followed by [AiChunk.Completed] — never echoes request
 * content, never emits [AiChunk.Failed]. The [DefaultGenerativeRouter] routes here when the cloud
 * engine is ineligible (offline / no config / no key / key-read failure).
 */
class StaticFallbackEngine : GenerativeAiEngine {

    override fun generate(request: AiRequest): Flow<AiChunk> = flow {
        emit(AiChunk.Text(STATIC_REPLY))
        emit(AiChunk.Completed(AiStopReason.COMPLETE))
    }

    companion object {
        const val STATIC_REPLY =
            "I can't reach an AI service right now — check your connection or set up a provider in settings."
    }
}
