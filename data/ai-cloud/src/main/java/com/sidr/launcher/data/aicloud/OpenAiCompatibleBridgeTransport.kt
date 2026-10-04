package com.sidr.launcher.data.aicloud

import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.domain.security.SecureSecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

/**
 * How the answer's shape reaches the provider (D2, b′; spec §7.12.2). The shape is the same in all
 * three — strict-compatible, generated with the prompt — and only the CHANNEL differs:
 *  - [PROMPT_ONLY] — the prompt describes it, nothing else is sent (the default until the measurement);
 *  - [RESPONSE_FORMAT] — `response_format: json_schema, strict`;
 *  - [FORCED_TOOL] — one tool whose parameters are the schema, forced by `tool_choice`.
 * The safety of the bridge does not rest on this choice: the validator judges every answer the same.
 */
enum class BridgeTransportMode { PROMPT_ONLY, RESPONSE_FORMAT, FORCED_TOOL }

/**
 * The bridge's own HTTP client — not the shared cloud one. That client's 30 s socket timeout
 * (`AiCloudProvidesModule`) would cut a non-streaming reasoning model's silent wait before the bridge's
 * ceiling, and the spec names the ceiling, not the socket, as the bound (§7.12.2). [socketTimeoutMs] is
 * therefore the ceiling in production (`BridgeProvidesModule`) and the measurement's cap in the harness.
 */
fun bridgeHttpClient(socketTimeoutMs: Long): HttpClient = HttpClient(Android) {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = socketTimeoutMs
    }
}

/**
 * The bridge's [BridgeTransport] over any OpenAI-compatible `chat/completions` endpoint — its OWN
 * non-streaming path, not `GenerativeAiEngine` (merging the assistant's port with the planner's is
 * change-control §5). It carries `LlmCommandPlanner`'s invariants, by requirement and by
 * `OpenAiCompatibleBridgeTransportTest`, since that class and its tests are deleted (Task 11):
 *  - `https://` only; with no active config or no key — **not one network call**;
 *  - never throws — the WHOLE exchange (config, key, encoding, call, decoding) is inside one net, not
 *    only the call; `CancellationException` is rethrown, so the user's «Отмена» cancels the request;
 *  - the key comes only from [SecureSecretStore], goes only into the `Authorization` header, and is
 *    never logged nor put in a reply (`DOC-AML-3`).
 * The body is exactly the [BridgeRequest]'s parts in the mode's form; this class adds nothing of the
 * device. Errors use the assistant engine's vocabulary ([AiError]) so the two surfaces stop disagreeing.
 */
