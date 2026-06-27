package com.sidr.launcher.data.aicloud

import com.sidr.launcher.domain.ai.AiChunk
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.AiRequest
import com.sidr.launcher.domain.ai.AiStopReason
import com.sidr.launcher.domain.ai.AiUsage
import com.sidr.launcher.domain.ai.GenerativeAiEngine
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.domain.security.SecureSecretStore
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

/**
 * [GenerativeAiEngine] for any **OpenAI-compatible** `chat/completions` endpoint (OpenRouter,
 * Google's OpenAI endpoint, Together, Groq, local Ollama / LM Studio, OpenAI itself). Provider-neutral
 * by construction: the base URL + free-text model come from [AiProviderConfigRepository] and the key
 * from [SecureSecretStore] — there is no per-vendor code, no hardcoded model, no fixed model list.
 *
 * Streaming is SSE, parsed **manually** over the response channel (so no `ktor-client-sse` dep). The
 * wire stream maps onto the unchanged domain [AiChunk] / [AiError] / [AiStopReason] contracts:
 * - expected failures are emitted as a terminal [AiChunk.Failed] and the flow then completes normally
 *   — they are never thrown to the collector;
 * - [CancellationException] is **not** caught/converted — it propagates so collection-cancel aborts
 *   the in-flight Ktor request.
 *
 * Nothing here logs the key, the Authorization header, the request body, or response text;
 * [AiError.detail] carries only a short safe diagnostic (a status hint / parser note).
 */
