package com.sidr.launcher.consumer.jvm.bridge

import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.bridge.BridgeRendering
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawArg
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * The one seam between the JVM transport and the network (A4′ phase 3b, plan §0.2 B-13): one POST of
 * [body] to [url] with [key] as the bearer, answered by the HTTP status and the body text. Tests pass a
 * fake; production passes [JdkHttpSender]. A live JVM run is never in a gate.
 */
fun interface BridgeHttpSender {
    suspend fun send(url: String, body: String, key: String): Pair<Int, String>
}

/**
 * Production's [BridgeHttpSender], over the JDK's own `java.net.http` — nothing Android, no new
 * dependency (`ModuleIsolationTest`). **It refuses a non-`https://` URL before anything is sent** (B-13),
 * independently of [JdkBridgeTransport]'s own refusal, so the property does not rest on one caller. It
 * follows no redirect (`HttpClient`'s default `NEVER`), so an `https://` endpoint cannot bounce the
 * request to plain HTTP. The client is built on the first send, so a harness that never asks the model
 * starts no HTTP machinery.
 *
 * [timeoutMs] bounds each request; `Main.kt` passes the bridge's LONGEST per-host ceiling
 * (`BridgeBudget.longestCeilingMs`), as `:data:ai-cloud` bounds its socket by it (`bridgeHttpClient`) — never
 * the capped one, which would cut another host's longer wait. **Unproven by any test** — no test touches the network (named, not
 * hidden: it is the one wiring here only a live run would show).
 */
class JdkHttpSender(private val timeoutMs: Long) : BridgeHttpSender {

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS)).build()
    }

    override suspend fun send(url: String, body: String, key: String): Pair<Int, String> {
        require(url.startsWith("https://", ignoreCase = true)) { "refused before sending: the bridge sends over https only" }
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        // On cancellation `await` stops waiting at once and calls `future.cancel(false)` on the request's future:
        // the caller is released, but the JDK client is not asked to abort the exchange, which may keep running
        // in the background until [timeoutMs] ends it. Cancelling stops the wait, not necessarily the request.
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        return response.statusCode() to response.body()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
    }
}

/**
 * The JVM consumer's [BridgeTransport] (A4′ phase 3b, Task 13; plan §0.2 B-13) over any OpenAI-compatible
 * `chat/completions` endpoint — `:data:ai-cloud`'s `OpenAiCompatibleBridgeTransport` in its `PROMPT_ONLY`
 * form only, with the same invariants:
 *  - `https://` only; with no endpoint, no model or no key — **not one call** to [sender];
 *  - never throws — the whole exchange is inside one net; `CancellationException` is rethrown;
 *  - the key goes only to [sender] (the `Authorization` header in production), never into the body or a
 *    reply; it is never logged.
 * The body is exactly the [BridgeRequest]'s parts — the system text, the command, the optional static retry
 * note and the generation limits; the reasoning cap only to OpenRouter's host (B-16), never to another.
 * Errors use the assistant engine's vocabulary ([AiError]).
 *
 * **The wire parse is a DUPLICATE of `:data:ai-cloud`'s** (B-13: this module may depend on `:domain` only),
 * read strictly — one JSON object; `steps` an array of step objects; each argument words, `null`, or a
 * reference of exactly `from_step` (an integer JSON number, controller ruling E8-P4) and `key` (a string).
 * Both copies are held by the same golden reply, `GOLDEN_STEPS_WITH_REFERENCE`, copied byte for byte into
 * `JdkBridgeTransportTest`; a drift between the two copies is caught by nothing but review.
 *
 * **One recorded difference:** [BridgeHttpSender] answers a status and a body, not headers, so a 429 here is
 * [AiError.RateLimited] with no `Retry-After` hint, where `:data:ai-cloud` carries the provider's.
 *
 * [baseUrl], [model] and [key] are what `Main.kt` read from `SIDR_JVM_BASE_URL`, `SIDR_JVM_MODEL` and
 * `SIDR_JVM_API_KEY`; this class reads no environment.
 */
