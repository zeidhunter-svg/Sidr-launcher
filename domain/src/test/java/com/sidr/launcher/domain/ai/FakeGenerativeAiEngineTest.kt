package com.sidr.launcher.domain.ai

import com.sidr.launcher.core.testing.FakeGenerativeAiEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeGenerativeAiEngineTest {

    private val request = AiRequest(
        messages = listOf(AiMessage(AiRole.USER, "open telegram offline?")),
        maxOutputTokens = 256,
    )

    @Test
    fun `collecting the flow yields the scripted chunks and records the request`() = runTest {
        val scripted = listOf(
            AiChunk.Text("Sure"),
            AiChunk.Text(", here goes."),
            AiChunk.Completed(AiStopReason.COMPLETE),
        )
        val engine = FakeGenerativeAiEngine(chunks = scripted)

        val collected = engine.generate(request).toList()

        assertEquals(scripted, collected)
        assertEquals(request, engine.lastRequest)
        assertEquals("Sure, here goes.", AiChunks.assembleText(collected))
    }

    @Test
    fun `a Failed terminal is delivered as a value, the collector does not throw`() = runTest {
        val scripted = listOf(
            AiChunk.Text("partial"),
            AiChunk.Failed(AiError.RateLimited(retryAfterMs = 1_000)),
        )
        val engine = FakeGenerativeAiEngine(chunks = scripted)

        // No try/catch: if the fake threw to the collector this would fail the test.
        val collected = engine.generate(request).toList()

        assertEquals(scripted, collected)
        assertEquals("partial", AiChunks.assembleText(collected))
        assertEquals(AiError.RateLimited(1_000), (collected.last() as AiChunk.Failed).error)
    }

    @Test
    fun `script lambda can vary chunks per request`() = runTest {
        val engine = FakeGenerativeAiEngine(
            script = { req -> listOf(AiChunk.Text(req.messages.first().content), AiChunk.Completed(AiStopReason.COMPLETE)) },
        )
        val collected = engine.generate(request).toList()
        assertEquals("open telegram offline?", AiChunks.assembleText(collected))
    }
}