class OpenAiCompatibleGenerativeAiEngine(
    private val httpClient: HttpClient,
    private val secretStore: SecureSecretStore,
    private val configRepository: AiProviderConfigRepository,
    private val ioDispatcher: CoroutineDispatcher,
    /**
     * Transport deadline for the **first** SSE line. This is a *network* deadline — deliberately
     * decoupled from the `< 2000ms` first-token figure in the perf budget, which is Block-N **UI
     * guidance** about choosing a light/fast model, NOT a network limit. Real providers (OpenRouter
     * routing/queueing, free tiers, local Ollama / LM Studio cold start) routinely take 5–30 s to
     * first token, so this is generous; see [DEFAULT_FIRST_TOKEN_TIMEOUT_MS].
     */
    private val firstTokenTimeoutMs: Long = DEFAULT_FIRST_TOKEN_TIMEOUT_MS,
    /**
     * Transport deadline for the silence **between** chunks once streaming has started. Also a network
     * deadline. The injected client's `socketTimeoutMillis` (inactivity between packets) must stay
     * `>=` this so the manual deadline — not Ktor — owns the semantics; see [DEFAULT_IDLE_TIMEOUT_MS].
     */
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
) : GenerativeAiEngine {

    override fun generate(request: AiRequest): Flow<AiChunk> = flow {
        // Resolve the non-secret config. No active config → not set up → MissingCredentials (routes
        // to a "set up provider" CTA in Block N, same as a missing key).
        val config = configRepository.activeConfig().first()
        if (config == null) {
            emit(AiChunk.Failed(AiError.MissingCredentials))
            return@flow
        }

        // Sanitize user-supplied strings at the boundary (a trailing newline/space in a pasted key
        // causes a baffling 401; some clients also reject illegal header chars). Keep trimmed values
        // out of logs all the same.
        val baseUrl = config.baseUrl.trim()
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            // HTTPS-only egress (app network-security-config also forbids cleartext). No raw URL in detail.
            emit(AiChunk.Failed(AiError.InvalidRequest(detail = "base_url_not_https")))
            return@flow
        }

        val key = when (val stored = secretStore.get(SecretKeys.apiKey(config.providerId))) {
            is OperationResult.Success -> stored.value?.trim()?.takeIf { it.isNotEmpty() }
            is OperationResult.Failure -> null // a store failure is treated as no usable key
        }
        if (key == null) {
            emit(AiChunk.Failed(AiError.MissingCredentials)) // don't even open the socket
            return@flow
        }

        val model = (request.model ?: config.modelId).value.trim()
        val url = chatCompletionsUrl(baseUrl)
        val body = json.encodeToString(request.toChatRequest(model))

        try {
            httpClient.preparePost(url) {
                header(HttpHeaders.Authorization, "Bearer $key")
                contentType(ContentType.Application.Json)
                accept(ContentType.Text.EventStream)
                setBody(body)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    emit(AiChunk.Failed(mapHttpError(response)))
                    return@execute
                }
                streamSse(response)
            }
        } catch (e: CancellationException) {
            throw e // never swallow cancellation — let collection-cancel abort the request
        } catch (e: UnknownHostException) {
            emit(AiChunk.Failed(AiError.Offline))
        } catch (e: ConnectException) {
            emit(AiChunk.Failed(AiError.Offline))
        } catch (e: IOException) {
            emit(AiChunk.Failed(AiError.Network(detail = e::class.simpleName)))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emit(AiChunk.Failed(AiError.Unknown(detail = e::class.simpleName)))
        }
    }.flowOn(ioDispatcher)

    /**
     * Reads the SSE body line-by-line, emitting [AiChunk.Text] for each non-empty `delta.content`
     * and exactly one terminal [AiChunk.Completed] at `[DONE]` / end-of-body. Per-read deadlines
     * (first-token, then idle) map to [AiError.Timeout]. `finish_reason` / final `usage` are captured
     * even off a content-empty terminal delta (we don't skip past it).
     */
    private suspend fun FlowCollector<AiChunk>.streamSse(response: HttpResponse) {
        val channel = response.bodyAsChannel()
        var firstRead = true
        var stopReason: AiStopReason? = null
        var refused = false // a refusal, once seen, wins over any later finish_reason
        var usage: AiUsage? = null

        while (true) {
            val deadline = if (firstRead) firstTokenTimeoutMs else idleTimeoutMs
            var line: String? = null
            // withTimeoutOrNull returns null ONLY on timeout; a clean EOF leaves `line == null` but
            // returns non-null (the lambda completed), so the two are distinguishable.
            val completed = withTimeoutOrNull(deadline) { line = channel.readUTF8Line() }
            if (completed == null) {
                emit(AiChunk.Failed(AiError.Timeout))
                return
            }
            firstRead = false
            val current = line ?: break // clean end of stream

            if (current.isBlank()) continue
            if (!current.startsWith("data:")) continue // ignore event:/id:/`:` comment lines
            val payload = current.removePrefix("data:").trim()
            if (payload == DONE_MARKER) break

            val chunk = runCatching { json.decodeFromString<StreamChunk>(payload) }.getOrNull()
                ?: continue // skip a single unparseable line; don't tear down the whole stream
            val choice = chunk.choices.firstOrNull()

            // Capture terminal metadata BEFORE the empty-content skip — the terminal delta usually
            // carries finish_reason with empty content.
            choice?.finishReason?.let { stopReason = mapStopReason(it) }
            if (!choice?.delta?.refusal.isNullOrEmpty()) refused = true
            chunk.usage?.let { usage = AiUsage(inputTokens = it.promptTokens, outputTokens = it.completionTokens) }

            val delta = choice?.delta?.content
            if (!delta.isNullOrEmpty()) emit(AiChunk.Text(delta))
        }

        val terminal = if (refused) AiStopReason.REFUSAL else stopReason ?: AiStopReason.COMPLETE
        emit(AiChunk.Completed(terminal, usage))
    }

    private fun mapHttpError(response: HttpResponse): AiError {
        val code = response.status.value
        return when {
            code == 401 || code == 403 -> AiError.Unauthorized
            code == 429 -> AiError.RateLimited(parseRetryAfterMs(response.headers[HttpHeaders.RetryAfter]))
            code in 500..599 -> AiError.ServerError(code)
            code in 400..499 -> AiError.InvalidRequest(detail = "http_$code")
            else -> AiError.Unknown(detail = "http_$code")
        }
    }

    private fun mapStopReason(raw: String): AiStopReason = when (raw.lowercase()) {
        "stop" -> AiStopReason.COMPLETE
        "length" -> AiStopReason.MAX_TOKENS
        "content_filter" -> AiStopReason.REFUSAL
        else -> AiStopReason.OTHER
    }

    /** Parses a `Retry-After` header: delta-seconds first, then an HTTP-date; `null` if unusable. */
    private fun parseRetryAfterMs(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val trimmed = header.trim()
        trimmed.toLongOrNull()?.let { return (it * 1000).coerceAtLeast(0) }
        return runCatching {
            val instant = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
                .parse(trimmed, java.time.Instant::from)
            java.time.Duration.between(java.time.Instant.now(), instant).toMillis().coerceAtLeast(0)
        }.getOrNull()
    }

    /**
     * Joins [baseUrl] and the `chat/completions` path with exactly one `/` between them, preserving a
     * user-entered `.../v1` segment. (Strips one trailing slash, then appends — never replaces the path.)
     */
    private fun chatCompletionsUrl(baseUrl: String): String =
        baseUrl.removeSuffix("/") + "/chat/completions"

    companion object {
        // Realistic *network* deadlines, NOT the `< 2000ms` first-token perf figure (that is a UI hint
        // about model choice, Fork P5-2 "guidance, not a pin"). 20 s absorbs OpenRouter routing/queueing,
        // free-tier latency, and local Ollama / LM Studio cold starts. The injected client's
        // socketTimeoutMillis must stay >= DEFAULT_IDLE_TIMEOUT_MS (see AiCloudProvidesModule).
        const val DEFAULT_FIRST_TOKEN_TIMEOUT_MS = 20_000L
        const val DEFAULT_IDLE_TIMEOUT_MS = 20_000L

        /**
         * A light/fast model suggestion for the Block-N provider-settings UI hint only. The engine
         * never falls back to it — the model is always the user's free-text config value. Model
         * strings move; verify live at build time. (e.g. via OpenRouter.)
         */
        const val DEFAULT_LIGHT_MODEL = "openai/gpt-4o-mini"

        private val json = Json {
            encodeDefaults = true // keep `stream: true`
            explicitNulls = false // drop `stop` when absent
            ignoreUnknownKeys = true
        }
        private const val DONE_MARKER = "[DONE]"
    }
}

