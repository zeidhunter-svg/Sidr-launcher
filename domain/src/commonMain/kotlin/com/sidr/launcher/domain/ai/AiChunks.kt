package com.sidr.launcher.domain.ai

/** Pure helpers over [AiChunk] streams. */
object AiChunks {
    /**
     * Concatenates the deltas of all [AiChunk.Text] chunks in iteration order, ignoring the terminal
     * [AiChunk.Completed] / [AiChunk.Failed] chunks. Returns "" when there is no text.
     */
    fun assembleText(chunks: Iterable<AiChunk>): String = buildString {
        for (chunk in chunks) {
            if (chunk is AiChunk.Text) append(chunk.delta)
        }
    }
}
