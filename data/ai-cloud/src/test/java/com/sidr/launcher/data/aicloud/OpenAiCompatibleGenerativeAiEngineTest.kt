package com.sidr.launcher.data.aicloud

import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.domain.ai.AiChunk
import com.sidr.launcher.domain.ai.AiChunks
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.AiMessage
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.AiRequest
import com.sidr.launcher.domain.ai.AiRole
import com.sidr.launcher.domain.ai.AiStopReason
import com.sidr.launcher.domain.security.SecretKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiCompatibleGenerativeAiEngineTest {

    private val provider = AiProviderId("openai-compatible")
    private val config = AiProviderConfig(
        providerId = provider,
        baseUrl = "https://api.example.com/v1",
        modelId = AiModelId("test-model"),
    )
    private val request = AiRequest(
        messages = listOf(AiMessage(AiRole.USER, "Hello there")),
        system = "You are a helpful assistant.",
        maxOutputTokens = 256,
    )

    private val sseHeaders = headersOf(HttpHeaders.ContentType, "text/event-stream")

    private fun seededStore(): FakeSecureSecretStore =
        FakeSecureSecretStore().apply { runBlocking { put(SecretKeys.apiKey(provider), "sk-test") } }

    private fun engine(
        mockEngine: MockEngine,
        store: FakeSecureSecretStore = seededStore(),
        cfg: AiProviderConfig? = config,
        dispatcher: CoroutineDispatcher,
        firstTokenMs: Long = 15_000L,
        idleMs: Long = 15_000L,
    ) = OpenAiCompatibleGenerativeAiEngine(
        httpClient = HttpClient(mockEngine),
        secretStore = store,
        configRepository = FakeAiProviderConfigRepository(cfg),
        ioDispatcher = dispatcher,
        firstTokenTimeoutMs = firstTokenMs,
        idleTimeoutMs = idleMs,
    )

    private fun sse(vararg dataLines: String): String =
        dataLines.joinToString("") { "data: $it\n\n" }

    // ── Happy stream ────────────────────────────────────────────────────────────

    @Test
    fun `streams ordered text then a single Completed COMPLETE`() = runTest {
        val dispatcher = Dispatchers.IO
        val body = sse(
            """{"choices":[{"delta":{"role":"assistant"}}]}""", // role-only: emits nothing
            """{"choices":[{"delta":{"content":"Hel"}}]}""",
            """{"choices":[{"delta":{"content":"lo"}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"stop"}]}""", // terminal: content-empty, carries stop
            "[DONE]",
        )
        val mock = MockEngine { respond(body, HttpStatusCode.OK, sseHeaders) }

        val chunks = engine(mock, dispatcher = dispatcher).generate(request).toList()

        assertEquals(
            listOf(AiChunk.Text("Hel"), AiChunk.Text("lo")),
            chunks.dropLast(1),
        )
        assertEquals(AiChunk.Completed(AiStopReason.COMPLETE), chunks.last())
        assertEquals("Hello", AiChunks.assembleText(chunks))
    }

    @Test
    fun `populates usage when the provider reports it`() = runTest {
        val dispatcher = Dispatchers.IO
        val body = sse(
            """{"choices":[{"delta":{"content":"Hi"}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":3}}""",
            "[DONE]",
        )
        val mock = MockEngine { respond(body, HttpStatusCode.OK, sseHeaders) }

        val completed = engine(mock, dispatcher = dispatcher).generate(request).toList().last()

        assertEquals(AiChunk.Completed(AiStopReason.COMPLETE, com.sidr.launcher.domain.ai.AiUsage(11, 3)), completed)
    }

    // ── Request shape ─────────────────────────────────────────────────────────────

    @Test
    fun `request targets configured base URL with bearer auth, minimal body, no sampling`() = runTest {
        val dispatcher = Dispatchers.IO
        var captured: HttpRequestData? = null
        val mock = MockEngine { req ->
            captured = req
            respond(sse("[DONE]"), HttpStatusCode.OK, sseHeaders)
        }

        engine(mock, dispatcher = dispatcher).generate(request).toList()

        val data = requireNotNull(captured)
        assertEquals("https://api.example.com/v1/chat/completions", data.url.toString())
        assertEquals("Bearer sk-test", data.headers[HttpHeaders.Authorization])

        val bodyText = (data.body as TextContent).text
        val obj = Json.parseToJsonElement(bodyText).jsonObject
        assertEquals("test-model", obj["model"]!!.jsonPrimitive.content)
        assertEquals(256, obj["max_tokens"]!!.jsonPrimitive.int)
        assertTrue(obj["stream"]!!.jsonPrimitive.boolean)
        assertFalse(bodyText.contains("temperature"))
        assertFalse(bodyText.contains("top_p"))
        assertFalse(bodyText.contains("top_k"))

        val messages = obj["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("You are a helpful assistant.", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
    }

    @Test
    fun `free-text model from config is sent verbatim`() = runTest {
        val dispatcher = Dispatchers.IO
        var captured: HttpRequestData? = null
        val mock = MockEngine { req -> captured = req; respond(sse("[DONE]"), HttpStatusCode.OK, sseHeaders) }
        val cfg = config.copy(modelId = AiModelId("vendor/some-exotic-model:free"))

        engine(mock, cfg = cfg, dispatcher = dispatcher).generate(request).toList()

        val obj = Json.parseToJsonElement((requireNotNull(captured).body as TextContent).text).jsonObject
        assertEquals("vendor/some-exotic-model:free", obj["model"]!!.jsonPrimitive.content)
    }

    // ── Refusal is a success terminal, not an error ──────────────────────────────

    @Test
    fun `content_filter finish maps to Completed REFUSAL, not Failed`() = runTest {
        val dispatcher = Dispatchers.IO
        val body = sse(
            """{"choices":[{"delta":{"content":"I can"}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"content_filter"}]}""",
            "[DONE]",
        )
        val mock = MockEngine { respond(body, HttpStatusCode.OK, sseHeaders) }

        val chunks = engine(mock, dispatcher = dispatcher).generate(request).toList()

        assertEquals(AiChunk.Completed(AiStopReason.REFUSAL), chunks.last())
        assertTrue(chunks.none { it is AiChunk.Failed })
    }

    @Test
    fun `delta refusal field maps to Completed REFUSAL`() = runTest {
        val dispatcher = Dispatchers.IO
        val body = sse(
            """{"choices":[{"delta":{"refusal":"I won't help with that."}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"stop"}]}""",
            "[DONE]",
        )
        val mock = MockEngine { respond(body, HttpStatusCode.OK, sseHeaders) }

        val chunks = engine(mock, dispatcher = dispatcher).generate(request).toList()

        assertEquals(AiChunk.Completed(AiStopReason.REFUSAL), chunks.last())
    }

    @Test
    fun `length finish maps to MAX_TOKENS`() = runTest {
        val dispatcher = Dispatchers.IO
        val body = sse(
            """{"choices":[{"delta":{"content":"trunc"}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"length"}]}""",
            "[DONE]",
        )
        val mock = MockEngine { respond(body, HttpStatusCode.OK, sseHeaders) }

        assertEquals(
            AiChunk.Completed(AiStopReason.MAX_TOKENS),
            engine(mock, dispatcher = dispatcher).generate(request).toList().last(),
        )
    }

    // ── Error mapping (each a terminal Failed, never a throw) ─────────────────────

    private suspend fun lastChunkForStatus(status: HttpStatusCode, dispatcher: CoroutineDispatcher, headers: io.ktor.http.Headers = sseHeaders): AiChunk {
        val mock = MockEngine { respond("", status, headers) }
        return engine(mock, dispatcher = dispatcher).generate(request).toList().last()
    }

    @Test
    fun `401 and 403 map to Unauthorized`() = runTest {
        val d = Dispatchers.IO
        assertEquals(AiChunk.Failed(AiError.Unauthorized), lastChunkForStatus(HttpStatusCode.Unauthorized, d))
        assertEquals(AiChunk.Failed(AiError.Unauthorized), lastChunkForStatus(HttpStatusCode.Forbidden, d))
    }

    @Test
    fun `429 maps to RateLimited with Retry-After seconds`() = runTest {
        val d = Dispatchers.IO
        val headers = headersOf(HttpHeaders.RetryAfter, "2")
        val last = lastChunkForStatus(HttpStatusCode.TooManyRequests, d, headers)
        assertEquals(AiChunk.Failed(AiError.RateLimited(2_000L)), last)
    }

    @Test
    fun `429 with no Retry-After yields null hint`() = runTest {
        val d = Dispatchers.IO
        assertEquals(AiChunk.Failed(AiError.RateLimited(null)), lastChunkForStatus(HttpStatusCode.TooManyRequests, d))
    }

    @Test
    fun `5xx maps to ServerError with status code`() = runTest {
        val d = Dispatchers.IO
        assertEquals(AiChunk.Failed(AiError.ServerError(503)), lastChunkForStatus(HttpStatusCode.ServiceUnavailable, d))
    }

    @Test
    fun `other 4xx maps to InvalidRequest`() = runTest {
        val d = Dispatchers.IO
        val last = lastChunkForStatus(HttpStatusCode.BadRequest, d)
        assertTrue(last is AiChunk.Failed && (last as AiChunk.Failed).error is AiError.InvalidRequest)
    }

    @Test
    fun `transport IOException maps to Network`() = runTest {
        val d = Dispatchers.IO
        val mock = MockEngine { throw IOException("connection reset by peer to https://secret.example") }
        val last = engine(mock, dispatcher = d).generate(request).toList().last()
        assertTrue(last is AiChunk.Failed && (last as AiChunk.Failed).error is AiError.Network)
        // detail must not leak the (URL-bearing) exception message
        assertEquals("IOException", ((last as AiChunk.Failed).error as AiError.Network).detail)
    }

    @Test
    fun `UnknownHostException maps to Offline`() = runTest {
        val d = Dispatchers.IO
        val mock = MockEngine { throw UnknownHostException("api.example.com") }
        assertEquals(AiChunk.Failed(AiError.Offline), engine(mock, dispatcher = d).generate(request).toList().last())
    }

    // ── Credentials / config preconditions ───────────────────────────────────────

    @Test
    fun `missing key yields MissingCredentials and opens no socket`() = runTest {
        val d = Dispatchers.IO
        var opened = false
        val mock = MockEngine { opened = true; respond(sse("[DONE]"), HttpStatusCode.OK, sseHeaders) }

        val chunks = engine(mock, store = FakeSecureSecretStore(), dispatcher = d).generate(request).toList()

        assertEquals(listOf(AiChunk.Failed(AiError.MissingCredentials)), chunks)
        assertFalse("no socket should be opened without a key", opened)
    }

    @Test
    fun `secret store failure is treated as missing credentials`() = runTest {
        val d = Dispatchers.IO
        var opened = false
        val store = FakeSecureSecretStore().apply {
            errorToReturn = com.sidr.launcher.domain.result.OperationError.UnknownError("keystore")
        }
        val mock = MockEngine { opened = true; respond(sse("[DONE]"), HttpStatusCode.OK, sseHeaders) }

        val chunks = engine(mock, store = store, dispatcher = d).generate(request).toList()

        assertEquals(listOf(AiChunk.Failed(AiError.MissingCredentials)), chunks)
        assertFalse(opened)
    }

    @Test
    fun `null config yields MissingCredentials and opens no socket`() = runTest {
        val d = Dispatchers.IO
        var opened = false
        val mock = MockEngine { opened = true; respond(sse("[DONE]"), HttpStatusCode.OK, sseHeaders) }

        val chunks = engine(mock, cfg = null, dispatcher = d).generate(request).toList()

        assertEquals(listOf(AiChunk.Failed(AiError.MissingCredentials)), chunks)
        assertFalse(opened)
    }

    @Test
    fun `non-https base URL is rejected with no raw URL in detail`() = runTest {
        val d = Dispatchers.IO
        var opened = false
        val mock = MockEngine { opened = true; respond(sse("[DONE]"), HttpStatusCode.OK, sseHeaders) }
        val insecure = config.copy(baseUrl = "http://insecure.example.com/v1")

        val chunks = engine(mock, cfg = insecure, dispatcher = d).generate(request).toList()

        val failed = chunks.single() as AiChunk.Failed
        val error = failed.error as AiError.InvalidRequest
        assertFalse(error.detail.orEmpty().contains("insecure.example.com"))
        assertFalse(opened)
    }

    // ── Timeouts + cancellation (real dispatcher, real suspension) ───────────────

    @Test
    fun `first-token deadline maps to Timeout`() = runBlocking {
        // A response channel that is opened but never produces a line → first read suspends → timeout.
        val channel = ByteChannel(autoFlush = true)
        val mock = MockEngine { respond(channel as ByteReadChannel, HttpStatusCode.OK, sseHeaders) }

        val chunks = engine(mock, dispatcher = Dispatchers.IO, firstTokenMs = 80L, idleMs = 80L)
            .generate(request).toList()

        assertEquals(listOf(AiChunk.Failed(AiError.Timeout)), chunks)
        channel.close()
    }

    @Test
    fun `first token arriving just under the deadline still completes normally`() = runBlocking {
        // Guards against a future over-tight first-token default: a first token that arrives within the
        // deadline must yield the normal Text…/Completed(COMPLETE) sequence, not a spurious Timeout.
        val channel = ByteChannel(autoFlush = true)
        val writerJob = launch(Dispatchers.IO) {
            delay(150) // well under the 1000ms first-token deadline used below
            channel.writeStringUtf8("""data: {"choices":[{"delta":{"content":"Hi"}}]}""" + "\n\n")
            channel.writeStringUtf8("""data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""" + "\n\n")
            channel.writeStringUtf8("data: [DONE]\n\n")
            channel.flush()
            channel.close()
        }
        val mock = MockEngine { respond(channel as ByteReadChannel, HttpStatusCode.OK, sseHeaders) }

        val chunks = engine(mock, dispatcher = Dispatchers.IO, firstTokenMs = 1_000L, idleMs = 1_000L)
            .generate(request).toList()

        assertEquals(AiChunk.Text("Hi"), chunks.first())
        assertEquals(AiChunk.Completed(AiStopReason.COMPLETE), chunks.last())
        writerJob.join()
    }

    @Test
    fun `cancelling collection aborts the request and emits no terminal`() = runBlocking {
        val firstArrived = CompletableDeferred<Unit>()
        val channel = ByteChannel(autoFlush = true)
        val writerJob = launch(Dispatchers.IO) {
            channel.writeStringUtf8("""data: {"choices":[{"delta":{"content":"Hi"}}]}""" + "\n\n")
            channel.flush()
            delay(60_000) // keep the stream open, never complete
        }
        val mock = MockEngine { respond(channel as ByteReadChannel, HttpStatusCode.OK, sseHeaders) }

        val collected = mutableListOf<AiChunk>()
        val collectJob = launch(Dispatchers.IO) {
            engine(mock, dispatcher = Dispatchers.IO).generate(request).collect { chunk ->
                collected += chunk
                if (chunk is AiChunk.Text) firstArrived.complete(Unit)
            }
        }

        firstArrived.await()
        collectJob.cancelAndJoin()

        assertTrue("expected the streamed text before cancel", collected.any { it is AiChunk.Text })
        assertTrue(
            "cancellation must not be swallowed into a terminal chunk",
            collected.none { it is AiChunk.Completed || it is AiChunk.Failed },
        )
        writerJob.cancelAndJoin()
        channel.close()
    }
}