// ── Wire DTOs — private to this adapter; never domain types. ─────────────────────────────────────

private fun AiRequest.toChatRequest(model: String): ChatCompletionRequest {
    val messages = buildList {
        system?.takeIf { it.isNotBlank() }?.let { add(ChatMessageDto(role = "system", content = it)) }
        this@toChatRequest.messages.forEach {
            add(ChatMessageDto(role = it.role.toWireRole(), content = it.content))
        }
    }
    return ChatCompletionRequest(
        model = model,
        messages = messages,
        maxTokens = maxOutputTokens,
        stop = stopSequences.takeIf { it.isNotEmpty() },
    )
}

private fun com.sidr.launcher.domain.ai.AiRole.toWireRole(): String = when (this) {
    com.sidr.launcher.domain.ai.AiRole.USER -> "user"
    com.sidr.launcher.domain.ai.AiRole.ASSISTANT -> "assistant"
}

/**
 * Minimal request body — **no sampling params** (`temperature`/`top_p`/`top_k`) by design, for the
 * widest backend compatibility (some models 400 on them).
 */
@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean = true,
    val stop: List<String>? = null,
)

@Serializable
private data class ChatMessageDto(val role: String, val content: String)

@Serializable
private data class StreamChunk(
    val choices: List<StreamChoice> = emptyList(),
    val usage: UsageDto? = null,
)

@Serializable
private data class StreamChoice(
    val delta: DeltaDto = DeltaDto(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
private data class DeltaDto(
    val content: String? = null,
    val refusal: String? = null,
)

@Serializable
private data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
)
