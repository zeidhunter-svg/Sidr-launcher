package com.sidr.launcher.agent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeAiProviderConfigRepository
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.core.testing.FakeSecureSecretStore
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.data.aicloud.BridgeTransportMode
import com.sidr.launcher.data.aicloud.OpenAiCompatibleBridgeTransport
import com.sidr.launcher.data.repository.agent.AppSortResolver
import com.sidr.launcher.data.repository.agent.AppTargetResolver
import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.data.repository.agent.CatalogArgumentSorts
import com.sidr.launcher.data.repository.agent.DeviceMentionBinder
import com.sidr.launcher.data.repository.agent.shortcut.AppShortcut
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolIds
import com.sidr.launcher.di.BridgeProvidesModule
import com.sidr.launcher.di.SortProvidesModule
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.AiModelId
import com.sidr.launcher.domain.ai.AiProviderConfig
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.ai.OutboundContextPolicy
import com.sidr.launcher.domain.ai.OutboundContextPolicy.AllowedContext
import com.sidr.launcher.domain.ai.bridge.BridgeError
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeModelPlanner
import com.sidr.launcher.domain.ai.bridge.BridgeOffering
import com.sidr.launcher.domain.ai.bridge.BridgeRendering
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.PreferenceEvidence
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.security.SecretKeys
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ArgSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bridge's egress, read off the PAYLOAD (spec §7.12.3; criterion 21; `DOC-AML-1`) — §7.1's sixth
 * barrier: the router's guard scanned our inventories, not what left the phone. This captures every body
 * the real transport sends through the real route and reads it.
 *
 * The graph is built by `BridgeProvidesModule`'s own providers over the production federation (a
 * NON-empty shortcut source carrying sentinels), our real descriptions, the real transport over a
 * MockEngine, and the real binder over apps, aliases and learned choices carrying sentinels. FastPath is
 * forced undecided, so every command reaches step 7. The mock REJECTS every first answer (a registered
 * tool outside the offering, carrying an echo sentinel), so every command also produces a retry body,
 * and accepts the second.
 *
 * Seven private sources (spec §7.12.3 (1)), in two groups — said, not blurred (plan §0.5 S5):
 *  - four the bridge graph DOES read — the dynamic tier (registry), installed apps, the alias table,
 *    learned choices (binder): planted, asserted absent from every body, each with a control proving it
 *    was read on the device;
 *  - three it holds NO port to — prayer location, calendar, usage history: asserted absent, and held
 *    STRUCTURALLY by `the bridge's classes take no port to prayer, calendar or usage data`. Their
 *    sentinels are planted nowhere — there is nowhere to plant them — so the scan of the bodies is not
 *    what holds these three; the signature scan is.
 *
 * **What a red here proves about WHICH source leaked** (Task 11 fix round 1, review m4). The alias and
 * learned-choice targets (`net.fixture.zantor` / «Zantor», `net.fixture.tessar.one` / `.two`) are also
 * installed apps — they have to be, or the binder could not bind them. So a red naming
 * `alias table: net.fixture.zantor` or `learned choices: net.fixture.tessar.two` (mutation M11d's
 * message) proves that the BOUND value leaked, not which store it came from. What singles out a source
 * is the sentinel only that source holds: «плимдорф» / `net.fixture.plimdorf` for the alias table,
 * «фальмира» / `net.fixture.falmira` for learned choices, «Vemblic» / `net.fixture.vemblic` for the app
 * list — each never typed and never bound, so it can only reach a body by that store being read whole.
 *
 * **Why «Pellix» stays although Task 10 renamed it in its own tests (R14-43).** There the label and the
 * package were both EXPECTED values on a rendered card, and a substring check could pass on the wrong
 * one. Here no expected value is compared by substring: the packages are compared by equality, and the
 * scan asks whether a body contains the whole SENTINEL `net.fixture.pellix.one` — the typed word
 * «pellix» is shorter than every sentinel, so it can neither fake a leak nor hide one. The label is not
 * a sentinel at all, for the reason «Lumora» and «Tessar» are not: it is what the user typed.
 */