class OpenAiCompatibleBridgeTransport(
    private val httpClient: HttpClient,
    private val secretStore: SecureSecretStore,
    private val configRepository: AiProviderConfigRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val mode: BridgeTransportMode = BridgeTransportMode.PROMPT_ONLY,
) : BridgeTransport {

    override suspend fun ask(request: BridgeRequest): TransportReply = withContext(ioDispatcher) {
        try {
            exchange(request)
        } catch (e: CancellationException) {
            throw e // never swallow the user's cancel
        } catch (e: Exception) {
            // A failure of the network is a network failure; anything else — a config flow that throws,
            // a schema that does not parse — is not the network, and saying "no network" would send the
            // user to the wrong place.
            val name = e::class.simpleName.orEmpty()
            TransportReply.Failed(
                when {
                    name.contains("Timeout") -> AiError.Timeout
                    e is java.io.IOException -> AiError.Network(detail = name)
                    else -> AiError.Unknown(detail = name)
                },
            )
        }
    }

    private suspend fun exchange(request: BridgeRequest): TransportReply {
        val config = configRepository.activeConfig().first()
            ?: return TransportReply.Failed(AiError.MissingCredentials)
        val baseUrl = config.baseUrl.trim()
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            return TransportReply.Failed(AiError.InvalidRequest(detail = "base_url_not_https"))
        }
        val key = when (val stored = secretStore.get(SecretKeys.apiKey(config.providerId))) {
            is OperationResult.Success -> stored.value?.trim()?.takeIf { it.isNotEmpty() }
            is OperationResult.Failure -> null
        } ?: return TransportReply.Failed(AiError.MissingCredentials)

        val body = encode(request, config.modelId.value.trim())
        val response = httpClient.post(baseUrl.removeSuffix("/") + "/chat/completions") {
            header(HttpHeaders.Authorization, "Bearer $key")
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            return TransportReply.Failed(statusError(response.status.value, response.headers[HttpHeaders.RetryAfter]))
        }
        return decode(response.bodyAsText())
    }

    private fun encode(request: BridgeRequest, model: String): String = buildJsonObject {
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
        when (mode) {
            BridgeTransportMode.PROMPT_ONLY -> Unit
            BridgeTransportMode.RESPONSE_FORMAT -> putJsonObject("response_format") {
                put("type", "json_schema")
                putJsonObject("json_schema") {
                    put("name", SCHEMA_NAME)
                    put("strict", true)
                    put("schema", Json.parseToJsonElement(request.schemaJson))
                }
            }
            BridgeTransportMode.FORCED_TOOL -> {
                putJsonArray("tools") {
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", TOOL_NAME)
                            put("description", TOOL_DESCRIPTION)
                            put("strict", true)
                            put("parameters", Json.parseToJsonElement(request.schemaJson))
                        }
                    }
                }
                putJsonObject("tool_choice") {
                    put("type", "function")
                    putJsonObject("function") { put("name", TOOL_NAME) }
                }
            }
        }
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
        val carrier = when (mode) {
            BridgeTransportMode.FORCED_TOOL -> (((message?.get("tool_calls") as? JsonArray)?.firstOrNull() as? JsonObject)
                ?.get("function") as? JsonObject)?.get("arguments") as? JsonPrimitive
            BridgeTransportMode.PROMPT_ONLY, BridgeTransportMode.RESPONSE_FORMAT -> message?.get("content") as? JsonPrimitive
        }
        val text = carrier?.contentOrNull ?: return TransportReply.Unparseable(meta)
        return parseAnswer(text)?.let { TransportReply.Answer(it, meta) } ?: TransportReply.Unparseable(meta)
    }

    /** Strict: after trimming and at most one code fence, the text must be ONE JSON object. */
    private fun parseAnswer(text: String): RawAnswer? = runCatching {
        val obj = strict.parseToJsonElement(unfenced(text.trim())).jsonObject
        val step = when (val raw = obj["step"]) {
            null, JsonNull -> null
            is JsonObject -> RawStep(
                tool = (raw["tool"] as? JsonPrimitive)?.contentOrNull,
                args = (raw["args"] as? JsonObject)?.mapValues { (_, value) -> argValue(value) } ?: emptyMap(),
            )
            else -> return null
        }
        RawAnswer(
            kind = (obj["kind"] as? JsonPrimitive)?.contentOrNull,
            step = step,
            question = (obj["question"] as? JsonPrimitive)?.contentOrNull,
            destination = (obj["destination"] as? JsonPrimitive)?.contentOrNull,
        )
    }.getOrNull()

    /** A string, a number or a boolean is read as text; `null` is absent; anything else is unreadable. */
    private fun argValue(value: JsonElement): String? = when (value) {
        JsonNull -> null
        is JsonPrimitive -> value.content
        else -> throw IllegalArgumentException("an argument must be a value or null")
    }

    private fun unfenced(text: String): String {
        if (!text.startsWith("```")) return text
        return text.substringAfter('\n', missingDelimiterValue = "").substringBeforeLast("```").trim()
    }

    private fun statusError(code: Int, retryAfter: String?): AiError = when {
        code == 401 || code == 403 -> AiError.Unauthorized
        code == 429 -> AiError.RateLimited(retryAfter?.trim()?.toLongOrNull()?.times(1_000))
        code in 500..599 -> AiError.ServerError(code)
        code in 400..499 -> AiError.InvalidRequest(detail = "http_$code")
        else -> AiError.Unknown(detail = "http_$code")
    }

    companion object {
        const val SCHEMA_NAME = "sidr_answer"
        const val TOOL_NAME = "submit_answer"
        const val TOOL_DESCRIPTION = "Submit your one answer in the required form."

        private val strict = Json { isLenient = false }
    }
}
