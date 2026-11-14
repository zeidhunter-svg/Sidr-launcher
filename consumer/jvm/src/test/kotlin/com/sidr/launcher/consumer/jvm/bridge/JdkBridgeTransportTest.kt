package com.sidr.launcher.consumer.jvm.bridge

import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawArg
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The JVM consumer's bridge transport (A4′ phase 3b, Task 13; plan §0.2 B-13): `PROMPT_ONLY`, behind the
 * [BridgeHttpSender] seam. Every test here uses a FAKE sender — no test reads the environment or touches
 * the network; a live JVM run is never in a gate (B-13). The wire parse is duplicated from `:data:ai-cloud`
 * and held by the same golden reply ([GOLDEN_STEPS_WITH_REFERENCE]).
 */
class JdkBridgeTransportTest {

    private val request = BridgeRequest(
        system = "FIXTURE-SYSTEM-TEXT",
        schemaJson = "{}",
        command = "find orvelle.txt",
        retryNote = null,
        maxOutputTokens = 512,
    )

    /** Every call the transport made: url, body, key. */
    private val sent = mutableListOf<Triple<String, String, String>>()

    private fun sender(body: String, status: Int = 200) = BridgeHttpSender { url, sentBody, key ->
        sent += Triple(url, sentBody, key)
        status to body
    }

    private fun transport(
        sender: BridgeHttpSender,
        baseUrl: String? = "https://api.example.com/v1",
        model: String? = "fixture-model",
        key: String? = "sk-SNTL-KEY",
    ) = JdkBridgeTransport(baseUrl = baseUrl, model = model, key = key, sender = sender)

    private fun completion(content: String, finish: String = "stop") =
        """{"choices":[{"message":{"content":${Json.encodeToString(String.serializer(), content)}},"finish_reason":"$finish"}]}"""

    /**
     * Fork 3b-B, B-13: the golden reply, read field for field into exactly the value
     * `OpenAiCompatibleBridgeTransportTest > steps and a reference object are read` asserts — one golden,
     * two parsers. And the one request it answers, held whole (fix round 1, review m1): the body is exactly
     * the `PROMPT_ONLY` form of the [BridgeRequest]'s parts — the request carries a reasoning cap, and the body
     * has none, because the host is not OpenRouter (B-16) — and the key reached the sender's key argument
     * only, never the body or the URL.
     */
    @Test
    fun `a prompt-only answer with steps and a reference is read`() = runTest {
        val reply = transport(sender(completion(GOLDEN_STEPS_WITH_REFERENCE))).ask(request.copy(reasoningMaxTokens = 256))

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
        val (url, body, key) = sent.single()
        assertEquals("https://api.example.com/v1/chat/completions", url)
        assertEquals(
            Json.parseToJsonElement(
                """{"model":"fixture-model","messages":[{"role":"system","content":"FIXTURE-SYSTEM-TEXT"},""" +
                    """{"role":"user","content":"find orvelle.txt"}],"max_tokens":512,"stream":false}""",
            ),
            Json.parseToJsonElement(body),
        )
        assertEquals("sk-SNTL-KEY", key)
        assertFalse("the key is not in the body", body.contains("sk-SNTL-KEY"))
        assertFalse("the key is not in the url", url.contains("sk-SNTL-KEY"))
    }

    /**
     * Fix round 1 (review m2): production's sender refuses plain HTTP on its own, before anything is built or
     * sent — the property does not rest on [JdkBridgeTransport]'s check alone. The URL is loopback on a port
     * nothing serves, so even a sender that lost its refusal could reach no provider.
     */
    @Test
    fun `the production sender refuses a non-https url before any request`() = runTest {
        val refused = runCatching { JdkHttpSender(timeoutMs = 1_000L).send("http://127.0.0.1:1/v1/chat/completions", "{}", "sk-SNTL-KEY") }
            .exceptionOrNull()

        assertEquals(IllegalArgumentException::class, refused?.let { it::class })
        assertEquals("refused before sending: the bridge sends over https only", refused?.message)
    }

    @Test
    fun `no key means nothing is sent`() = runTest {
        val keys = listOf(null, "", "   ")

        val replies = keys.map { key -> transport(sender(completion(DECLINE)), key = key).ask(request) }

        assertEquals(keys.map { TransportReply.Failed(AiError.MissingCredentials) }, replies)
        assertEquals(emptyList<Triple<String, String, String>>(), sent)
    }

    @Test
    fun `a non-https base url is refused before sending`() = runTest {
        val reply = transport(sender(completion(DECLINE)), baseUrl = "http://api.example.com/v1").ask(request)

        assertEquals(TransportReply.Failed(AiError.InvalidRequest(detail = "base_url_not_https")), reply)
        assertEquals(emptyList<Triple<String, String, String>>(), sent)
    }

    /** Review Focus 4 of phase 3a, as `:data:ai-cloud` holds it: some upstream failures arrive as HTTP 200. */
    @Test
    fun `a 200 carrying an error object is unreadable`() = runTest {
        val reply = transport(sender("""{"error":{"message":"upstream failure","code":502}}""")).ask(request)

        assertEquals(TransportReply.Unparseable(ReplyMeta.NONE), reply)
    }

    private companion object {
        const val DECLINE = """{"kind":"decline","steps":null,"question":null,"destination":null}"""

        /**
         * Copied byte for byte from `:data:ai-cloud`'s
         * `OpenAiCompatibleBridgeTransportTest.GOLDEN_STEPS_WITH_REFERENCE` (A4′ phase 3b, Task 8; B-13,
         * controller ruling R13-2): two steps, words, nulls and one reference. The two copies are held equal
         * by review, not by a guard — a drift between them is a residual, named in the Task 13 report.
         */
        const val GOLDEN_STEPS_WITH_REFERENCE =
            """{"kind":"plan","steps":[{"tool":"fixture_sieve","args":{"name":"lumora","label":null}},""" +
                """{"tool":"fixture_mortar","args":{"target":{"from_step":0,"key":"kept"},"label":null}}],""" +
                """"question":null,"destination":null}"""
    }
}