class JdkBridgeTransport(
    private val baseUrl: String?,
    private val model: String?,
    private val key: String?,
    private val sender: BridgeHttpSender,
) : BridgeTransport {

    override suspend fun ask(request: BridgeRequest): TransportReply = try {
        exchange(request)
    } catch (e: CancellationException) {
        throw e // never swallow the user's cancel
    } catch (e: Exception) {
        // As `:data:ai-cloud`: a failure of the network is a network failure; anything else is not the
        // network, and saying "no network" would send the user to the wrong place.
        val name = e::class.simpleName.orEmpty()
        TransportReply.Failed(
            when {
                name.contains("Timeout") -> AiError.Timeout
                e is java.io.IOException -> AiError.Network(detail = name)
                else -> AiError.Unknown(detail = name)
            },
        )
    }

    /** As `:data:ai-cloud`: the planner's ceiling reads the host through the same [acceptsReasoningCap] the body does. */
    override suspend fun sendsReasoningCap(): Boolean = baseUrl?.let(::acceptsReasoningCap) == true

    private suspend fun exchange(request: BridgeRequest): TransportReply {
        // No endpoint or no model is "no active config" — the credentials failure `:data:ai-cloud` answers.
        val endpoint = baseUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return TransportReply.Failed(AiError.MissingCredentials)
        val modelId = model?.trim()?.takeIf { it.isNotEmpty() } ?: return TransportReply.Failed(AiError.MissingCredentials)
        if (!endpoint.startsWith("https://", ignoreCase = true)) {
            return TransportReply.Failed(AiError.InvalidRequest(detail = "base_url_not_https"))
        }
        val secret = key?.trim()?.takeIf { it.isNotEmpty() } ?: return TransportReply.Failed(AiError.MissingCredentials)

        // B-16: the reasoning cap reaches OpenRouter only; for any other host it is dropped here, unsent.
        val reasoningCap = request.reasoningMaxTokens?.takeIf { acceptsReasoningCap(endpoint) }
        val (status, text) = sender.send(endpoint.removeSuffix("/") + "/chat/completions", encode(request, modelId, reasoningCap), secret)
        if (status !in 200..299) return TransportReply.Failed(statusError(status))
        return decode(text)
    }

    private fun encode(request: BridgeRequest, model: String, reasoningCap: Int?): String = buildJsonObject {
        put("model", model)
        putJsonArray("messages") {
            addJsonObject {
                put("role", "system")
                put("content", request.system)
            }
            addJsonObject {
                put("role", "user")
                put("content", request.command)
            }
            request.retryNote?.let { note -> addJsonObject { put("role", "system"); put("content", note) } }
        }
        put("max_tokens", request.maxOutputTokens)
        put("stream", false)
        reasoningCap?.let { cap -> putJsonObject("reasoning") { put("max_tokens", cap) } }
    }.toString()

    private fun decode(bodyText: String): TransportReply {
        val root = runCatching { strict.parseToJsonElement(bodyText).jsonObject }.getOrNull()
            ?: return TransportReply.Unparseable(ReplyMeta.NONE)
        val choice = (root["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val message = choice?.get("message") as? JsonObject
        val usage = root["usage"] as? JsonObject
        val meta = ReplyMeta(
            finishReason = (choice?.get("finish_reason") as? JsonPrimitive)?.contentOrNull,
            completionTokens = (usage?.get("completion_tokens") as? JsonPrimitive)?.intOrNull,
            reasoningTokens = ((usage?.get("completion_tokens_details") as? JsonObject)
                ?.get("reasoning_tokens") as? JsonPrimitive)?.intOrNull,
        )
        val text = (message?.get("content") as? JsonPrimitive)?.contentOrNull ?: return TransportReply.Unparseable(meta)
        return parseAnswer(text)?.let { TransportReply.Answer(it, meta) } ?: TransportReply.Unparseable(meta)
    }

    /**
     * Strict: after trimming and at most one code fence, the text must be ONE JSON object. `steps` absent
     * or `null` is no steps; otherwise it must be an array of objects — anything else is unreadable.
     */
    private fun parseAnswer(text: String): RawAnswer? = runCatching {
        val obj = strict.parseToJsonElement(unfenced(text.trim())).jsonObject
        val steps = when (val raw = obj["steps"]) {
            null, JsonNull -> null
            is JsonArray -> raw.map { element -> stepOf(element as? JsonObject ?: return null) }
            else -> return null
        }
        RawAnswer(
            kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull,
            steps = steps,
            question = (obj["question"] as? JsonPrimitive)?.contentOrNull,
            destination = (obj["destination"] as? JsonPrimitive)?.contentOrNull,
        )
    }.getOrNull()

    private fun stepOf(raw: JsonObject): RawStep = RawStep(
        tool = (raw["tool"] as? JsonPrimitive)?.contentOrNull,
        args = (raw["args"] as? JsonObject)?.mapValues { (_, value) -> argValue(value) } ?: emptyMap(),
    )

    /**
     * A string, a number or a boolean is read as text; `null` is absent; an object is a reference or
     * unreadable ([referenceOf]); anything else (an array) is unreadable.
     */
    private fun argValue(value: JsonElement): RawArg? = when (value) {
        JsonNull -> null
        is JsonPrimitive -> RawArg.Text(value.content)
        is JsonObject -> referenceOf(value)
        else -> throw IllegalArgumentException("an argument must be a value, a reference or null")
    }

    /**
     * Controller ruling E8-P4: a reference is EXACTLY an object of two fields — `from_step`, an integer JSON
     * number (never the string `"0"`, never a fraction), and `key`, a JSON string. Anything else is
     * unreadable. Whether the step and the key may stand is `BridgeValidator`'s judgement, not this one's.
     */
    private fun referenceOf(obj: JsonObject): RawArg.Ref {
        require(obj.keys == setOf(BridgeRendering.REF_FROM_STEP, BridgeRendering.REF_KEY)) { "a reference has exactly two fields" }
        val step = (obj[BridgeRendering.REF_FROM_STEP] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toIntOrNull()
        val key = (obj[BridgeRendering.REF_KEY] as? JsonPrimitive)?.takeIf { it.isString }?.content
        requireNotNull(step) { "a reference's step is an integer number" }
        requireNotNull(key) { "a reference's key is a string" }
        return RawArg.Ref(step, key)
    }

    private fun unfenced(text: String): String {
        if (!text.startsWith("```")) return text
        return text.substringAfter('\n', missingDelimiterValue = "").substringBeforeLast("```").trim()
    }

    private fun statusError(code: Int): AiError = when {
        code == 401 || code == 403 -> AiError.Unauthorized
        code == 429 -> AiError.RateLimited(null) // no headers through the seam: no Retry-After hint
        code in 500..599 -> AiError.ServerError(code)
        code in 400..499 -> AiError.InvalidRequest(detail = "http_$code")
        else -> AiError.Unknown(detail = "http_$code")
    }

    companion object {
        /** The environment `Main.kt` reads (controller ruling R13-3); B-13 names the key's. */
        const val ENV_API_KEY = "SIDR_JVM_API_KEY"
        const val ENV_BASE_URL = "SIDR_JVM_BASE_URL"
        const val ENV_MODEL = "SIDR_JVM_MODEL"

        /** The one host the reasoning cap is sent to (B-16) — `:data:ai-cloud`'s, duplicated with its parse. */
        const val REASONING_CAP_HOST = "openrouter.ai"

        /** B-16, as `:data:ai-cloud` reads it: the host PARSED and compared whole, case ignored; unparseable is not OpenRouter. */
        fun acceptsReasoningCap(baseUrl: String): Boolean =
            runCatching { URI(baseUrl.trim()).host }.getOrNull()?.equals(REASONING_CAP_HOST, ignoreCase = true) == true

        private val strict = Json { isLenient = false }
    }
}
