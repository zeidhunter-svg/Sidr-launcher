package com.sidr.launcher.domain.ai

import com.sidr.launcher.core.testing.FakeGenerativeAiEngine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GenerateReplyUseCaseTest {

    private val scripted = listOf(
        AiChunk.Text("Hello!"),
        AiChunk.Completed(AiStopReason.COMPLETE),
    )
    private val engine = FakeGenerativeAiEngine(chunks = scripted)
    private val builder = PromptContextBuilder()
    private val useCase = GenerateReplyUseCase(engine, builder)

    // ── L → M: builder request reaches the engine ────────────────────────────

    @Test
    fun `generate streams engine chunks`() = runTest {
        val chunks = useCase.generate("open telegram").toList()
        assertEquals(scripted, chunks)
    }

    @Test
    fun `generate passes builder output to engine`() = runTest {
        useCase.generate("open telegram").toList()

        val received = engine.lastRequest
        assertNotNull("engine must receive a request", received)
        val expected = builder.build("open telegram")
        assertEquals(expected, received)
    }

    @Test
    fun `request has exactly one USER message with the verbatim command`() = runTest {
        useCase.generate("open telegram").toList()

        val req = engine.lastRequest!!
        assertEquals(1, req.messages.size)
        assertEquals(AiRole.USER, req.messages[0].role)
        assertEquals("open telegram", req.messages[0].content)
    }

    @Test
    fun `request carries static system prompt`() = runTest {
        useCase.generate("open telegram").toList()

        val req = engine.lastRequest!!
        assertEquals(PromptContextBuilder.DEFAULT_SYSTEM_PROMPT, req.system)
    }

    @Test
    fun `request has no model override (null — uses engine default)`() = runTest {
        useCase.generate("open telegram").toList()
        assertEquals(null, engine.lastRequest!!.model)
    }

    @Test
    fun `user command containing privacy-denylist term is passed verbatim (guard scans statics not user content)`() = runTest {
        // The outbound guard scans static text/field inventories, not user input — verified in
        // AiRequestGuardTest. This test confirms the use case does not sanitise user content.
        useCase.generate("show my calendar events for tomorrow").toList()
        assertEquals("show my calendar events for tomorrow", engine.lastRequest!!.messages[0].content)
    }
}
