package com.sidr.launcher.agent

import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeBridgeTransport
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeLearnedPlanStore
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.data.repository.text.IcuTextFolding
import com.sidr.launcher.di.BridgeProvidesModule
import com.sidr.launcher.di.IntentProvidesModule
import com.sidr.launcher.di.MemoryProvidesModule
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.bridge.BridgeRendering
import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawArg
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.FormPart
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.LearnedPlans
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.Replay
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.CommandRouteStep
import com.sidr.launcher.domain.memory.resolution.DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.ResolvedCommand
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.text.TextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The device has one answer to "is this the same word?", and every path that asks gets it** (A4′ phase
 * 3b, B-1; controller ruling E4-P3). Since Task 7 that answer decides FastPath's launch (a word form is a
 * question, not «no such app») and the key of every learned launch choice, besides step 2b's target and the
 * bridge's mention — so a `PlainTextFolding` bound in production would silently change all four, and no
 * other test would notice: every per-path test passes its own folding.
 *
 * Read the `BridgePayloadGuardTest` way — by calling the modules' own `@Provides` functions, which is what
 * Hilt reaches:
 *  - the binding is ICU's ([BridgeProvidesModule.provideTextFolding]);
 *  - the two FastPath collaborators the modules build BY HAND — the resolver
 *    ([IntentProvidesModule.provideIntentActionResolver]) and the learned-choice decorator
 *    ([MemoryProvidesModule.provideResolveCommandWithPreferenceUseCase]) — fold by the folding they are
 *    GIVEN, not by their `PlainTextFolding` default. A distinguishing folding proves it: under it every
 *    text is one and the same word, so «zantor» finds «Lumora» and every key is that word.
 *
 * The fourth hand-built collaborator is step 2c's learned-plan cache ([BridgeProvidesModule.provideLearnedPlans],
 * A4′ phase 4, final review I2): it matches a command to a pinned form by the folding it is GIVEN.
 *
 * The fifth is the route itself ([BridgeProvidesModule.provideRouteCommandUseCase], A4′ tail fix round, task 6): step
 * 1a compares a bare command with an installed label by the folding it is GIVEN.
 *
 * Step 2b (`AppTargetResolver`) and the bridge's binder (`DeviceMentionBinder`) take the folding through
 * `@Inject`, so the one binding is the only thing Hilt can hand them. `provideModelPlanner` passes its own
 * parameter — run here since phase 3b's close (final review B-M1), over a fake transport.
 */
class TextFoldingBindingGuardTest {

    private val oneWord = object : TextFolding {
        override fun fold(text: String): String = SAME_WORD
        override fun words(text: String): List<String> = listOf(SAME_WORD)
    }

    @Test
    fun `the device folds by ICU, and FastPath's resolver and learned-choice key are built with that folding`() = runTest {
        assertSame(IcuTextFolding, BridgeProvidesModule.provideTextFolding())

        val apps = FakeInstalledAppsRepository().apply { appsToReturn = listOf(InstalledApp("net.fixture.lumora", "Lumora")) }
        val resolver = IntentProvidesModule.provideIntentActionResolver(apps, oneWord)
        assertEquals(
            OperationResult.Success(ExecutableAction.LaunchAppAction("net.fixture.lumora")),
            resolver.resolve(LauncherIntent.LaunchAppIntent("zantor")),
        )

        val tessars = listOf(InstalledApp("net.fixture.tessar.one", "Tessar"), InstalledApp("net.fixture.tessar.two", "Tessar"))
        val decorator = MemoryProvidesModule.provideResolveCommandWithPreferenceUseCase(
            route = CommandRouteStep { CommandOutcome.NeedsConfirmation(tessars, query = "tessar") },
            store = FakeResolutionPreferenceStore(),
            policy = DefaultResolutionPreferencePolicy(),
            catalog = FakeActionCatalog(),
            folding = oneWord,
        )
        val asked = decorator.resolve("открой tessar") as ResolvedCommand.Outcome
        assertEquals(CapabilityKey(ActionIds.LAUNCH_APP, SAME_WORD), asked.learningToken!!.capabilityKey)
    }

