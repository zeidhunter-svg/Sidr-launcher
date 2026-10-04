package com.sidr.launcher.data.aicloud

import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.security.SecretKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The bridge's transport (spec §7.12.2). `LlmCommandPlanner`'s invariants are carried over by these
 * tests, because its own tests are deleted with it (Task 11): https only, no call without a config or a
 * key, never a throw, cancellation propagates, the key only in the header. And the three forms of the
 * schema switch each send exactly what they say.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiCompatibleBridgeTransportTest {

    private val provider = AiProviderId("openai-compatible")
    private val config = AiProviderConfig(
        providerId = provider,
        baseUrl = "https://api.example.com/v1",
        modelId = AiModelId("fixture-model"),
    )
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val schema = """{"type":"object","additionalProperties":false,"required":["kind"],"properties":{"kind":{"type":"string"}}}"""
    private val request = BridgeRequest(
        system = "FIXTURE-SYSTEM-TEXT",
        schemaJson = schema,
        command = "открой lumora",
        retryNote = null,
        maxOutputTokens = 512,
    )

    private val bodies = mutableListOf<String>()
    private val sentHeaders = mutableListOf<Headers>()

    private fun completion(content: String, finish: String = "stop", usage: String = "") =
        """{"choices":[{"message":{"content":${Json.encodeToString(String.serializer(), content)}},"finish_reason":"$finish"}]$usage}"""

    private fun engine(
        body: String = completion(DECLINE),
        status: HttpStatusCode = HttpStatusCode.OK,
        headers: Headers = jsonHeaders,
    ) = MockEngine { sent ->
        bodies += (sent.body as TextContent).text
        sentHeaders += sent.headers
        respond(body, status, headers)
    }

    private fun seededStore() =
        FakeSecureSecretStore().apply { runBlocking { put(SecretKeys.apiKey(provider), "sk-SNTL-KEY") } }

    private fun transport(
        engine: MockEngine = engine(),
        mode: BridgeTransportMode = BridgeTransportMode.PROMPT_ONLY,
        store: FakeSecureSecretStore = seededStore(),
        cfg: AiProviderConfig? = config,
    ) = OpenAiCompatibleBridgeTransport(HttpClient(engine), store, FakeAiProviderConfigRepository(cfg), Dispatchers.Unconfined, mode)

    private fun sentJson(): JsonObject = Json.parseToJsonElement(bodies.single()).jsonObject

    @Test
    fun `a JSON object answer is read into its fields`() = runTest {
        val content = """{"kind":"plan","step":{"tool":"launch_app","args":{"app":"lumora","url":null}},"question":null,"destination":null}"""

        val reply = transport(engine(completion(content))).ask(request)

        assertEquals(
            TransportReply.Answer(
                RawAnswer("plan", RawStep("launch_app", mapOf("app" to "lumora", "url" to null)), null, null),
                ReplyMeta("stop", null, null),
            ),
            reply,
        )
    }

    @Test
    fun `an answer inside a code fence is read`() = runTest {
        val reply = transport(engine(completion("```json\n$DECLINE\n```"))).ask(request)

        assertEquals("decline", ((reply as TransportReply.Answer).answer.kind))
    }

    @Test
    fun `prose is unparseable, never a guess`() = runTest {
        val reply = transport(engine(completion("I think you want to open Lumora."))).ask(request)

        assertTrue(reply is TransportReply.Unparseable)
    }

    /** Review Focus 4: some upstream failures arrive as HTTP 200 with an error object. */
    @Test
    fun `a 200 that carries an error object is unparseable`() = runTest {
        val reply = transport(engine("""{"error":{"message":"upstream failure","code":502}}""")).ask(request)

        assertEquals(TransportReply.Unparseable(ReplyMeta.NONE), reply)
    }

    @Test
    fun `no active config is a credentials failure and makes no call`() = runTest {
        val reply = transport(cfg = null).ask(request)

        assertEquals(TransportReply.Failed(AiError.MissingCredentials), reply)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `a missing key is a credentials failure and makes no call`() = runTest {
        val reply = transport(store = FakeSecureSecretStore()).ask(request)

        assertEquals(TransportReply.Failed(AiError.MissingCredentials), reply)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `a non-https base url is refused and makes no call`() = runTest {
        val reply = transport(cfg = config.copy(baseUrl = "http://api.example.com/v1")).ask(request)

        assertEquals(TransportReply.Failed(AiError.InvalidRequest(detail = "base_url_not_https")), reply)
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `401 is a credentials failure`() = runTest {
        val reply = transport(engine("{}", HttpStatusCode.Unauthorized)).ask(request)

        assertEquals(TransportReply.Failed(AiError.Unauthorized), reply)
    }

    @Test
    fun `429 is rate limited, with the provider's hint`() = runTest {
        val headers = headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("7"))

        val reply = transport(engine("{}", HttpStatusCode.TooManyRequests, headers)).ask(request)

        assertEquals(TransportReply.Failed(AiError.RateLimited(7_000)), reply)
    }

    @Test
    fun `500 is a server error`() = runTest {
        val reply = transport(engine("{}", HttpStatusCode.InternalServerError)).ask(request)

        assertEquals(TransportReply.Failed(AiError.ServerError(500)), reply)
    }

    @Test
    fun `a network exception is a network failure, never a throw`() = runTest {
        val failing = MockEngine { throw java.io.IOException("boom") }

        val reply = transport(failing).ask(request)

        assertEquals(TransportReply.Failed(AiError.Network(detail = "IOException")), reply)

        // The net covers the whole exchange, not only the call: a schema that does not parse fails in
        // encoding, before any socket, and is a failure — not a throw, and not "no network".
        val broken = transport(mode = BridgeTransportMode.RESPONSE_FORMAT).ask(request.copy(schemaJson = "{"))
        assertTrue("$broken", broken is TransportReply.Failed && broken.error is AiError.Unknown)
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        val cancelled = MockEngine { throw CancellationException("cancelled by the user") }

        try {
            transport(cancelled).ask(request)
            fail("a CancellationException must propagate")
        } catch (expected: CancellationException) {
            // the user's «Отмена» must reach the caller, not become a Failed reply
        }
    }

    @Test
    fun `prompt mode sends the system text and the command, and nothing else`() = runTest {
        transport().ask(request)

        val sent = sentJson()
        assertEquals(setOf("model", "messages", "max_tokens", "stream"), sent.keys)
        assertEquals("fixture-model", sent.getValue("model").jsonPrimitive.content)
        assertEquals(
            listOf("system" to "FIXTURE-SYSTEM-TEXT", "user" to "открой lumora"),
            sent.getValue("messages").jsonArray.map {
                it.jsonObject.getValue("role").jsonPrimitive.content to it.jsonObject.getValue("content").jsonPrimitive.content
            },
        )
        assertEquals("512", sent.getValue("max_tokens").jsonPrimitive.content)
        assertEquals("false", sent.getValue("stream").jsonPrimitive.content)
    }

    @Test
    fun `response-format mode adds the schema as a strict json_schema and nothing more`() = runTest {
        transport(mode = BridgeTransportMode.RESPONSE_FORMAT).ask(request)

        val sent = sentJson()
        assertEquals(setOf("model", "messages", "max_tokens", "stream", "response_format"), sent.keys)
        val format = sent.getValue("response_format").jsonObject
        assertEquals("json_schema", format.getValue("type").jsonPrimitive.content)
        val jsonSchema = format.getValue("json_schema").jsonObject
        assertEquals("sidr_answer", jsonSchema.getValue("name").jsonPrimitive.content)
        assertEquals(JsonPrimitive(true), jsonSchema.getValue("strict"))
        assertEquals(Json.parseToJsonElement(schema), jsonSchema.getValue("schema"))
    }

    @Test
    fun `forced-tool mode sends the schema as the only tool and reads the answer from its call`() = runTest {
        val toolCall = """{"choices":[{"message":{"tool_calls":[{"type":"function","function":{"name":"submit_answer","arguments":${Json.encodeToString(String.serializer(), DECLINE)}}}]},"finish_reason":"tool_calls"}]}"""

        val reply = transport(engine(toolCall), mode = BridgeTransportMode.FORCED_TOOL).ask(request)

        assertEquals("decline", (reply as TransportReply.Answer).answer.kind)
        val sent = sentJson()
        val function = (sent.getValue("tools") as JsonArray).single().jsonObject.getValue("function").jsonObject
        assertEquals("submit_answer", function.getValue("name").jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement(schema), function.getValue("parameters"))
        assertEquals(
            "submit_answer",
            sent.getValue("tool_choice").jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content,
        )
    }

    @Test
    fun `a retry adds only the static note after the command`() = runTest {
        transport().ask(request.copy(retryNote = "FIXTURE-RETRY-NOTE"))

        assertEquals(
            listOf("system" to "FIXTURE-SYSTEM-TEXT", "user" to "открой lumora", "system" to "FIXTURE-RETRY-NOTE"),
            sentJson().getValue("messages").jsonArray.map {
                it.jsonObject.getValue("role").jsonPrimitive.content to it.jsonObject.getValue("content").jsonPrimitive.content
            },
        )
    }

    /** `DOC-AML-3`, carried over: the key is in the Authorization header and nowhere else. */
    @Test
    fun `the key is sent only in the header and never in the body or a failure`() = runTest {
        transport().ask(request)
        val refused = transport(engine("{}", HttpStatusCode.Unauthorized)).ask(request)

        assertEquals("Bearer sk-SNTL-KEY", sentHeaders.first()[HttpHeaders.Authorization])
        assertFalse(bodies.any { it.contains("sk-SNTL-KEY") })
        assertFalse(refused.toString().contains("sk-SNTL-KEY"))
    }

    @Test
    fun `the reply's meta carries the finish reason and the reasoning tokens when the provider reports them`() = runTest {
        val usage = ""","usage":{"completion_tokens":321,"completion_tokens_details":{"reasoning_tokens":200}}"""

        val reply = transport(engine(completion(DECLINE, finish = "length", usage = usage))).ask(request)

        assertEquals(ReplyMeta("length", 321, 200), (reply as TransportReply.Answer).meta)
    }

    private companion object {
        const val DECLINE = """{"kind":"decline","step":null,"question":null,"destination":null}"""
    }
}