class BridgePayloadGuardTest {

    private val provider = AiProviderId("payload-guard")
    private val keySentinel = "sk-SNTL-KEY"
    private val echoSentinel = "SNTL-REJECTED-ECHO"

    private val sources: Map<String, List<String>> = mapOf(
        "dynamic tier" to listOf("net.fixture.qorvath", "Qorvath", "Brimwell"),
        "installed apps" to listOf("net.fixture.lumora", "net.fixture.vemblic", "Vemblic", "net.fixture.pellix.one", "net.fixture.pellix.two"),
        "alias table" to listOf("net.fixture.zantor", "Zantor", "плимдорф", "net.fixture.plimdorf"),
        "learned choices" to listOf("net.fixture.tessar.one", "net.fixture.tessar.two", "фальмира", "net.fixture.falmira"),
        "prayer location (no port)" to listOf("Ostrevik"),
        "calendar (no port)" to listOf("Halvorn standup"),
        "usage history (no port)" to listOf("net.fixture.usagelog"),
        "the rejected answer" to listOf(echoSentinel),
        "the key" to listOf(keySentinel),
    )

    /** Each names ONE app the way a user types it; the binder resolves it on the device. */
    private val commands = listOf("давай lumora", "давай кварцелот", "давай tessar", "давай pellix")

    private val federation = ProductionGraph.federation(
        ProductionGraph.shortcutSource(
            AppShortcut(packageName = "net.fixture.qorvath", shortcutId = "brimwell_now", appLabel = "Qorvath", shortcutLabel = "Brimwell"),
        ),
    )

    private fun apps() = FakeInstalledAppsRepository().apply {
        appsToReturn = listOf(
            InstalledApp(packageName = "net.fixture.lumora", label = "Lumora"),
            InstalledApp(packageName = "net.fixture.vemblic", label = "Vemblic"),
            InstalledApp(packageName = "net.fixture.zantor", label = "Zantor"),
            InstalledApp(packageName = "net.fixture.tessar.one", label = "Tessar"),
            InstalledApp(packageName = "net.fixture.tessar.two", label = "Tessar"),
            InstalledApp(packageName = "net.fixture.pellix.one", label = "Pellix"),
            InstalledApp(packageName = "net.fixture.pellix.two", label = "Pellix"),
        )
    }

    private fun aliases() = FakeAliasStore().apply {
        runBlocking {
            upsert(Alias("кварцелот", AliasTarget.App("net.fixture.zantor"), createdAtEpochMs = 0L))
            upsert(Alias("плимдорф", AliasTarget.App("net.fixture.plimdorf"), createdAtEpochMs = 0L))
        }
    }

    private fun learned() = FakeResolutionPreferenceStore().apply {
        val tessar = CandidateSet(listOf(ResolvedTarget.App("net.fixture.tessar.one"), ResolvedTarget.App("net.fixture.tessar.two")))
        runBlocking {
            upsert(ResolutionPreference(CapabilityKey(ActionIds.LAUNCH_APP, "tessar"), ResolutionContext.None,
                ResolvedTarget.App("net.fixture.tessar.two"), PreferenceEvidence(3, 3, 1L), fingerprintOf(tessar)))
            upsert(ResolutionPreference(CapabilityKey(ActionIds.LAUNCH_APP, "фальмира"), ResolutionContext.None,
                ResolvedTarget.App("net.fixture.falmira"), PreferenceEvidence(3, 3, 1L),
                fingerprintOf(CandidateSet(listOf(ResolvedTarget.App("net.fixture.falmira"))))))
        }
    }

    private class Capture(private val mode: BridgeTransportMode) {
        val bodies = mutableListOf<String>()

