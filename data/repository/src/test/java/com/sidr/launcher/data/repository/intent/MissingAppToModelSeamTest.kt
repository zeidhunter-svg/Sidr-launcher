package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeBridgeTransport
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.AppTargetResolver
import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.data.repository.agent.CatalogArgumentSorts
import com.sidr.launcher.data.repository.agent.DeviceMentionBinder
import com.sidr.launcher.data.repository.agent.DynamicToolName
import com.sidr.launcher.data.repository.agent.DynamicToolNames
import com.sidr.launcher.data.repository.agent.PermissionPresence
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.ToolMatchPlanner
import com.sidr.launcher.data.repository.agent.ToolPermissionCatalog
import com.sidr.launcher.data.repository.agent.ToolSelector
import com.sidr.launcher.data.repository.agent.ToolVocabulary
import com.sidr.launcher.data.repository.agent.sortResolversOf
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CompositePlanner
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeModelPlanner
import com.sidr.launcher.domain.ai.bridge.BridgeRendering
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawArg
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The seam task 2 of the A4′ acceptance tail creates, crossed with every layer real that a JVM test can have**
 * (plan review C1: without it the task is vacuous). Owner decision 14 + amendment D1: online, FastPath's «нет
 * такого приложения» is asked of the model, the model may CORRECT the name — «открой клод», «открой cloude» →
 * «Claude» — and the device binds the correction only to an installed label that is exactly it. Each half has its
 * own fake-driven tests (`HandleUserCommandUseCaseTest`, `RouteCommandUseCaseTest`, `BridgeValidatorTest`,
 * `DeviceMentionBinderTest`); none of them alone shows that a model's «Claude» for the user's «клод» survives the
 * validator — which until the amendment refused it as `UNDERIVABLE_MENTION` — and binds on the device.
 *
 * Real here: the matcher ([RuleBasedIntentMatcher]), the resolver, FastPath, the router, step 2b's production
 * planner (`TemplatePlanner` + [ToolMatchPlanner]), the bridge ([BridgeModelPlanner] with its `BridgeValidator`),
 * the production descriptions ([AuthoredToolDescriptions] — the app mention's `ArgSorts.APP` comes from there), the
 * production registry shape (the two authored sources, through a [ToolFederation]) and the binder
 * ([DeviceMentionBinder] over the real [AppTargetResolver]). Faked: the world — the installed apps, the transport
 * (scripted with the model's one answer), the store, the provider and the network, in the state that makes the
 * model reachable. The folding is [PlainTextFolding] (`IcuTextFolding` is Android's; nothing here needs ICU's
 * segmentation). Every app and package is invented (R14-43); every expected value is compared by equality.
 */
class MissingAppToModelSeamTest {

    private val claude = InstalledApp(packageName = "net.fixture.qandril", label = "Claude")
    private val apps = FakeInstalledAppsRepository().apply {
        appsToReturn = listOf(claude, InstalledApp(packageName = "net.fixture.alpha", label = "Lumora"))
    }
    private val executor = FakeActionExecutor()
    private val agentStore = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    /** Nothing in this file may reach a worker: the route plans, binds and starts — the engine never runs here. */
    private val refusing = object : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = error("no tool may run in this seam: ${invocation.id}")
    }

    /** `AgentProvidesModule`'s two authored sources, the levels the bridge offers on Android. */
    private val federation = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), refusing),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(ToolPermissionCatalog(), PermissionPresence { true }), refusing),
        ),
    )
    private val appTargets = AppTargetResolver(apps, FakeAliasStore(), PlainTextFolding)
    private val binder = DeviceMentionBinder(
        appTargets,
        CatalogArgumentSorts(),
        sortResolversOf(appTargets),
        FakeResolutionPreferenceStore(),
        DefaultResolutionPreferencePolicy(),
        PlainTextFolding,
        federation.registry,
    )
    private val planner = CompositePlanner(
        listOf(
            TemplatePlanner(),
            ToolMatchPlanner(
                ToolSelector(ToolVocabulary(), object : DynamicToolNames { override fun names(): List<DynamicToolName> = emptyList() }),
                appTargets,
                CatalogArgumentSorts(),
                sortResolversOf(appTargets),
            ),
        ),
    )

    /** The model's one answer: `launch_app` with the app it believes is meant, in its own word. */
    private fun modelLaunches(app: String) = FakeBridgeTransport(
        listOf(
            TransportReply.Answer(
                RawAnswer(
                    kind = BridgeRendering.KIND_PLAN,
                    steps = listOf(RawStep(ToolIds.LAUNCH_APP.value, mapOf(AuthoredToolDescriptions.MENTION_ARG to RawArg.Text(app)))),
                    question = null,
                    destination = null,
                ),
                ReplyMeta.NONE,
            ),
        ),
    )

    private fun router(transport: FakeBridgeTransport) = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = RuleBasedIntentMatcher(),
            resolver = IntentActionResolver(apps),
            executor = executor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(SupervisorJob()),
        ),
        modelPlanner = BridgeModelPlanner(
            federation.registry,
            AuthoredToolDescriptions(),
            transport,
            BridgeFilter.ANDROID_OFFERED_LEVELS,
            folding = PlainTextFolding,
        ),
        binder = binder,
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = false)),
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
        startAgentSession = StartAgentSessionUseCase(planner, agentStore, ids, federation.registry),
    )

    /**
     * The card «открой …» comes back as when the model's «Claude» bound: one launch of the one app labelled exactly
     * so — the package the device's, the words the MODEL's and marked so. `EXTERNAL`: production's `launch_app`.
     */
    private fun launchesClaude(command: String) = CommandOutcome.Interpreted(
        Interpretation.Act(
            goal = AgentGoal(command, GoalShape.Free(command)),
            steps = listOf(
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(
                        ToolIds.LAUNCH_APP,
                        mapOf("query" to ArgSource.Literal("Claude"), "app" to ArgSource.Literal(claude.packageName)),
                    ),
                    risk = ActionRiskLevel.SAFE,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.MODEL_PROPOSED,
                ),
            ),
            origins = mapOf(0 to mapOf("query" to ValueOrigin.ModelGenerated, "app" to ValueOrigin.Ours)),
            effects = mapOf(0 to ToolEffect.EXTERNAL),
            choices = emptyList(),
        ),
    )

    /**
     * Decision 14's first example. FastPath reads «открой клод» as a launch of «клод», which nothing installed is
     * named — and there is no transliterating fold, so the device alone could never bind it. Online it is asked of
     * the model once, with the command as typed; the model's «Claude» is admitted as the model's word and binds to
     * the app labelled exactly «Claude». The store plan does not start; nothing is executed. Task 4 of the tail plan
     * turns a reading with no card owed into a started session; until then it is the card (`Interpreted`).
     */
    @Test
    fun `open klod binds Claude through the real bridge`() = runTest {
        val transport = modelLaunches("Claude")

        val outcome = router(transport).route("открой клод")

        assertEquals(launchesClaude("открой клод"), outcome)
        assertEquals("one ask, no retry: the answer passed the validator", listOf("открой клод"), transport.requests.map { it.command })
        assertTrue("the store plan did not start", agentStore.saved.isEmpty())
        assertTrue("nothing is executed", executor.executedActions.isEmpty())
    }

    /** Decision 14's second example: a misspelling in the same script, which the edge rule does not reach either. */
    @Test
    fun `open cloude binds Claude through the real bridge`() = runTest {
        val transport = modelLaunches("Claude")

        val outcome = router(transport).route("открой cloude")

        assertEquals(launchesClaude("открой cloude"), outcome)
        assertEquals(listOf("открой cloude"), transport.requests.map { it.command })
        assertTrue(agentStore.saved.isEmpty())
    }

    /**
     * Controller ruling K3 at the seam: the model names «Claudex», which is not on this phone. The device binds
     * nothing (an exact label only), and the user gets A0's store plan in THEIR words — the goal and every saved
     * invocation compared whole: «клод», never «Claudex». The model's word does not reach the store.
     */
    @Test
    fun `the model's own word never reaches the store`() = runTest {
        val transport = modelLaunches("Claudex")

        val outcome = router(transport).route("открой клод")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
        assertEquals(AgentGoal("открой клод", GoalShape.AppNotInstalled("клод")), agentStore.saved.single().goal)
        assertEquals(
            listOf(
                ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("клод"))),
                ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            ),
            agentStore.saved.single().plan.steps.map { it.invocation },
        )
        assertEquals("one ask: binding found nothing, and nothing of it went back", 1, transport.requests.size)
        assertTrue("nothing is executed", executor.executedActions.isEmpty())
    }
}
