package com.sidr.launcher.data.aicloud

import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawArg
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.security.SecretKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ConnectTimeoutException
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
    private val openRouter = config.copy(baseUrl = "https://openrouter.ai/api/v1")
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
        val content = """{"kind":"plan","steps":[{"tool":"launch_app","args":{"app":"lumora","url":null}}],"question":null,"destination":null}"""

        val reply = transport(engine(completion(content))).ask(request)

        assertEquals(
            TransportReply.Answer(
                RawAnswer("plan", listOf(RawStep("launch_app", mapOf("app" to RawArg.Text("lumora"), "url" to null))), null, null),
                ReplyMeta("stop", null, null),
            ),
            reply,
        )
    }

    /**
     * Fork 3b-B: `steps` in order, and an argument that is a reference object read as a reference — never
     * as text. The reply is [GOLDEN_STEPS_WITH_REFERENCE], the golden JSON the JVM transport is held by too
     * (B-13: Task 13 copies it byte for byte into `:consumer:jvm`).
     */
    @Test
    fun `steps and a reference object are read`() = runTest {
        val reply = transport(engine(completion(GOLDEN_STEPS_WITH_REFERENCE))).ask(request)

        assertEquals(
            TransportReply.Answer(
                RawAnswer(
                    kind = "plan",
                    steps = listOf(
                        RawStep("fixture_sieve", mapOf("name" to RawArg.Text("lumora"), "label" to null)),
                        RawStep("fixture_mortar", mapOf("target" to RawArg.Ref(step = 0, key = "kept"), "label" to null)),
                    ),
                    question = null,
                    destination = null,
                ),
                ReplyMeta("stop", null, null),
            ),
            reply,
        )
    }

    /**
     * Controller ruling E8-P4: `from_step` is an integer JSON NUMBER. The string `"0"` is not one (a lenient
     * read would turn the model's text into an index), nor is a fraction, a boolean or `null`. Each makes
     * the reply unreadable — the shape-error class, answered by the one retry — never a guess.
     */
    @Test
    fun `a reference with a non-integer step is unreadable`() = runTest {
        val fromSteps = listOf("\"0\"", "0.5", "true", "null")

        val replies = fromSteps.map { fromStep ->
            val content = """{"kind":"plan","steps":[{"tool":"fixture_mortar","args":{"target":{"from_step":$fromStep,"key":"kept"},"label":null}}],"question":null,"destination":null}"""
            transport(engine(completion(content))).ask(request)
        }

        assertEquals(fromSteps.map { TransportReply.Unparseable(ReplyMeta("stop", null, null)) }, replies)
    }

    /**
     * Task 8's review m2 (R8-m2), pinned at phase 3b's close (final review F4): the rest of ruling E8-P4's
     * strictness. A reference is EXACTLY two fields, its `key` is a JSON string, and a `steps` element is an
     * object — a third field, a numeric key or a bare string where a step belongs is unreadable, never a guess.
     */
    @Test
    fun `a reference with another field, a non-string key or a non-object step is unreadable`() = runTest {
        fun plan(target: String) =
            """{"kind":"plan","steps":[{"tool":"fixture_mortar","args":{"target":$target,"label":null}}],"question":null,"destination":null}"""
        val contents = listOf(
            plan("""{"from_step":0,"key":"kept","note":"x"}"""),
            plan("""{"from_step":0,"key":7}"""),
            """{"kind":"plan","steps":["fixture_mortar"],"question":null,"destination":null}""",
        )

        val replies = contents.map { content -> transport(engine(completion(content))).ask(request) }

        assertEquals(contents.map { TransportReply.Unparseable(ReplyMeta("stop", null, null)) }, replies)
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

    /**
     * The exception the shipped engine really raises, not a stand-in named "…Timeout…" (that would test
     * the substring, not the behaviour). `bridgeHttpClient` sets connect and socket timeouts and no
     * request timeout, so `HttpRequestTimeoutException` cannot arise from it. A reasoning model's silent
     * wait ends inside `HttpURLConnection.getResponseCode()`, and Ktor 3.0.1's Android engine
     * (`timeoutAwareConnection`, read with `javap`) turns that `java.net.SocketTimeoutException` into
     * `ConnectTimeoutException(request, cause)` — exactly the call below, on the request it was given.
     * It is an `IOException`, so without the timeout branch it would read as "no network".
     */
    @Test
    fun `a request that times out is reported as a timeout, not as no network`() = runTest {
        val silent = MockEngine { sent -> throw ConnectTimeoutException(sent, java.net.SocketTimeoutException("Read timed out")) }

        val reply = transport(silent).ask(request)

        assertEquals(TransportReply.Failed(AiError.Timeout), reply)
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

    /**
     * Fork 3b-H, B-16: the one provider the measurement covers gets the cap as `reasoning.max_tokens` — in
     * its own field, and nothing more. (The name has no dot: a JVM method name cannot carry one.)
     */
    @Test
    fun `a reasoning cap is sent to OpenRouter as reasoning max_tokens`() = runTest {
        transport(cfg = openRouter).ask(request.copy(reasoningMaxTokens = 1_234))

        val sent = sentJson()
        assertEquals(setOf("model", "messages", "max_tokens", "stream", "reasoning"), sent.keys)
        assertEquals(Json.parseToJsonElement("""{"max_tokens":1234}"""), sent.getValue("reasoning"))
        assertEquals("512", sent.getValue("max_tokens").jsonPrimitive.content)
    }

    /**
     * A request that carries no cap sends no reasoning object — even to OpenRouter, which then gets the 3a body
     * unchanged. (Production's `BridgeBudget.Default` carries a cap of 2 048 since phase 3b's Task 14, so this is
     * the shape of a budget with the switch off, not of the shipped one.)
     */
    @Test
    fun `no reasoning cap sends no reasoning object`() = runTest {
        transport(cfg = openRouter).ask(request)

        assertEquals(setOf("model", "messages", "max_tokens", "stream"), sentJson().keys)
    }

    /**
     * B-16: an endpoint that does not know the field may refuse the whole request (OpenAI answers an
     * unknown field with 400 → `REQUEST_REJECTED`), so no other provider ever receives it. The HOST is
     * what is compared: a look-alike host and a userinfo prefix are other providers too.
     */
    @Test
    fun `a reasoning cap is never sent to another provider`() = runTest {
        val elsewhere = listOf(
            "https://api.quillmere.example/v1",
            "https://openrouter.ai.quillmere.example/v1",
            "https://openrouter.ai@quillmere.example/v1",
        )

        elsewhere.forEach { baseUrl ->
            transport(cfg = config.copy(baseUrl = baseUrl)).ask(request.copy(reasoningMaxTokens = 1_234))
        }

        assertEquals(elsewhere.size, bodies.size)
        bodies.forEachIndexed { i, body ->
            assertEquals(elsewhere[i], setOf("model", "messages", "max_tokens", "stream"), Json.parseToJsonElement(body).jsonObject.keys)
        }
    }

    /**
     * Owner decision 2026-11-17 (F3): the planner picks the ceiling by [OpenAiCompatibleBridgeTransport.sendsReasoningCap],
     * so it must read the host exactly as the body does — for every host, whether it says «the cap reaches it» is
     * whether the sent body carried `reasoning`.
     */
    @Test
    fun `the ceiling's reading of the host is the body's`() = runTest {
        val hosts = listOf(
            "https://openrouter.ai/api/v1",
            "https://OPENROUTER.AI/api/v1",
            "https://api.quillmere.example/v1",
            "https://openrouter.ai.quillmere.example/v1",
            "https://openrouter.ai@quillmere.example/v1",
        )

        val said = hosts.map { baseUrl ->
            val t = transport(cfg = config.copy(baseUrl = baseUrl))
            t.ask(request.copy(reasoningMaxTokens = 1_234))
            t.sendsReasoningCap()
        }

        val carried = bodies.map { "reasoning" in Json.parseToJsonElement(it).jsonObject }
        assertEquals(listOf(true, true, false, false, false), carried)
        assertEquals(carried, said)
    }

    /** No active config: nothing to read the host of — `false` (the longer ceiling), and no call is made. */
    @Test
    fun `with no active config the cap reaches no host and nothing is called`() = runTest {
        assertFalse(transport(cfg = null).sendsReasoningCap())
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun `the reply's meta carries the finish reason and the reasoning tokens when the provider reports them`() = runTest {
        val usage = ""","usage":{"completion_tokens":321,"completion_tokens_details":{"reasoning_tokens":200}}"""

        val reply = transport(engine(completion(DECLINE, finish = "length", usage = usage))).ask(request)

        assertEquals(ReplyMeta("length", 321, 200), (reply as TransportReply.Answer).meta)
    }

    private companion object {
        const val DECLINE = """{"kind":"decline","steps":null,"question":null,"destination":null}"""

        /**
         * The golden reply of the answer form (A4′ phase 3b, Task 8; B-13, controller ruling E8-P5): two
         * steps, words, nulls and one reference. `:consumer:jvm`'s transport duplicates this module's parse,
         * and Task 13 copies THIS text, byte for byte, into its test — one golden, two parsers.
         */
        const val GOLDEN_STEPS_WITH_REFERENCE =
            """{"kind":"plan","steps":[{"tool":"fixture_sieve","args":{"name":"lumora","label":null}},""" +
                """{"tool":"fixture_mortar","args":{"target":{"from_step":0,"key":"kept"},"label":null}}],""" +
                """"question":null,"destination":null}"""
    }
}
