package com.sidr.launcher.data.aicloud

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.ai.router.ActionProposal
import com.sidr.launcher.domain.ai.router.CatalogSchemaRenderer
import com.sidr.launcher.domain.ai.router.CommandPlanner
import com.sidr.launcher.domain.ai.router.PlanResult
import com.sidr.launcher.domain.ai.router.ProposalValidator
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.domain.security.SecureSecretStore
import io.ktor.client.HttpClient
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject

/**
 * [CommandPlanner] over any **OpenAI-compatible** `chat/completions` endpoint (AIL-4). Reuses the same
 * BYOK transport plumbing as [OpenAiCompatibleGenerativeAiEngine] — base URL + free-text model from
 * [AiProviderConfigRepository], key from [SecureSecretStore] — but this is a **separate, non-streaming
 * request path**: routing is one decision, so we send `stream:false`, hard-timeout the whole call, and
 * read one body. The assistant's streaming body is untouched.
 *
 * Fail-closed by construction — [plan] **never throws** and returns [PlanResult.NoPlan] on every
 * degradation: no active config, non-`https://` base URL, missing/unusable key, HTTP error, timeout
 * (AIL-Q1), network failure, or a reply that is not a strict, schema-valid JSON object (AIL-Q2 — a
 * non-tool-capable model that answers with prose parses to `NoPlan`, so the launcher is never worse
 * than rule-only). The offline short-circuit lives in `RouteCommandUseCase`; this impl still maps any
 * network exception to `NoPlan` defensively.
 *
 * Privacy: the outbound body is **exactly** the user command (one `user` message) + the static Action
 * Registry schema ([CatalogSchemaRenderer], one `system` message). No device/usage/calendar/location/
 * history/clipboard context. Nothing here logs the key, the body, or the reply.
 */
class LlmCommandPlanner(
    private val httpClient: HttpClient,
    private val secretStore: SecureSecretStore,
    private val configRepository: AiProviderConfigRepository,
    private val ioDispatcher: CoroutineDispatcher,
    /** Hard TOTAL deadline for the routing call (AIL-Q1). Exceeded → [PlanResult.NoPlan]. */
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    /** Small output budget — the reply is one short JSON object, not prose. */
    private val maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
) : CommandPlanner {

    override suspend fun plan(command: String, catalog: ActionCatalog): PlanResult =
        withContext(ioDispatcher) {
            val trimmed = command.trim()
            if (trimmed.isEmpty()) return@withContext PlanResult.NoPlan

            val config = configRepository.activeConfig().first() ?: return@withContext PlanResult.NoPlan
            val baseUrl = config.baseUrl.trim()
            if (!baseUrl.startsWith("https://", ignoreCase = true)) return@withContext PlanResult.NoPlan

            val key = when (val stored = secretStore.get(SecretKeys.apiKey(config.providerId))) {
                is OperationResult.Success -> stored.value?.trim()?.takeIf { it.isNotEmpty() }
                is OperationResult.Failure -> null
            } ?: return@withContext PlanResult.NoPlan

            val model = config.modelId.value.trim()
            val schema = CatalogSchemaRenderer.render(catalog)
            val url = baseUrl.removeSuffix("/") + "/chat/completions"
            val body = json.encodeToString(
                RouterChatRequest(
                    model = model,
                    messages = listOf(
                        RouterMessageDto(role = "system", content = schema),
                        RouterMessageDto(role = "user", content = trimmed),
                    ),
                    maxTokens = maxOutputTokens,
                ),
            )

            val proposal = try {
                withTimeoutOrNull(timeoutMs) {
                    val response = httpClient.post(url) {
                        header(HttpHeaders.Authorization, "Bearer $key")
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                        setBody(body)
                    }
                    if (!response.status.isSuccess()) return@withTimeoutOrNull null
                    parseResponse(response.bodyAsText())
                }
            } catch (e: CancellationException) {
                throw e // never swallow parent cancellation
            } catch (e: Exception) {
                null // network / IO / anything → NoPlan
            } ?: return@withContext PlanResult.NoPlan

            ProposalValidator.validate(proposal, catalog)
        }

    /** Chat-completion envelope → the JSON object in `choices[0].message.content` → [ActionProposal]. */
    private fun parseResponse(bodyText: String): ActionProposal? {
        val content = runCatching {
            json.decodeFromString<RouterResponse>(bodyText).choices.firstOrNull()?.message?.content
        }.getOrNull() ?: return null
        return parseProposal(content)
    }

    /**
     * Strict-parse the model's reply content into an [ActionProposal]. Tolerates prose/markdown around
     * the object (extracts the first `{`..last `}` slice) but the slice itself must be valid JSON with a
     * string `action`; anything else → `null` → `NoPlan` (fail-closed).
     */
    private fun parseProposal(content: String): ActionProposal? = runCatching {
        val slice = extractJsonObject(content) ?: return null
        val obj = json.parseToJsonElement(slice).jsonObject
        val action = (obj["action"] as? JsonPrimitive)?.contentOrNull ?: return null
        val args = (obj["args"] as? JsonObject)
            ?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }
            ?.toMap()
            ?: emptyMap()
        val confidence = (obj["confidence"] as? JsonPrimitive)?.floatOrNull
        val question = (obj["question"] as? JsonPrimitive)?.contentOrNull
        ActionProposal(action = action, args = args, confidence = confidence, question = question)
    }.getOrNull()

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start in 0 until end) text.substring(start, end + 1) else null
    }

    companion object {
        /** AIL-Q1: reuse the `< 2000ms` cloud budget as a HARD total deadline for the routing call. */
        const val DEFAULT_TIMEOUT_MS = 2_000L
        const val DEFAULT_MAX_OUTPUT_TOKENS = 256

        private val json = Json {
            encodeDefaults = true // keep stream:false
            explicitNulls = false
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

// ── Wire DTOs — private to this adapter; never domain types. ─────────────────────────────────────

@Serializable
private data class RouterChatRequest(
    val model: String,
    val messages: List<RouterMessageDto>,
    @SerialName("max_tokens") val maxTokens: Int,
    val stream: Boolean = false,
)

@Serializable
private data class RouterMessageDto(val role: String, val content: String)

@Serializable
private data class RouterResponse(val choices: List<RouterChoice> = emptyList())

@Serializable
private data class RouterChoice(val message: RouterResponseMessage = RouterResponseMessage())

@Serializable
private data class RouterResponseMessage(val content: String? = null)
