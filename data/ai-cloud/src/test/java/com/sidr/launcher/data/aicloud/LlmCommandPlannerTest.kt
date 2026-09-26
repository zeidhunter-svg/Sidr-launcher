package com.sidr.launcher.data.aicloud

import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.router.PlanResult
import com.sidr.launcher.domain.security.SecretKeys
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LlmCommandPlannerTest {

    private val provider = AiProviderId("openai-compatible")
    private val config = AiProviderConfig(
        providerId = provider,
        baseUrl = "https://api.example.com/v1",
        modelId = AiModelId("test-model"),
    )
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val catalog: ActionCatalog = object : ActionCatalog {
        private val list = listOf(
            ActionDescriptor(
                id = ActionIds.LAUNCH_APP,
                title = "Open app",
                description = "Launch an installed app by name",
                category = ActionCategory.APP,
                risk = ActionRiskLevel.SAFE,
                argSchema = listOf(ActionArg("query", description = "The app name to launch")),
            ),
            ActionDescriptor(
                id = ActionIds.OPEN_URL,
                title = "Open link",
                description = "Open a web address in the browser",
                category = ActionCategory.WEB,
                risk = ActionRiskLevel.CONFIRM,
                argSchema = listOf(ActionArg("url", description = "The web address to open")),
            ),
        )
        override fun all(): List<ActionDescriptor> = list
        override fun descriptor(id: ActionId): ActionDescriptor? = list.firstOrNull { it.id == id }
    }

    private fun seededStore(): FakeSecureSecretStore =
        FakeSecureSecretStore().apply { runBlocking { put(SecretKeys.apiKey(provider), "sk-test") } }

    private fun planner(
        mockEngine: MockEngine,
        store: FakeSecureSecretStore = seededStore(),
        cfg: AiProviderConfig? = config,
        timeoutMs: Long = 2_000L,
    ) = LlmCommandPlanner(
        httpClient = HttpClient(mockEngine),
        secretStore = store,
        configRepository = FakeAiProviderConfigRepository(cfg),
        ioDispatcher = Dispatchers.IO,
        timeoutMs = timeoutMs,
    )

    /** Wrap a model reply [content] in a chat-completion envelope, escaping via kotlinx-serialization. */
    private fun chatResponse(content: String): String {
        val escaped = Json.encodeToString(String.serializer(), content)
        return """{"choices":[{"message":{"content":$escaped}}]}"""
    }

    @Test
    fun `valid tool JSON routes to a registered action`() = runTest {
        val mock = MockEngine {
            respond(
                chatResponse("""{"action":"launch_app","args":{"query":"telegram"},"confidence":0.92}"""),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val result = planner(mock).plan("open telegram please", catalog)
        assertEquals(PlanResult.RoutedAction(LauncherAction.LaunchApp("telegram"), 0.92f), result)
    }

    @Test
    fun `JSON wrapped in markdown fences still parses`() = runTest {
        val fenced = "Sure!\n```json\n{\"action\":\"open_url\",\"args\":{\"url\":\"https://x.test\"}}\n```"
        val mock = MockEngine { respond(chatResponse(fenced), HttpStatusCode.OK, jsonHeaders) }
        val result = planner(mock).plan("go to x.test", catalog)
        assertEquals(LauncherAction.OpenUrl("https://x.test"), (result as PlanResult.RoutedAction).action)
    }

    @Test
    fun `free-text reply is NoPlan (non-tool-capable model)`() = runTest {
        val mock = MockEngine {
            respond(chatResponse("I think you should open Telegram."), HttpStatusCode.OK, jsonHeaders)
        }
        assertEquals(PlanResult.NoPlan, planner(mock).plan("open telegram", catalog))
    }

    @Test
    fun `hallucinated action id is NoPlan`() = runTest {
        val mock = MockEngine {
            respond(chatResponse("""{"action":"wipe_device","args":{}}"""), HttpStatusCode.OK, jsonHeaders)
        }
        assertEquals(PlanResult.NoPlan, planner(mock).plan("do it", catalog))
    }

    @Test
    fun `no active config is NoPlan and never calls the network`() = runTest {
        var called = false
        val mock = MockEngine { called = true; respond("{}", HttpStatusCode.OK, jsonHeaders) }
        assertEquals(PlanResult.NoPlan, planner(mock, cfg = null).plan("open telegram", catalog))
        assertFalse("planner must not open a socket without config", called)
    }

    @Test
    fun `missing key is NoPlan and never calls the network`() = runTest {
        var called = false
        val mock = MockEngine { called = true; respond("{}", HttpStatusCode.OK, jsonHeaders) }
        val result = planner(mock, store = FakeSecureSecretStore()).plan("open telegram", catalog)
        assertEquals(PlanResult.NoPlan, result)
        assertFalse("planner must not open a socket without a key", called)
    }

    @Test
    fun `non-https base url is NoPlan and never calls the network`() = runTest {
        var called = false
        val mock = MockEngine { called = true; respond("{}", HttpStatusCode.OK, jsonHeaders) }
        val cfg = config.copy(baseUrl = "http://api.example.com/v1")
        assertEquals(PlanResult.NoPlan, planner(mock, cfg = cfg).plan("open telegram", catalog))
        assertFalse(called)
    }

    @Test
    fun `server error is NoPlan`() = runTest {
        val mock = MockEngine { respond("boom", HttpStatusCode.InternalServerError, jsonHeaders) }
        assertEquals(PlanResult.NoPlan, planner(mock).plan("open telegram", catalog))
    }

    @Test
    fun `rate limited is NoPlan`() = runTest {
        val mock = MockEngine { respond("slow down", HttpStatusCode.TooManyRequests, jsonHeaders) }
        assertEquals(PlanResult.NoPlan, planner(mock).plan("open telegram", catalog))
    }

    @Test
    fun `a hard timeout collapses to NoPlan`() = runBlocking {
        // Real timing (not virtual): the handler stalls past the injected deadline.
        val mock = MockEngine {
            delay(1_000)
            respond(
                chatResponse("""{"action":"launch_app","args":{"query":"telegram"}}"""),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val result = planner(mock, timeoutMs = 100).plan("open telegram", catalog)
        assertEquals(PlanResult.NoPlan, result)
    }

    @Test
    fun `outbound body is exactly the user command plus the static schema - no private context`() = runTest {
        var capturedBody: String? = null
        val mock = MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
                chatResponse("""{"action":"launch_app","args":{"query":"maps"}}"""),
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        // A sensitive value the planner is NEVER given — it must not appear on the wire.
        val sentinel = "GPS:37.7749,-122.4194"
        planner(mock).plan("navigate home", catalog)

        val body = requireNotNull(capturedBody)
        val root = Json.parseToJsonElement(body).jsonObject
        val messages = root.getValue("messages").jsonArray
        // Exactly two messages: a system schema, then the verbatim user command. Nothing else.
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("navigate home", messages[1].jsonObject.getValue("content").jsonPrimitive.content)
        assertTrue(
            "system message must be the rendered action schema",
            messages[0].jsonObject.getValue("content").jsonPrimitive.content.contains("launch_app"),
        )
        assertFalse("no private/device context may leave", body.contains(sentinel))
    }
}
