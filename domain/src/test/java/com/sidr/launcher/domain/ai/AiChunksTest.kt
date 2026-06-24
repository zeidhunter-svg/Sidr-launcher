package com.sidr.launcher.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiChunksTest {

    @Test
    fun `assembleText concatenates Text deltas in order`() {
        val chunks = listOf(
            AiChunk.Text("Hel"),
            AiChunk.Text("lo, "),
            AiChunk.Text("world"),
            AiChunk.Completed(AiStopReason.COMPLETE),
        )
        assertEquals("Hello, world", AiChunks.assembleText(chunks))
    }

    @Test
    fun `assembleText ignores terminal Completed and Failed chunks`() {
        val chunks = listOf(
            AiChunk.Completed(AiStopReason.MAX_TOKENS, AiUsage(inputTokens = 3, outputTokens = 7)),
            AiChunk.Text("kept"),
            AiChunk.Failed(AiError.Timeout),
        )
        assertEquals("kept", AiChunks.assembleText(chunks))
    }

    @Test
    fun `assembleText returns empty string when there is no text`() {
        assertEquals("", AiChunks.assembleText(emptyList()))
        assertEquals("", AiChunks.assembleText(listOf(AiChunk.Completed(AiStopReason.REFUSAL))))
        assertEquals("", AiChunks.assembleText(listOf(AiChunk.Failed(AiError.Offline))))
    }

    @Test
    fun `refusal is modeled as a successful terminal stop reason, not an error`() {
        // A refusal carries text-so-far + a Completed(REFUSAL); it is NOT an AiChunk.Failed.
        val chunks = listOf(
            AiChunk.Text("I can't help with that."),
            AiChunk.Completed(AiStopReason.REFUSAL),
        )
        assertEquals("I can't help with that.", AiChunks.assembleText(chunks))
        assertEquals(AiStopReason.REFUSAL, (chunks.last() as AiChunk.Completed).stopReason)
    }
}
