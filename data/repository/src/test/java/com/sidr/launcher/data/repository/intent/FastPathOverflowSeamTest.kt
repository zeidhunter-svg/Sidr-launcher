package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.core.testing.FakeModelPlanner
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The seam A4′ phase 1 creates, crossed once, with every layer real that a JVM test can have.**
 *
 * Phase 1 moves a decision ACROSS a layer boundary: whether FastPath's 0.90 is earned is now decided
 * in `HandleUserCommandUseCase`, and what that decision is FOR happens one layer up, in
 * `RouteCommandUseCase` — step (2) (the A0 store plan) versus steps (3)–(7) (local-only message, or the
 * model). Each layer's own tests use a fake of the other (`RouteCommandUseCaseTest` drives
 * `FakeIntentMatcher`), which is exactly how A1′ shipped four green layers and a dead capability
 * (§0 «Механика SDD-блока»). So here the matcher is the real [RuleBasedIntentMatcher], the use case
 * and the resolver are real, and the router is real; only the world (installed apps, executor, model
 * planner, provider, connectivity, session store) is faked, each in the state that makes the branch
 * under test the live one.
 *
 * Fixtures (R14-43): "Lumora" is the only installed label; "veltrix", "quendor saga" and "corvane"
 * are not installed; "lumora studio" extends the installed label ON PURPOSE (owner fork F1-3's case).
 * Every expected value is compared by equality, never by `contains`.
 */
class FastPathOverflowSeamTest {

    private val apps = FakeInstalledAppsRepository().apply {
        appsToReturn = listOf(InstalledApp("net.fixture.alpha", "Lumora"))
    }
    private val executor = FakeActionExecutor()
    private val modelPlanner = FakeModelPlanner()
    private val agentStore = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    private fun router(localOnly: Boolean = false) = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = RuleBasedIntentMatcher(),
            resolver = IntentActionResolver(apps),
            executor = executor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(SupervisorJob()),
        ),
        modelPlanner = modelPlanner,
        binder = FakeMentionBinder(),
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = localOnly)),
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
        startAgentSession = StartAgentSessionUseCase(TemplatePlanner(), agentStore, ids, FakeToolRegistry.withA0Tools()),
    )

    /**
     * Spec §5.4 (в), §10 criterion 1: the A0 branch — accepted by the owner on the device 2026-08-22
     * and written into `DOC-ADL-3`'s second amendment — survives phase 1 byte for byte. Held
     * separately from the phase's positive tests so that breaking A0 goes red on its own. Three
     * shapes: a one-word name, a two-word name with nothing of it installed, and an installed brand's
     * variant that is not installed — owner fork F1-3 (2026-09-26): the world, not the parse, so the
     * store offer stays.
     */
    @Test
    fun `a launch naming nothing installed still becomes the A0 launch-then-store plan, byte for byte`() = runTest {
        val outcomes = listOf("открой veltrix", "открой quendor saga", "открой lumora studio").map { router().route(it) }

        outcomes.forEach { assertTrue("expected an agent session, was $it", it is CommandOutcome.AgentSessionStarted) }
        assertEquals(
            listOf(
                AgentGoal("открой veltrix", GoalShape.AppNotInstalled("veltrix")),
                AgentGoal("открой quendor saga", GoalShape.AppNotInstalled("quendor saga")),
                AgentGoal("открой lumora studio", GoalShape.AppNotInstalled("lumora studio")),
            ),
            agentStore.saved.map { it.goal },
        )
        assertEquals(
            listOf(
                ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("veltrix"))),
                ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            ),
            agentStore.saved.first().plan.steps.map { it.invocation },
        )
        assertEquals(0, modelPlanner.planCallCount)
    }

    @Test
    fun `a launch carrying a second goal reaches the model instead of the store, even when its app is installed`() = runTest {
        val outcome = router().route("открой lumora и проверь почту")

        assertEquals(CommandOutcome.LowConfidence, outcome)
        assertEquals("the model is asked exactly once", 1, modelPlanner.planCallCount)
        assertTrue("no store plan may start", agentStore.saved.isEmpty())
        assertTrue("nothing is launched", executor.executedActions.isEmpty())
    }

    /** `DOC-ADL-3` as amended: in a local state the model is not asked and nothing leaves the device. */
    @Test
    fun `in local-only mode the same command says so plainly and consults nothing`() = runTest {
        val outcome = router(localOnly = true).route("открой lumora и проверь почту")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), outcome)
        assertEquals(0, modelPlanner.planCallCount)
        assertTrue(agentStore.saved.isEmpty())
    }

    @Test
    fun `a launch carrying a second goal with nothing of it installed reaches the model too`() = runTest {
        val outcome = router().route("open corvane and send the report")

        assertEquals(CommandOutcome.LowConfidence, outcome)
        assertEquals(1, modelPlanner.planCallCount)
        assertTrue(agentStore.saved.isEmpty())
    }

    @Test
    fun `a search carrying a second goal is not run and reaches the model`() = runTest {
        val outcome = router().route("найди билеты в казань и закажи такси")

        assertEquals(CommandOutcome.LowConfidence, outcome)
        assertTrue("the search must not run with the second goal inside it", executor.executedActions.isEmpty())
        assertEquals(1, modelPlanner.planCallCount)
    }

    /** Spec §5.2: «не понижаем весь рукав» — at the seam, not only in the matcher. */
    @Test
    fun `a search whose conjunction joins two nouns is still run by FastPath and the model is not asked`() = runTest {
        val outcome = router().route("найди рецепт хлеба и соли")

        assertEquals(CommandOutcome.Executed, outcome)
        val search = executor.executedActions.single() as ExecutableAction.OpenSearchAction
        assertEquals("рецепт хлеба и соли", search.query)
        assertEquals(0, modelPlanner.planCallCount)
    }
}
