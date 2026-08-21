package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeCommandPlanner
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.Planner
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.SimpleCommand
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.tool.ToolRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The deterministic gate in front of the planner (AIL-4 forks R1/R2/R4, re-anchored by Этап 4.0 /
 * ADR 1/4 onto `localOnlyMode`).
 *
 * Two properties are load-bearing and are what this class exists to hold down.
 *
 * **`DOC-ADL-3` - parity.** Local-only / no provider / offline ⇒ the planner is never consulted and
 * nothing leaves the device, and every outcome FastPath actually *decided* is returned byte-for-byte.
 * Note what Этап 4.0 changed and what it did not: parity is a property of FastPath's decisions and of
 * the absence of any outbound call. It was never a promise to keep answering "Unknown command" when
 * FastPath decided nothing - that answer was a claim about the *command* while the truth was about
 * the *system*, and the three [CommandMessage] states replace it. A test asserting the old string
 * would be testing the lie.
 *
 * **`DOC-NYH-1` - nothing auto-executes.** A planner proposal still surfaces only as a non-executing
 * [CommandOutcome.RoutedAction] (Fork R4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteCommandUseCaseTest {

    private val matcher = FakeIntentMatcher()
    private val appsRepo = FakeInstalledAppsRepository()
    private val executor = FakeActionExecutor()
    private val planner = FakeCommandPlanner()
    private val connectivity = FakeConnectivityChecker(initiallyOnline = true)

    // The agent side of the cut (Task 11). The real `TemplatePlanner` over the real A0 tool set, not a
    // stub that always says yes: the branch is only worth testing if the plan it produces is the one
    // the slice actually ships.
    private val agentStore = FakeAgentSessionStore()
    private val toolRegistry = FakeToolRegistry.withA0Tools()
    private val agentIds = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    private val catalog: ActionCatalog = object : ActionCatalog {
        private val list = listOf(
            ActionDescriptor(
                id = ActionIds.OPEN_URL,
                title = "Open link",
                description = "Open a web address",
                category = ActionCategory.WEB,
                risk = ActionRiskLevel.CONFIRM,
                argSchema = listOf(ActionArg("url", description = "url")),
            ),
            ActionDescriptor(
                id = ActionIds.SHOW_APPS,
                title = "All apps",
                description = "Show the app grid",
                category = ActionCategory.SYSTEM,
                risk = ActionRiskLevel.SAFE,
            ),
        )
        override fun all(): List<ActionDescriptor> = list
        override fun descriptor(id: ActionId): ActionDescriptor? = list.firstOrNull { it.id == id }
    }

    private val provider = configuredProvider()

    /**
     * [localOnly] `false` + a configured provider + online is the **new default posture** (ADR 1/4):
     * understanding is available unless something concrete prevents it. Each test that exercises a
     * prevented state turns exactly one of those three off.
     */
    private fun useCase(
        localOnly: Boolean = false,
        agentPlanner: Planner = TemplatePlanner(),
    ): RouteCommandUseCase = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = matcher,
            resolver = IntentActionResolver(appsRepo),
            executor = executor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
        ),
        planner = planner,
        catalog = catalog,
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = localOnly)),
        providerConfigRepository = provider,
        connectivityChecker = connectivity,
        startAgentSession = StartAgentSessionUseCase(agentPlanner, agentStore, agentIds, toolRegistry),
    )

    private fun driveUnknown() {
        matcher.intentToReturn = LauncherIntent.UnknownIntent(originalInput = "do a barrel roll", reason = "x")
        matcher.confidenceToReturn = 0.0f
    }

    private fun driveConfidentShowApps() {
        matcher.intentToReturn = LauncherIntent.SimpleCommandIntent(SimpleCommand.SHOW_APPS)
        matcher.confidenceToReturn = 0.99f
    }

    /** FastPath DECIDES "open убер" and, with no such app installed, cannot ACHIEVE it. */
    private fun driveAppLaunch(query: String = "убер") {
        matcher.intentToReturn = LauncherIntent.LaunchAppIntent(displayNameQuery = query)
        matcher.confidenceToReturn = 0.99f
    }

    /** An A0 planner that plans nothing, so the cut has to fail open to the FastPath outcome. */
    private fun neverPlans(): Planner = object : Planner {
        override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult =
            PlanningResult.NoPlan
    }

    @Test
    fun `local-only and FastPath missed - says so plainly, planner never consulted (DOC-ADL-3)`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.ShowApps, 0.9f)

        val outcome = useCase(localOnly = true).route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `local-only and FastPath decided - outcome is byte-for-byte the FastPath one (DOC-ADL-3)`() = runTest {
        driveConfidentShowApps()
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.OpenUrl("https://x.test"), 0.9f)

        val outcome = useCase(localOnly = true).route("apps")

        assertEquals(CommandOutcome.ShowApps, outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `FastPath hit - planner never consulted, latency optimization not a filter (R1)`() = runTest {
        driveConfidentShowApps()
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.OpenUrl("https://x.test"), 0.9f)

        val outcome = useCase().route("apps")

        assertEquals(CommandOutcome.ShowApps, outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `offline - honest needs-network, planner never consulted (DOC-ADL-3)`() = runTest {
        driveUnknown()
        connectivity.online = false
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.ShowApps, 0.9f)

        val outcome = useCase().route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingNeedsNetwork), outcome)
        assertEquals(0, planner.planCallCount)
    }

    /**
     * Fork F4 made this a property of the GATE rather than of [LlmCommandPlanner]'s implementation.
     * Before Этап 4.0 nothing left the device without a provider only because that one adapter
     * happened to return `NoPlan` first; a second planner implementation, or a reordering inside this
     * one, would have silently ended the guarantee. `planCallCount == 0` is the guarantee: the gate
     * refuses to ask before there is anyone to ask.
     */
    @Test
    fun `no provider configured - honest needs-provider, planner never consulted (F4)`() = runTest {
        driveUnknown()
        provider.clearActiveConfig()
        connectivity.online = true
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.ShowApps, 0.9f)

        val outcome = useCase().route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingNeedsProvider), outcome)
        assertEquals(0, planner.planCallCount)
    }

    /**
     * The three unavailable-states are an ordered chain of early returns, so they can never overlap.
     * With ALL THREE causes present at once exactly one message is produced, and it is the outermost:
     * telling a user in local-only mode to go configure a provider would be advice for a problem they
     * do not have.
     */
    @Test
    fun `all three causes at once - only the outermost is reported`() = runTest {
        driveUnknown()
        provider.clearActiveConfig()
        connectivity.online = false

        val outcome = useCase(localOnly = true).route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), outcome)
        assertEquals(0, planner.planCallCount)
    }

    /** An empty submit is a hint, never a goal: it must not cost a round-trip or an excuse. */
    @Test
    fun `empty input - never reaches the planner and is not an unavailable-state`() = runTest {
        connectivity.online = false

        val outcome = useCase().route("   ")

        assertEquals(CommandOutcome.Empty, outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `planner returns NoPlan - keeps the FastPath outcome`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.NoPlan

        val outcome = useCase().route("do a barrel roll")

        assertEquals(CommandOutcome.Unknown("do a barrel roll"), outcome)
        assertEquals(1, planner.planCallCount)
    }

    @Test
    fun `CONFIRM-risk proposal surfaces as needsConfirmation RoutedAction (R4)`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.OpenUrl("https://x.test"), 0.8f)

        val outcome = useCase().route("go to x.test")

        assertEquals(
            CommandOutcome.RoutedAction(LauncherAction.OpenUrl("https://x.test"), 0.8f, needsConfirmation = true),
            outcome,
        )
        assertEquals("go to x.test", planner.lastCommand)
    }

    @Test
    fun `SAFE proposal surfaces as one-tap RoutedAction, still not executed (R4)`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.ShowApps, 0.7f)

        val outcome = useCase().route("show me everything")

        assertEquals(
            CommandOutcome.RoutedAction(LauncherAction.ShowApps, 0.7f, needsConfirmation = false),
            outcome,
        )
    }

    @Test
    fun `Clarify becomes a Message`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.Clarify("Which app did you mean?")

        val outcome = useCase().route("open the thing")

        assertEquals(CommandOutcome.Message(CommandMessage.Verbatim("Which app did you mean?")), outcome)
    }

    @Test
    fun `rule path runs exactly once even when planner is consulted`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.NoPlan

        useCase().route("do a barrel roll")

        // handle() → matcher.match() exactly once: the router wraps, never double-runs the rule path.
        assertEquals(1, matcher.callCount)
    }

    // --- Task 11: the cut into the command pipeline ---------------------------------------------

    /**
     * The one outcome A0 hands to the agent: a goal FastPath **decided** (it understood "open X") but
     * could not **achieve** (no such app). Not "any Message", not "anything that did not execute".
     */
    @Test
    fun `a command for an app that is not installed becomes an agent session`() = runTest {
        driveAppLaunch()
        appsRepo.appsToReturn = emptyList()

        val outcome = useCase().route("открой убер")

        assertTrue("expected an agent session, was $outcome", outcome is CommandOutcome.AgentSessionStarted)
    }

    /**
     * The branch sits ABOVE the local-only check on purpose, and that is not a hole in `DOC-ADL-3`:
     * the A0 planner is deterministic and offline, so the model planner is still never consulted and
     * nothing leaves the device.
     */
    @Test
    fun `the agent runs in local-only mode and the model planner is still never consulted`() = runTest {
        driveAppLaunch()
        appsRepo.appsToReturn = emptyList()

        val outcome = useCase(localOnly = true).route("открой убер")

        assertTrue("expected an agent session, was $outcome", outcome is CommandOutcome.AgentSessionStarted)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `the agent runs offline and with no provider configured, and nothing is transmitted`() = runTest {
        driveAppLaunch()
        appsRepo.appsToReturn = emptyList()
        connectivity.online = false
        provider.clearActiveConfig()

        val outcome = useCase().route("открой убер")

        assertTrue("expected an agent session, was $outcome", outcome is CommandOutcome.AgentSessionStarted)
        assertEquals(0, planner.planCallCount)
    }

    /** Fails open: no plan, no session, and the FastPath outcome is returned byte-for-byte. */
    @Test
    fun `when the agent produces no plan the FastPath outcome is returned byte-for-byte`() = runTest {
        driveAppLaunch()
        appsRepo.appsToReturn = emptyList()

        val outcome = useCase(agentPlanner = neverPlans()).route("открой убер")

        assertEquals(CommandOutcome.Message(CommandMessage.NoAppFound("убер")), outcome)
        assertTrue("nothing may be persisted when there is no plan", agentStore.saved.isEmpty())
    }

    /** An outcome FastPath decided AND achieved is not the agent's business. */
    @Test
    fun `an outcome FastPath decided and achieved is untouched by the agent`() = runTest {
        driveAppLaunch()
        appsRepo.appsToReturn = listOf(InstalledApp("com.uber", "убер", "Main"))

        val outcome = useCase().route("открой убер")

        assertEquals(CommandOutcome.Executed, outcome)
        assertTrue("no session may be started for an achieved goal", agentStore.saved.isEmpty())
    }
}