        val engine = MockEngine { sent ->
            val body = (sent.body as TextContent).text
            bodies += body
            val messages = Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray
            val command = messages[1].jsonObject.getValue("content").jsonPrimitive.content
            val answer = if (messages.size == 2) REJECTED else accepted(command.substringAfterLast(' '))
            respond(envelope(answer), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }

        private fun quote(text: String) = Json.encodeToString(String.serializer(), text)

        private fun envelope(answer: String): String = when (mode) {
            BridgeTransportMode.FORCED_TOOL ->
                """{"choices":[{"message":{"tool_calls":[{"type":"function","function":{"name":"submit_answer","arguments":${quote(answer)}}}]}}]}"""
            BridgeTransportMode.PROMPT_ONLY, BridgeTransportMode.RESPONSE_FORMAT ->
                """{"choices":[{"message":{"content":${quote(answer)}}}]}"""
        }

        private fun accepted(mention: String) =
            """{"kind":"plan","steps":[{"tool":"launch_app","args":{"app":"$mention","duration":null,"query":null,"time":null,"url":null}}],"question":null,"destination":null}"""

        companion object {
            const val REJECTED =
                """{"kind":"plan","steps":[{"tool":"set_app_alias","args":{"app":"lumora","phrase":"SNTL-REJECTED-ECHO"}}],"question":null,"destination":null}"""
        }
    }

