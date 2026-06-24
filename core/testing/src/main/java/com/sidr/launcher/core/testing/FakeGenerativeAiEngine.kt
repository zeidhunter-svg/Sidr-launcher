package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.ai.AiChunk
import com.sidr.launcher.domain.ai.AiRequest
import com.sidr.launcher.domain.ai.GenerativeAiEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scriptable fake [GenerativeAiEngine] for unit tests.
 *
 * Pass a fixed [chunks] list, or a [script] lambda producing chunks per request. Records the last
 * [AiRequest] seen for assertions. [delayBetweenChunksMs] (default 0) lets stream-timing tests
 * advance a virtual scheduler between emissions. Terminal failures are emitted as a value
 * ([AiChunk.Failed]); this fake never throws to the collector.
 */
class FakeGenerativeAiEngine(
    private val chunks: List<AiChunk> = emptyList(),
    private val delayBetweenChunksMs: Long = 0L,
    private val script: ((AiRequest) -> List<AiChunk>)? = null,
) : GenerativeAiEngine {

    var lastRequest: AiRequest? = null
        private set

    override fun generate(request: AiRequest): Flow<AiChunk> = flow {
        lastRequest = request
        val toEmit = script?.invoke(request) ?: chunks
        for ((index, chunk) in toEmit.withIndex()) {
            if (index > 0 && delayBetweenChunksMs > 0) delay(delayBetweenChunksMs)
            emit(chunk)
        }
    }
}
