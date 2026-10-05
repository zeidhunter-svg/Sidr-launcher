package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.core.testing.FakeModelPlanner
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.Planner
import com.sidr.launcher.domain.agent.PlanningRequest
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.SidrDestination
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.intent.SimpleCommand
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
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
 * **`DOC-ADL-3` - parity, re-anchored to the wording amended 2026-08-22** (A0 Task 14; spec §8.1
 * required this re-anchoring and the docs-only commit `ccf7426` missed it — the A0 Task 14 review
 * caught it). The rule is now exactly two claims: local-only / no provider / offline ⇒ the **model**
 * planner is never consulted, and nothing leaves the device. The former trailing clause — "every
 * outcome FastPath *decided* is returned byte-for-byte" — no longer holds unqualified, because A0
 * replaces `NoAppFound` with a deterministic two-step plan. What survives, and what
 * `local-only and FastPath decided…` and `an outcome FastPath decided **and achieved** is untouched by
 * the agent` hold below, is that a decided *and achieved* outcome is byte-identical. The agent side of
 * the same rule is held by `AgentEgressSentinelGuardTest`, not here.
 * Note what Этап 4.0 changed and what it did not: parity is a property of FastPath's decisions and of
 * the absence of any outbound call. It was never a promise to keep answering "Unknown command" when
 * FastPath decided nothing - that answer was a claim about the *command* while the truth was about
 * the *system*, and the three [CommandMessage] states replace it. A test asserting the old string
 * would be testing the lie.
 *
 * **`DOC-NYH-1` - nothing auto-executes.** A model reading surfaces only as a non-executing
 * [CommandOutcome.Interpreted] (Fork R4; A4′ phase 3a, spec §7.12.3 condition 1).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteCommandUseCaseTest {

    private val matcher = FakeIntentMatcher()
    private val appsRepo = FakeInstalledAppsRepository()
    private val executor = FakeActionExecutor()
    private val planner = FakeModelPlanner()
    private val binder = FakeMentionBinder()
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
        modelPlanner = planner,
        binder = binder,
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
        override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult =
            PlanningResult.NoPlan
    }

    @Test
    fun `local-only and FastPath missed - says so plainly, planner never consulted (DOC-ADL-3)`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

        val outcome = useCase(localOnly = true).route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `local-only and FastPath decided - outcome is byte-for-byte the FastPath one (DOC-ADL-3)`() = runTest {
        driveConfidentShowApps()
        planner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

        val outcome = useCase(localOnly = true).route("apps")

        assertEquals(CommandOutcome.ShowApps, outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `FastPath hit - planner never consulted, latency optimization not a filter (R1)`() = runTest {
        driveConfidentShowApps()
        planner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

        val outcome = useCase().route("apps")

        assertEquals(CommandOutcome.ShowApps, outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `offline - honest needs-network, planner never consulted (DOC-ADL-3)`() = runTest {
        driveUnknown()
        connectivity.online = false
        planner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

        val outcome = useCase().route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingNeedsNetwork), outcome)
        assertEquals(0, planner.planCallCount)
    }

    /**
     * Fork F4 made this a property of the GATE rather than of any one planner's implementation (the
     * router's `LlmCommandPlanner` then; the bridge's `BridgeModelPlanner` since A4′ phase 3a).
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
        planner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

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
    fun `the model declines - keeps the FastPath outcome`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Declined

        val outcome = useCase().route("do a barrel roll")

        assertEquals(CommandOutcome.Unknown("do a barrel roll"), outcome)
        assertEquals(1, planner.planCallCount)
    }

    @Test
    fun `Clarify becomes a Message`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Clarify("Which app did you mean?")

        val outcome = useCase().route("open the thing")

        assertEquals(CommandOutcome.Message(CommandMessage.Verbatim("Which app did you mean?")), outcome)
    }

    @Test
    fun `rule path runs exactly once even when planner is consulted`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Declined

        useCase().route("do a barrel roll")

        // handle() → matcher.match() exactly once: the router wraps, never double-runs the rule path.
        assertEquals(1, matcher.callCount)
        // The premise the name states (Task 11): the planner WAS consulted on this path.
        assertEquals(1, planner.planCallCount)
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

    /**
     * The other half of "the cut is narrow by construction" (spec §5) — the half nothing held.
     * `Help` is a real, reachable FastPath outcome that **is** a `Message` and is **not**
     * `NoAppFound`, so it is exactly what the "not any Message" clause is about.
     *
     * Found by mutation: widening the key from `is CommandMessage.NoAppFound` to any `Message` left
     * every other test in this class green, and would turn "помощь" into an agent session that plans
     * a Play Store search for the word "помощь".
     */
    @Test
    fun `a decided Message that is not NoAppFound is not handed to the agent`() = runTest {
        matcher.intentToReturn = LauncherIntent.SimpleCommandIntent(SimpleCommand.HELP)
        matcher.confidenceToReturn = 0.99f

        val outcome = useCase().route("помощь")

        assertEquals(CommandOutcome.Message(CommandMessage.Help), outcome)
        assertTrue("only NoAppFound may open the agent branch", agentStore.saved.isEmpty())
    }

    /**
     * **What** is handed over, not merely that something was — the gap the five tests above shared.
     *
     * The shape's argument is the app name FastPath extracted (`NoAppFound.query`), never the whole
     * command: with `AppNotInstalled("открой убер")` step 0 tries to launch that literal phrase and
     * step 1 searches the store for it. `text` carries the raw command trimmed, because it is what
     * A4''s model planner will read and what the surface shows. The padded input pins the trim.
     *
     * Found by mutation: substituting `rawInput.trim()` for `message.query` was green everywhere.
     */
    @Test
    fun `the goal handed to the agent carries the extracted app name, not the whole command`() = runTest {
        driveAppLaunch()
        appsRepo.appsToReturn = emptyList()

        val outcome = useCase().route("  открой убер  ")

        assertTrue("expected an agent session, was $outcome", outcome is CommandOutcome.AgentSessionStarted)
        assertEquals(
            AgentGoal(text = "открой убер", shape = GoalShape.AppNotInstalled("убер")),
            agentStore.saved.first().goal,
        )
    }

    // --- Task 9: routing step 2b — a FastPath miss may reach a registered tool ------------------

    /**
     * A planner that plans one SAFE step, standing in for a matched Tier-0 tool.
     *
     * **Its arguments are derived from the descriptor, not typed in.** The brief wrote
     * `mapOf("query" to ArgSource.Literal("x"))`, which happens to validate against
     * [FakeToolRegistry.withA0Tools]'s first tool (`launch_app` declares a required `query`) — but only
     * by coincidence of that fixture's contents. `StartAgentSessionUseCase.start` runs
     * `InvocationValidator.validate` over the plan before persisting anything, so a stand-in naming an
     * argument the descriptor does not declare would be rejected as `UNDECLARED_ARG` and the tests
     * below would assert a shape that can never occur. Reading the schema makes the fake faithful to
     * what [com.sidr.launcher.domain.agent.Planner] implementations actually do (`ToolMatchPlanner`
     * fills exactly the declared args) and keeps it correct for whatever the fixture holds.
     */
    private fun plansOneStep(): Planner = object : Planner {
        override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
            val tool = registry.all().firstOrNull() ?: return PlanningResult.NoPlan
            val args = tool.argSchema
                .filter { it.required }
                .associate { it.name to ArgSource.Literal("x") }
            return PlanningResult.Planned(
                ExecutionPlan(
                    listOf(
                        PlanStep(
                            index = 0,
                            invocation = ToolInvocation(tool.id, args),
                            risk = tool.risk,
                            precondition = StepPrecondition.None,
                            rationale = StepRationale.GOAL_DIRECT,
                        ),
                    ),
                ),
            )
        }
    }

    /**
     * The three local states are the acceptance-sensitive surface. A planner that plans nothing must
     * leave every one of them byte-for-byte identical, or step 2b is a behaviour change wearing a
     * capability's clothes. All three were green **before** step 2b existed and must still be green
     * after — that is the whole point of pinning them here.
     */
    @Test
    fun `local-only with no matching tool behaves exactly as before`() = runTest {
        driveUnknown()

        val outcome = useCase(localOnly = true, agentPlanner = neverPlans()).route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), outcome)
        assertEquals(0, planner.planCallCount)
        assertTrue("a goal nothing planned may not be persisted", agentStore.saved.isEmpty())
    }

    @Test
    fun `no provider with no matching tool still says no provider`() = runTest {
        driveUnknown()
        provider.clearActiveConfig()
        connectivity.online = true

        val outcome = useCase(agentPlanner = neverPlans()).route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingNeedsProvider), outcome)
        assertEquals(0, planner.planCallCount)
        assertTrue("a goal nothing planned may not be persisted", agentStore.saved.isEmpty())
    }

    @Test
    fun `offline with no matching tool still says needs network`() = runTest {
        driveUnknown()
        connectivity.online = false

        val outcome = useCase(agentPlanner = neverPlans()).route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingNeedsNetwork), outcome)
        assertEquals(0, planner.planCallCount)
        assertTrue("a goal nothing planned may not be persisted", agentStore.saved.isEmpty())
    }

    /**
     * `DOC-ADL-3` as amended 2026-08-22: deterministic plan replay is part of the local path and MAY
     * change an outcome FastPath decided. What stays invariant is that no model is consulted and
     * nothing leaves the device — `AgentEgressSentinelGuardTest` holds that half, and this test's
     * planner is [plansOneStep], which is not a model.
     */
    @Test
    fun `a matching tool starts an agent session even in local-only mode`() = runTest {
        driveUnknown()

        val outcome = useCase(localOnly = true, agentPlanner = plansOneStep()).route("do a barrel roll")

        assertTrue("expected an agent session, was $outcome", outcome is CommandOutcome.AgentSessionStarted)
        assertEquals(0, planner.planCallCount)
    }

    /**
     * **What** is handed over, not merely that something was.
     *
     * Step 2b builds [GoalShape.Free] and never [GoalShape.AppNotInstalled] — the latter is block (2)'s
     * shape, and a mutation swapping them stays green in every other test here because [plansOneStep]
     * ignores the shape entirely. Against the shipped `CompositePlanner` it would not: `TemplatePlanner`
     * claims `AppNotInstalled` and would plan a Play Store search for the whole unrecognised command.
     *
     * `text` carries the raw command **trimmed**, exactly as block (2) does. The padded input pins it.
     */
    @Test
    fun `the goal step 2b hands over is the trimmed raw command under GoalShape Free`() = runTest {
        driveUnknown()

        val outcome = useCase(agentPlanner = plansOneStep()).route("  do a barrel roll  ")

        assertTrue("expected an agent session, was $outcome", outcome is CommandOutcome.AgentSessionStarted)
        assertEquals(
            AgentGoal(text = "do a barrel roll", shape = GoalShape.Free("do a barrel roll")),
            agentStore.saved.first().goal,
        )
    }

    /**
     * Step 2b is keyed on `isUndecided()`, so a confident FastPath hit keeps its byte-for-byte return
     * and never becomes an agent session.
     *
     * This one is green **before** step 2b exists as well as after — it is a mutation guard, not a
     * failing-first test. Its non-vacuity was checked by planting the mutation it exists to catch:
     * dropping the `ruleOutcome.isUndecided()` condition from step 2b turns it RED.
     */
    @Test
    fun `a decided FastPath outcome is never handed to the tool planner`() = runTest {
        driveConfidentShowApps()

        val outcome = useCase(agentPlanner = plansOneStep()).route("show apps")

        assertEquals(CommandOutcome.ShowApps, outcome)
        assertTrue("only an undecided command may open step 2b", agentStore.saved.isEmpty())
    }

    // --- A4′ phase 3a: the one model path (spec §7.12.3) ----------------------------------------

    /**
     * Criterion 15, the route half: the model's reading becomes a card; NO session is started.
     *
     * The padded input pins step 7's trim twice over (Task 11 ruling on hole H1): what the MODEL is asked,
     * and the goal the card will start — the trimmed command under [GoalShape.Free], never
     * `AppNotInstalled`, which is block (2)'s shape and which `TemplatePlanner` would claim.
     */
    @Test
    fun `a model plan becomes an interpretation, and no session is started`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Proposed(
            ProposedStep(
                tool = ToolIds.PLAY_STORE_SEARCH,
                args = mapOf("query" to "lumora"),
                origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                risk = ActionRiskLevel.CONFIRM,
                effect = ToolEffect.EXTERNAL,
            ),
        )

        val outcome = useCase().route("  найди lumora в магазине  ")

        val act = (outcome as CommandOutcome.Interpreted).interpretation as Interpretation.Act
        assertEquals(ToolIds.PLAY_STORE_SEARCH, act.step.invocation.id)
        assertEquals("the model is asked the trimmed command", "найди lumora в магазине", planner.lastCommand)
        assertEquals(
            AgentGoal(text = "найди lumora в магазине", shape = GoalShape.Free("найди lumora в магазине")),
            act.goal,
        )
        assertTrue("nothing is started before «Выполнить»", agentStore.saved.isEmpty())
    }

    @Test
    fun `an assistant answer opens the assistant with the question`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.ToAssistant("почему небо синее")

        assertEquals(CommandOutcome.OpenAssistant("почему небо синее"), useCase().route("почему небо синее"))
    }

    @Test
    fun `a navigation answer is shown before it opens anything`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

        assertEquals(
            CommandOutcome.Interpreted(Interpretation.Navigate(SidrDestination.APP_GRID)),
            useCase().route("покажи все приложения"),
        )
    }

    /** Total over the reasons (Task 11 ruling on hole H3): each one is passed through, none is collapsed. */
    @Test
    fun `an unavailable model is said so, with its reason`() = runTest {
        driveUnknown()

        ModelUnavailableReason.entries.forEach { reason ->
            planner.answer = ModelPlanning.Unavailable(reason)

            assertEquals(
                "$reason",
                CommandOutcome.Message(CommandMessage.UnderstandingModelUnavailable(reason)),
                useCase().route("сделай что-нибудь"),
            )
        }
        assertEquals("every reason was routed", ModelUnavailableReason.entries.size, planner.planCallCount)
    }

    /**
     * Criterion 15, the other half (plan review): the deterministic paths get NO card. The model here
     * WOULD answer with a step, so "it was not asked" is not vacuous — FastPath's decision, A0's plan and
     * step 2b's tool match each return before step 7, and none of them waits on «Выполнить».
     */
    @Test
    fun `the deterministic paths never get an interpretation card`() = runTest {
        planner.answer = ModelPlanning.Proposed(
            ProposedStep(
                tool = ToolIds.PLAY_STORE_SEARCH,
                args = mapOf("query" to "lumora"),
                origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                risk = ActionRiskLevel.CONFIRM,
                effect = ToolEffect.EXTERNAL,
            ),
        )

        driveConfidentShowApps()
        val fastPath = useCase().route("apps")

        driveAppLaunch()
        appsRepo.appsToReturn = emptyList()
        val a0 = useCase().route("открой убер")

        driveUnknown()
        val toolMatch = useCase(agentPlanner = plansOneStep()).route("do a barrel roll")

        assertEquals(CommandOutcome.ShowApps, fastPath)
        assertTrue("A0: $a0", a0 is CommandOutcome.AgentSessionStarted)
        assertTrue("2b: $toolMatch", toolMatch is CommandOutcome.AgentSessionStarted)
        assertEquals(0, planner.planCallCount)
    }

    /** Review Focus 5, D1: what binding found goes to the user — the model is asked exactly once. */
    @Test
    fun `a mention that matches no app says so and asks the model nothing more`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Proposed(
            ProposedStep(
                tool = ToolIds.LAUNCH_APP,
                args = mapOf("app" to "телегу"),
                origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
                risk = ActionRiskLevel.SAFE,
                effect = ToolEffect.EXTERNAL,
            ),
        )
        binder.binding = { _, _ -> Binding.NotFound("телегу") }

        val outcome = useCase().route("открой телегу")

        assertEquals(CommandOutcome.Message(CommandMessage.NoAppFound("телегу")), outcome)
        assertEquals(1, planner.planCallCount)
    }

    /**
     * Task 11 ruling on hole H2: the device could not read its own app list. That is said as a failure —
     * never hidden behind FastPath's own answer (`Unknown`), which would blame the command for the state
     * of the phone — and the model is not asked again.
     */
    @Test
    fun `an unreadable app list is said as a failure, not hidden behind FastPath's answer`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Proposed(
            ProposedStep(
                tool = ToolIds.LAUNCH_APP,
                args = mapOf("app" to "lumora"),
                origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
                risk = ActionRiskLevel.SAFE,
                effect = ToolEffect.EXTERNAL,
            ),
        )
        binder.binding = { _, _ -> Binding.Unavailable }

        val outcome = useCase().route("открой lumora")

        assertEquals(CommandOutcome.Failed(CommandFailure.Generic), outcome)
        assertEquals(1, planner.planCallCount)
    }
}