    /**
     * Phase 3b close, final review B-M1 — the third hand-built collaborator: the bridge's planner, as
     * [BridgeProvidesModule.provideModelPlanner] builds it, checks derivability by the folding it is GIVEN,
     * not by `BridgeModelPlanner`'s `PlainTextFolding` default. Under [oneWord] every text is one word, so a
     * mention the command never said («zantor» for «открой lumora») is derivable and the reading proposed on
     * the first ask; under the plain folding the same answer is underivable, asked again once, and refused —
     * which is what a dropped argument would do to every Thai or CJK mention in production.
     */
    @Test
    fun `the bridge's planner checks derivability by the folding it is given`() = runTest {
        val zantor = TransportReply.Answer(
            RawAnswer(
                kind = BridgeRendering.KIND_PLAN,
                steps = listOf(RawStep("launch_app", mapOf("app" to RawArg.Text("zantor")))),
                question = null,
                destination = null,
            ),
            ReplyMeta.NONE,
        )
        val transport = FakeBridgeTransport(listOf(zantor, zantor))
        val planner = BridgeProvidesModule.provideModelPlanner(FakeToolRegistry.withA0Tools(), AuthoredToolDescriptions(), transport, oneWord)

        val result = planner.plan("открой lumora")

        assertEquals("proposed under the folding it was given", ToolIds.LAUNCH_APP, (result as? ModelPlanning.Proposed)?.steps?.single()?.tool)
        assertEquals("asked once — the mention was derivable the first time", 1, transport.requests.size)
    }

    /**
     * A4′ phase 4, final review I2 — step 2c's cache, as [BridgeProvidesModule.provideLearnedPlans] builds it,
     * reads a command's words by the folding it is GIVEN. The form pinned here is `[SAME_WORD] …` — a word only
     * [oneWord] produces — so the command «открой lumora» fits it under [oneWord] (every token is that one word,
     * the second is the slot) and under no other folding: the plain one reads «открой» and «lumora», neither the
     * form's word, and the cache would miss every command in production.
     */
    @Test
    fun `the learned plans match by the folding they are given`() = runTest {
        val store = FakeLearnedPlanStore()
        val form = CommandForm(listOf(FormPart.Word(SAME_WORD), FormPart.Slot))
        val template = PlanTemplate(listOf(TemplateStep(ToolIds.LAUNCH_APP, mapOf("app" to TemplateArg.Slot(0)))))
        store.upsert(
            LearnedPlan(
                LearnedPlanShape(form, template),
                streak = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD,
                confirmations = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD,
                confirmedAtEpochMs = 0L,
            ),
        )
        val plans = BridgeProvidesModule.provideLearnedPlans(
            store, FakeToolRegistry.withA0Tools(), AuthoredToolDescriptions(), FakeMentionBinder(), oneWord,
        )

        val replay = plans.replay("открой lumora")

        assertTrue("replayed under the folding it was given: $replay", replay is Replay.Bound)
        assertEquals(form, (replay as Replay.Bound).form)
    }

    /**
     * A4′ tail fix round, task 6 — step 1a's exact installed label, as [BridgeProvidesModule.provideRouteCommandUseCase]
     * builds the route: it reads the command by the folding it is GIVEN, not by `RouteCommandUseCase`'s
     * `PlainTextFolding` default. Under [oneWord] «zantor» is the label «Lumora» (both are [SAME_WORD]), so the bare
     * command opens Lumora on the device and the model is never asked; under the plain folding they are two words, step
     * 1a answers `null` and the command goes to the model — which is what a dropped argument would do to every label
     * ICU segments differently from the plain folding.
     */
    @Test
    fun `the route reads a bare label by the folding it is given`() = runTest {
        val lumora = InstalledApp("net.fixture.lumora", "Lumora")
        val apps = FakeInstalledAppsRepository().apply { appsToReturn = listOf(lumora) }
        var asked = 0
        val store = FakeAgentSessionStore()
        val route = BridgeProvidesModule.provideRouteCommandUseCase(
            handleUserCommand = HandleUserCommandUseCase(
                matcher = FakeIntentMatcher(),
                resolver = IntentActionResolver(apps),
                executor = FakeActionExecutor(),
                confidencePolicy = DefaultIntentConfidencePolicy(),
                recordingScope = CoroutineScope(Dispatchers.Unconfined),
            ),
            modelPlanner = ModelPlanner { asked++; ModelPlanning.Declined },
            binder = FakeMentionBinder(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
            startAgentSession = StartAgentSessionUseCase(
                TemplatePlanner(),
                store,
                object : AgentSessionIdFactory {
                    override fun newId() = AgentSessionId("label-guard")
                },
                FakeToolRegistry.withA0Tools(),
            ),
            learnedPlans = LearnedPlans.NONE,
            installedApps = apps,
            folding = oneWord,
        )

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("label-guard")), route.route("zantor"))
        assertEquals("the model is never asked for an exact label", 0, asked)
        assertEquals(
            listOf(
                ToolInvocation(
                    ToolIds.LAUNCH_APP,
                    mapOf("query" to ArgSource.Literal("zantor"), "app" to ArgSource.Literal(lumora.packageName)),
                ),
            ),
            store.saved.single().plan.steps.map { it.invocation },
        )
    }

    private companion object {
        /** Invented (R14-43): no app, alias or command in any fixture contains it. */
        const val SAME_WORD = "gurmvex"
    }
}