    private fun route(mode: BridgeTransportMode, capture: Capture) = BridgeProvidesModule.provideRouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = FakeIntentMatcher(),
            resolver = IntentActionResolver(apps()),
            executor = FakeActionExecutor(),
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(Dispatchers.Unconfined),
        ),
        modelPlanner = BridgeProvidesModule.provideModelPlanner(
            federation.registry,
            BridgeProvidesModule.provideToolDescriptions(AuthoredToolDescriptions()),
            OpenAiCompatibleBridgeTransport(
                HttpClient(capture.engine),
                FakeSecureSecretStore().apply { runBlocking { put(SecretKeys.apiKey(provider), keySentinel) } },
                FakeAiProviderConfigRepository(AiProviderConfig(provider, "https://api.example.com/v1", AiModelId("fixture-model"))),
                Dispatchers.Unconfined,
                mode,
            ),
            // Not `provideTextFolding()`: off Robolectric `android.icu` is the android.jar stub. The folding
            // reads the model's reply only and adds nothing to a request, so the payload under guard is the same.
            PlainTextFolding,
        ),
        binder = BridgeProvidesModule.provideMentionBinder(
            DeviceMentionBinder(
                AppTargetResolver(apps(), aliases(), PlainTextFolding),
                CatalogArgumentSorts(),
                // The production registry, by its own `@Provides` (phase 3b, Task 6) — the precedent above.
                SortProvidesModule.provideSortResolvers(AppSortResolver(AppTargetResolver(apps(), aliases(), PlainTextFolding))),
                learned(),
                DefaultResolutionPreferencePolicy(),
                PlainTextFolding,
            ),
        ),
        featureFlagRepository = FakeFeatureFlagRepository(),
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
        startAgentSession = StartAgentSessionUseCase(
            TemplatePlanner(),
            FakeAgentSessionStore(),
            object : AgentSessionIdFactory {
                private var n = 0
                override fun newId() = AgentSessionId("guard-${++n}")
            },
            federation.registry,
        ),
    )

    /** [routed] has one entry per body: the command whose `route()` call was running when that body was sent. */
    private data class Run(
        val mode: BridgeTransportMode,
        val bodies: List<String>,
        val routed: List<String>,
        val outcomes: List<CommandOutcome>,
    )

    private val runs: List<Run> by lazy {
        BridgeTransportMode.entries.map { mode ->
            val capture = Capture(mode)
            val route = route(mode, capture)
            val routed = mutableListOf<String>()
            val outcomes = commands.map { command ->
                val outcome = runBlocking { route.route(command) }
                repeat(capture.bodies.size - routed.size) { routed += command }
                outcome
            }
            Run(mode, capture.bodies.toList(), routed.toList(), outcomes)
        }
    }

    private fun messagesOf(body: String) = Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray

    /**
     * Controller ruling R1 (Task 11): the FIXED text of a system message — what is left once every
     * per-tool literal (each offered tool's id, our sentence for it, its argument names and their
     * descriptions) is blanked. Blanked IN PLACE, at the very position the renderer wrote each literal,
     * to `\u0000` — never by a search-and-replace over the whole text. A global replace of a short
     * argument name (`app`, `url`, `time`) would also cut it out of a header («the app store») or the
     * instruction, and could cut a denylisted term in half with it (removing `time` from «timemail»
     * leaves «mail» and hides «email»): a scan made weaker by its own preparation. Blanking to a
     * non-letter also keeps two neighbours from joining into a term that was never sent. Every position
     * is checked before it is blanked, so a renderer that moves its layout fails here loudly instead of
     * blanking the wrong span.
     */
    private fun fixedTextOf(system: String, offering: BridgeOffering): String {
        val out = StringBuilder(system)
        fun expect(at: Int, fixed: String): Int {
            assertTrue("the offering's layout moved: «$fixed» expected at $at", system.startsWith(fixed, at))
            return at + fixed.length
        }
        fun blank(at: Int, literal: String): Int {
            val end = expect(at, literal)
            for (i in at until end) out.setCharAt(i, '\u0000')
            return end
        }
        // The one sentence naming every argument (`BridgeRendering`: distinct names, sorted).
        val names = offering.tools.flatMap { it.description.args }.map { it.name }.distinct().sorted()
        val sentence = system.indexOf(NAMES_OPENING)
        assertTrue("no argument-name sentence in the system message", sentence >= 0)
        var at = sentence + NAMES_OPENING.length
        names.forEachIndexed { i, name ->
            if (i > 0) at = expect(at, ", ")
            at = blank(at, name)
        }
        expect(at, NAMES_CLOSING)
        // One line per tool: "- <id>: <text>[ args: <name> (<markers>) — <description>, …]". The markers,
        // the joiners and the headers between the lines are fixed text and stay standing.
        offering.tools.forEach { tool ->
            val id = tool.descriptor.id.value
            val line = system.indexOf("\n- $id: ")
            assertTrue("no line for $id in the system message", line >= 0)
            at = blank(line + "\n- ".length, id)
            at = expect(at, ": ")
            at = blank(at, tool.description.text)
            if (tool.description.args.isNotEmpty()) {
                at = expect(at, " args: ")
                tool.description.args.forEachIndexed { i, arg ->
                    if (i > 0) at = expect(at, ", ")
                    at = blank(at, arg.name)
                    at = expect(at, " (")
                    val markersEnd = system.indexOf(") — ", at)
                    assertTrue("no marker list after ${arg.name} on the line for $id", markersEnd >= 0)
                    at = blank(markersEnd + ") — ".length, arg.description)
                }
            }
            assertTrue("the line for $id runs on past its last literal", at == system.length || system[at] == '\n')
        }
        return out.toString()
    }

    @Test
    fun `allow-list holds exactly the vetted categories, the authored tool schema among them`() {
        assertEquals(
            setOf(
                AllowedContext.USER_COMMAND,
                AllowedContext.STATIC_SYSTEM_PROMPT,
                AllowedContext.GENERATION_LIMITS,
                AllowedContext.AUTHORED_TOOL_SCHEMA,
            ),
            OutboundContextPolicy.ALLOWED,
        )
    }

    /** Criterion 21: every source, every form, every retry. Violations are collected, so a red names them all. */
    @Test
    fun `no private source reaches any body, in any transport form, or in the retry`() {
        val violations = runs.flatMap { run ->
            run.bodies.flatMap { body ->
                sources.flatMap { (source, sentinels) ->
                    sentinels.filter { body.contains(it) }.map { "${run.mode} / $source: $it" }
                }
            }
        }.distinct()
        assertEquals(emptyList<String>(), violations)

        // The retry carries the closed code's note and nothing else (spec §7.12.2).
        runs.forEach { run ->
            run.bodies.chunked(2).forEach { (first, retry) ->
                assertEquals(2, messagesOf(first).size)
                val retryMessages = messagesOf(retry)
                assertEquals(3, retryMessages.size)
                assertEquals(
                    BridgeRendering.retryNote(BridgeError.NOT_OFFERED),
                    retryMessages[2].jsonObject.getValue("content").jsonPrimitive.content,
                )
            }
        }
    }

    /** §7.12.3 (2): the catalog part of the payload IS the rendered offering of the nineteen — not one line more. */
    @Test
    fun `the catalog part of every body is exactly the rendered offering of the nineteen authored tools`() {
        val offering = BridgeFilter.offeringOf(federation.registry, AuthoredToolDescriptions())
        val system = BridgeRendering.systemText(offering)
        val schema = Json.parseToJsonElement(BridgeRendering.schemaJson(offering))

        assertEquals(BridgeOfferingGuardTest.OFFERED_NINETEEN, offering.tools.map { it.descriptor.id.value }.toSet())
        runs.forEach { run ->
            run.bodies.forEach { body ->
                val json = Json.parseToJsonElement(body).jsonObject
                assertEquals("${run.mode}", system, messagesOf(body)[0].jsonObject.getValue("content").jsonPrimitive.content)
                when (run.mode) {
                    BridgeTransportMode.PROMPT_ONLY -> assertEquals(setOf("model", "messages", "max_tokens", "stream"), json.keys)
                    BridgeTransportMode.RESPONSE_FORMAT -> assertEquals(
                        schema,
                        json.getValue("response_format").jsonObject.getValue("json_schema").jsonObject.getValue("schema"),
                    )
                    BridgeTransportMode.FORCED_TOOL -> assertEquals(
                        schema,
                        json.getValue("tools").jsonArray.single().jsonObject.getValue("function").jsonObject.getValue("parameters"),
                    )
                }
            }
        }

        // Controller ruling R1 (spec §7.12.3 (3)): the offering's FIXED text — the argument-name sentence,
        // the three namespace headers, the markers, the joiners — leaves the device in every body, and no
        // denylist scan read it (`BridgeRenderingTest` scans the instruction and the retry notes only).
        // The per-tool literals are NOT scanned and the denylist is NOT narrowed: `open_location_settings`
        // and `open_data_usage_settings` legitimately carry «location» and «usage». See [fixedTextOf].
        val denylisted = OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS + OutboundContextPolicy.CREDENTIAL_TERMS
        runs.forEach { run ->
            run.bodies.forEach { body ->
                val fixed = fixedTextOf(messagesOf(body)[0].jsonObject.getValue("content").jsonPrimitive.content, offering)
                assertEquals(
                    "${run.mode}: a denylisted term in the offering's fixed text",
                    emptyList<String>(),
                    denylisted.filter { fixed.contains(it, ignoreCase = true) },
                )
                // Non-vacuity: the blanking left the fixed text standing — the sentence, and the opening of
                // every namespace header the offering has (the header's own words are what is scanned).
                assertTrue("${run.mode}: the argument-name sentence survived", fixed.contains(NAMES_OPENING) && fixed.contains(NAMES_CLOSING))
                offering.tools.map { it.description.namespace }.distinct().forEach { namespace ->
                    val opening = "\n\n" + namespace.name.lowercase().replaceFirstChar { it.uppercase() } + " ("
                    assertTrue("${run.mode}: the $namespace header survived the blanking", fixed.contains(opening))
                }
            }
        }
    }

    /** Non-vacuity: every planted value WAS read on the device, and the body WAS captured. */
    @Test
    fun `the controls - the device read every planted source, and the command and offering are in the body`() {
        assertTrue(federation.registry.all().any { it.id.value.startsWith(ShortcutToolIds.PREFIX) && "net.fixture.qorvath" in it.id.value })
        runs.forEach { run ->
            val bound = run.outcomes.map { (it as CommandOutcome.Interpreted).interpretation as Interpretation.Act }
            fun pinned(index: Int) = (bound[index].step.invocation.args["app"] as? ArgSource.Literal)?.value
            assertEquals("${run.mode}: installed apps", "net.fixture.lumora", pinned(0))
            assertEquals("${run.mode}: alias table", "net.fixture.zantor", pinned(1))
            assertEquals("${run.mode}: learned choices", "net.fixture.tessar.two", pinned(2))
            assertEquals("${run.mode}: a choice", listOf("net.fixture.pellix.one", "net.fixture.pellix.two"), bound[3].choice!!.candidates.map { it.packageName })
            assertEquals(commands.size * 2, run.bodies.size)
            // Task 11 ruling on hole H4: the user slot of every body — first ask and retry alike — is
            // exactly the command that was being routed, as the user typed it (`USER_COMMAND`), and nothing
            // rides along in it. Each body is paired with the `route()` call that sent it, so an extra ask
            // inside one command is caught here too, not only counted above.
            run.bodies.zip(run.routed).forEach { (body, command) ->
                assertEquals(
                    "${run.mode}",
                    JsonObject(mapOf("role" to JsonPrimitive("user"), "content" to JsonPrimitive(command))),
                    messagesOf(body)[1].jsonObject,
                )
                assertTrue(body.contains("launch_app"))
            }
        }
    }

    /** Spec §7.12.3 (1), the three sources with no port (plan §0.5 S5): held by the bridge's signatures. */
    @Test
    fun `the bridge's classes take no port to prayer, calendar or usage data`() {
        val forbiddenPackages = listOf(
            "com.sidr.launcher.domain.prayer",
            "com.sidr.launcher.domain.history",
            "com.sidr.launcher.domain.suggestions",
            // The implementations, not only the ports (plan review): a provider taken directly would
            // pass a scan of domain packages alone.
            "com.sidr.launcher.data.repository.suggestions",
            "com.sidr.launcher.data.prayer",
            // Android's own doors to calendar, location and usage — a Context opens all of them.
            "android.content",
            "android.app.usage",
        )
        val bridgeClasses = listOf(
            BridgeModelPlanner::class.java,
            OpenAiCompatibleBridgeTransport::class.java,
            DeviceMentionBinder::class.java,
            AppTargetResolver::class.java,
            AuthoredToolDescriptions::class.java,
        )
        val types = bridgeClasses.flatMap { type -> type.constructors.flatMap { it.parameterTypes.toList() } } +
            BridgeProvidesModule::class.java.declaredMethods.flatMap { it.parameterTypes.toList() }

        val reaching = types.filter { type -> forbiddenPackages.any { type.name.startsWith(it) } }.map { it.name }.distinct()
        assertEquals(emptyList<String>(), reaching)
        // The control: the scan read real signatures — it sees the ports the bridge DOES hold.
        assertTrue(types.any { it.simpleName == "ToolRegistry" })
        assertTrue(types.any { it.simpleName == "ResolutionPreferenceStore" })

        // Task 11 ruling on hole H5: the ERASED types above cannot see a port behind a type argument — a
        // `Provider<…>`, a `Lazy<…>` or a `() -> UsageHistoryRepository` erases to `Provider` / `Function0`.
        // The generic signatures name it. A package matches only as a whole qualified name (not preceded by
        // a letter, digit, `_` or `.`, not followed by an identifier character), so `com.x.android.content…`
        // or `…domain.prayerx` cannot pass for a forbidden package.
        val genericNames = bridgeClasses.flatMap { type -> type.constructors.flatMap { it.genericParameterTypes.toList() } }
            .map { it.typeName } +
            BridgeProvidesModule::class.java.declaredMethods.flatMap { it.genericParameterTypes.toList() }.map { it.typeName }
        val packagePatterns = forbiddenPackages.map { Regex("""(?<![\w.])""" + Regex.escape(it) + """(?!\w)""") }
        val reachingGeneric = genericNames.filter { name -> packagePatterns.any { it.containsMatchIn(name) } }.distinct()
        assertEquals(emptyList<String>(), reachingGeneric)
        // The same control for the generic read: it sees a port the bridge does hold, by its full name.
        assertTrue(genericNames.any { it == "com.sidr.launcher.domain.tool.ToolRegistry" })
    }

    private companion object {
        /** The fixed opening and closing of the argument-name sentence (`BridgeRendering.offeringText`). */
        const val NAMES_OPENING = "Every argument name: "
        const val NAMES_CLOSING = ". Give \"args\" every one of these names: "
    }
}
