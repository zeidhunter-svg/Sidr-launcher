package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeBridgeTransport
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeLearnedPlanStore
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.core.testing.FakeModelPlanner
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.domain.action.ActionArg
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
import com.sidr.launcher.domain.agent.UnresolvedTarget
import com.sidr.launcher.domain.agent.bind
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures
import com.sidr.launcher.domain.ai.bridge.BridgeModelPlanner
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.PendingChoice
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.SidrDestination
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.toPlanStep
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.intent.SearchTarget
import com.sidr.launcher.domain.intent.SimpleCommand
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.DefaultLearnedPlans
import com.sidr.launcher.domain.memory.plan.LearnedPlanFixtures
import com.sidr.launcher.domain.memory.plan.LearnedPlanFixtures.modelText
import com.sidr.launcher.domain.memory.plan.LearnedPlanFixtures.pin
import com.sidr.launcher.domain.memory.plan.LearnedPlanFixtures.said
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.LearnedPlans
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.Replay
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
 * [CommandOutcome.Interpreted] (Fork R4; A4′ phase 3a, spec §7.12.3 condition 1) — **since D3's second amendment
 * (A4′ acceptance tail, task 4) only when it owes a pick or sends the model's own text out**; any other fresh reading
 * starts its session at once, marked as the model's, and runs nothing here: the engine runs it behind its consent gate
 * (`ModelReadingConsentTest`).
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
        agentRegistry: ToolRegistry = toolRegistry,
        modelPlanner: ModelPlanner = planner,
        // A4′ acceptance tail, task 3: the one alias store FastPath's resolver and step 1a read — as the graph hands it
        // to both. `AliasStore.NONE` by default, so every earlier test builds the route exactly as it did.
        aliases: AliasStore = AliasStore.NONE,
        // A4′ acceptance tail, task 4 (I5): what step 7 asks the cache to teach, for the tests that count it. `NONE` by
        // default, so every earlier test builds the route exactly as it did.
        learnedPlans: LearnedPlans = LearnedPlans.NONE,
    ): RouteCommandUseCase = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = matcher,
            resolver = IntentActionResolver(appsRepo, PlainTextFolding, aliases),
            executor = executor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
        ),
        modelPlanner = modelPlanner,
        binder = binder,
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = localOnly)),
        providerConfigRepository = provider,
        connectivityChecker = connectivity,
        startAgentSession = StartAgentSessionUseCase(agentPlanner, agentStore, agentIds, agentRegistry),
        learnedPlans = learnedPlans,
        aliases = aliases,
        installedApps = appsRepo,
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

    /**
     * A4′ acceptance tail, task 5 (owner decision 2: б): the model read the command and found no tool for it — the
     * user is told so («Понял команду, но Sidr так пока не умеет»), not that the command is «unknown», which blames
     * the command for what is a limit of the tool set. Before the tail this kept FastPath's `Unknown`.
     */
    @Test
    fun `the model declines - says it understood and Sidr cannot do it yet`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Declined

        val outcome = useCase().route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstoodNoAction), outcome)
        assertEquals(1, planner.planCallCount)
    }

    /**
     * K1 outranks the line: what FastPath yielded to the model (a search, a «нет такого приложения») and the model
     * declined is answered as FastPath would have answered it — a search run, a store plan — never «понял, но не
     * умею» in place of a working search.
     */
    @Test
    fun `a yielded command the model declines is not this line`() = runTest {
        driveConfidentSearch()
        planner.answer = ModelPlanning.Declined

        val search = useCase().route("найди $SEARCH_QUERY")

        assertNotEquals(CommandOutcome.Message(CommandMessage.UnderstoodNoAction), search)
        assertEquals("the search FastPath would have run", CommandOutcome.Executed, search)
        assertEquals(listOf(fastPathSearch), executor.executedActions)

        driveAppLaunch("клод")
        appsRepo.appsToReturn = emptyList()

        val missing = useCase().route("открой клод")

        assertNotEquals(CommandOutcome.Message(CommandMessage.UnderstoodNoAction), missing)
        assertEquals("the store plan FastPath's «нет такого приложения» would have started",
            CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), missing)
        assertEquals(storeGoalForKlod, agentStore.saved.single().goal)
    }

    /**
     * K11 outranks the line: a 2b app target the model was handed (K2) and declined is answered on the device —
     * «нет такого приложения» in the user's word — not «понял, но не умею».
     */
    @Test
    fun `a 2b target the model declines answers on the device, not this line`() = runTest {
        driveUnknown()
        val target = removeTarget("удали приложение клод", words = "клод", candidates = emptyList())
        planner.answer = ModelPlanning.Declined

        val outcome = useCase(agentPlanner = claims(target), agentRegistry = removeRegistry).route("удали приложение клод")

        assertNotEquals(CommandOutcome.Message(CommandMessage.UnderstoodNoAction), outcome)
        assertEquals(CommandOutcome.Message(CommandMessage.NoAppFound("клод")), outcome)
        assertEquals("the model was asked once", 1, planner.planCallCount)
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
     * D3, second amendment (A4′ acceptance tail, task 4; owner answer «13 / D3: да»): a FRESH model reading that owes
     * no pick and sends none of the model's own text off the phone ([needsCard] false) starts its session at once,
     * through `startPlanned` and the engine, and the outcome carries the model's mark (`proposedByModel`, controller
     * ruling K4). Replaces criterion 15's route half («the reading becomes a card; NO session is started») for exactly
     * these readings. The step is `CONFIRM`: starting is not running — the engine's consent card stops it
     * (`ModelReadingConsentTest`), and nothing is executed here.
     *
     * The padded input still pins step 7's trim twice over (Task 11 ruling on hole H1): what the MODEL is asked, and
     * the goal the session starts with — the trimmed command under [GoalShape.Free], never `AppNotInstalled`, which
     * is block (2)'s shape and which `TemplatePlanner` would claim.
     */
    @Test
    fun `a fresh reading with no pick and no model text going out starts at once, marked as the model's`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Proposed(
            listOf(
                ProposedStep(
                    tool = ToolIds.PLAY_STORE_SEARCH,
                    args = mapOf("query" to "lumora"),
                    origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    risk = ActionRiskLevel.CONFIRM,
                    effect = ToolEffect.EXTERNAL,
                ),
            ),
        )

        val outcome = useCase().route("  найди lumora в магазине  ")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1"), proposedByModel = true), outcome)
        assertEquals("the model is asked the trimmed command", "найди lumora в магазине", planner.lastCommand)
        val started = agentStore.saved.single()
        assertEquals(
            AgentGoal(text = "найди lumora в магазине", shape = GoalShape.Free("найди lumora в магазине")),
            started.goal,
        )
        assertEquals(
            listOf(ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("lumora")))),
            started.plan.steps.map { it.invocation },
        )
        assertTrue("the route starts a session; it runs nothing", executor.executedActions.isEmpty())
    }

    /** The pick the binder owes for «открой tessar»: two apps answer the words exactly. */
    private fun owedTessarPick() = Interpretation.Act(
        goal = freeGoal("открой tessar"),
        steps = listOf(
            PlanStep(
                index = 0,
                invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("tessar"))),
                risk = ActionRiskLevel.SAFE,
                precondition = StepPrecondition.None,
                rationale = StepRationale.MODEL_PROPOSED,
            ),
        ),
        origins = mapOf(0 to mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER))),
        effects = mapOf(0 to ToolEffect.EXTERNAL),
        choices = listOf(PendingChoice(0, AppChoice("app", "tessar", listOf(tessarOne, tessarTwo), learning = null))),
    )

    /** D3, second amendment: a reading that owes the user a pick is the card, as before — nothing starts. */
    @Test
    fun `a fresh reading that owes a pick gets the card`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Proposed(
            listOf(
                ProposedStep(
                    tool = ToolIds.LAUNCH_APP,
                    args = mapOf("app" to "tessar"),
                    origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    risk = ActionRiskLevel.SAFE,
                    effect = ToolEffect.EXTERNAL,
                ),
            ),
        )
        binder.binding = { _, _ -> Binding.Bound(owedTessarPick()) }

        val outcome = useCase().route("открой tessar")

        assertEquals(CommandOutcome.Interpreted(owedTessarPick()), outcome)
        assertTrue("nothing starts before the pick and «Выполнить»", agentStore.saved.isEmpty())
    }

    /**
     * D3, second amendment, `DOC-MZN-2`: a search whose text the MODEL wrote would leave the phone with that text — the
     * card shows it first and nothing starts. The same search in the USER's words is theirs going out: it starts at
     * once, marked as the model's. The two readings differ in the origin of the one value and in nothing else.
     */
    @Test
    fun `a fresh reading that sends the model's text off the phone gets the card`() = runTest {
        driveUnknown()
        fun search(origin: ValueOrigin) = ModelPlanning.Proposed(
            listOf(
                ProposedStep(
                    tool = WEB_SEARCH,
                    args = mapOf("query" to "орвелин история"),
                    origins = mapOf("query" to origin),
                    risk = ActionRiskLevel.SAFE,
                    effect = ToolEffect.EXTERNAL,
                ),
            ),
        )
        val route = useCase()

        planner.answer = search(ValueOrigin.ModelGenerated)
        val modelText = route.route("поищи что-нибудь про орвелин")
        assertEquals(
            CommandOutcome.Interpreted(
                Interpretation.Act(
                    goal = freeGoal("поищи что-нибудь про орвелин"),
                    steps = listOf(
                        PlanStep(
                            index = 0,
                            invocation = ToolInvocation(WEB_SEARCH, mapOf("query" to ArgSource.Literal("орвелин история"))),
                            risk = ActionRiskLevel.SAFE,
                            precondition = StepPrecondition.None,
                            rationale = StepRationale.MODEL_PROPOSED,
                        ),
                    ),
                    origins = mapOf(0 to mapOf("query" to ValueOrigin.ModelGenerated)),
                    effects = mapOf(0 to ToolEffect.EXTERNAL),
                    choices = emptyList(),
                ),
            ),
            modelText,
        )
        assertTrue("the model's text going out waits on the card", agentStore.saved.isEmpty())

        planner.answer = search(ValueOrigin.SaidBy(PrincipalId.USER))
        val usersWords = route.route("поищи орвелин история")
        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1"), proposedByModel = true), usersWords)
        assertEquals(
            listOf(ToolInvocation(WEB_SEARCH, mapOf("query" to ArgSource.Literal("орвелин история")))),
            agentStore.saved.single().plan.steps.map { it.invocation },
        )
    }

    /** A cache that teaches [shape] for any reading and counts how often step 7 asks it to — and replays nothing. */
    private class CountingLessons(private val shape: LearnedPlanShape) : LearnedPlans {
        var asked = 0
            private set

        override suspend fun replay(command: String): Replay = Replay.Miss
        override fun learnable(command: String, steps: List<ProposedStep>): LearnedPlanShape? {
            asked++
            return shape
        }
    }

    /**
     * Owner answer I5 «не засчитывать»: a session that started without the interpretation card teaches NOTHING — step
     * 7 does not even ask what it would teach, and `AgentSessionStarted` has no lesson to carry. Only a reading the
     * user confirmed on the card may teach (B-8). The control: the same cache, asked for a reading that needs the card
     * (model text going out), is asked once and its lesson rides on the card.
     */
    @Test
    fun `a session started without a card teaches nothing`() = runTest {
        driveUnknown()
        val lesson = LearnedPlanShape(
            CommandForm.parse("поищи …"),
            PlanTemplate(listOf(TemplateStep(WEB_SEARCH, mapOf("query" to TemplateArg.Slot(0))))),
        )
        val lessons = CountingLessons(lesson)
        val route = useCase(learnedPlans = lessons)
        fun search(origin: ValueOrigin) = ModelPlanning.Proposed(
            listOf(ProposedStep(WEB_SEARCH, mapOf("query" to "орвелин"), mapOf("query" to origin), ActionRiskLevel.SAFE, ToolEffect.EXTERNAL)),
        )

        planner.answer = search(ValueOrigin.SaidBy(PrincipalId.USER))
        val started = route.route("поищи орвелин")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1"), proposedByModel = true), started)
        assertEquals("a no-card start is not asked what it would teach", 0, lessons.asked)

        planner.answer = search(ValueOrigin.ModelGenerated)
        val card = route.route("поищи орвелин")

        assertEquals("the control: a card's reading is asked once", 1, lessons.asked)
        assertEquals(lesson, (card as CommandOutcome.Interpreted).learnable)
    }

    /**
     * D3, second amendment: a reading that would start at once but whose start FAILS — the store refused the save, or
     * the registry refuses the plan — is not lost: it comes back as the card, as before the amendment, with what it
     * would teach (a card's «Выполнить» is a confirmation, B-8). Nothing is started.
     */
    @Test
    fun `a fresh reading whose start fails keeps the card`() = runTest {
        driveUnknown()
        val lesson = LearnedPlanShape(
            CommandForm.parse("поищи …"),
            PlanTemplate(listOf(TemplateStep(WEB_SEARCH, mapOf("query" to TemplateArg.Slot(0))))),
        )
        val search = ProposedStep(
            WEB_SEARCH,
            mapOf("query" to "орвелин"),
            mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
            ActionRiskLevel.SAFE,
            ToolEffect.EXTERNAL,
        )
        planner.answer = ModelPlanning.Proposed(listOf(search))
        val card = Interpretation.Act(
            goal = freeGoal("поищи орвелин"),
            steps = listOf(search.toPlanStep(0, search.args)),
            origins = mapOf(0 to mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER))),
            effects = mapOf(0 to ToolEffect.EXTERNAL),
            choices = emptyList(),
        )

        agentStore.failNextSave = true
        val storeFailed = useCase(learnedPlans = CountingLessons(lesson)).route("поищи орвелин")
        val refused = useCase(agentRegistry = FakeToolRegistry(emptyList()), learnedPlans = CountingLessons(lesson)).route("поищи орвелин")

        assertEquals("the store refused the save", CommandOutcome.Interpreted(card, learnable = lesson), storeFailed)
        assertEquals("the registry refused the plan", CommandOutcome.Interpreted(card, learnable = lesson), refused)
        assertTrue("nothing is started", agentStore.saved.isEmpty())
    }

    /**
     * Fork 3b-B, A4′ phase 3b Task 9: a reading of two steps is handed to the binder WHOLE, once, and comes
     * back as ONE card carrying both steps in order — and still nothing is started (condition 1). The
     * binder is the fake's default (every step bound as the model wrote it), so the card is what the route
     * made of the whole reading; the expected card is spelled out, by equality. The plan is RUNNABLE in
     * [toolRegistry] — every step gives its required `query`, no reference — so a start, had step 7 tried
     * one, would pass `startPlanned`'s check and be saved: the empty store is evidence (finding E9-M1).
     *
     * **Since the D3 second amendment (task 4) the first step's text is the MODEL's** — a reading of two steps the user
     * said entirely would start at once — so the card is owed for that one value, and the whole reading still comes
     * back as one card: the property under test (one card for N steps) is unchanged.
     */
    @Test
    fun `a two-step model reading is one card and nothing is started`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Proposed(
            listOf(
                ProposedStep(
                    tool = ToolIds.PLAY_STORE_SEARCH,
                    args = mapOf("query" to "lumora"),
                    origins = mapOf("query" to ValueOrigin.ModelGenerated),
                    risk = ActionRiskLevel.CONFIRM,
                    effect = ToolEffect.EXTERNAL,
                ),
                ProposedStep(
                    tool = ToolIds.LAUNCH_APP,
                    args = mapOf("query" to "vemblic"),
                    origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    risk = ActionRiskLevel.SAFE,
                    effect = ToolEffect.LOCAL,
                ),
            ),
        )

        val outcome = useCase().route("найди lumora и открой vemblic")

        assertEquals(
            CommandOutcome.Interpreted(
                Interpretation.Act(
                    goal = AgentGoal(text = "найди lumora и открой vemblic", shape = GoalShape.Free("найди lumora и открой vemblic")),
                    steps = listOf(
                        PlanStep(
                            index = 0,
                            invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("lumora"))),
                            risk = ActionRiskLevel.CONFIRM,
                            precondition = StepPrecondition.None,
                            rationale = StepRationale.MODEL_PROPOSED,
                        ),
                        PlanStep(
                            index = 1,
                            invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("vemblic"))),
                            risk = ActionRiskLevel.SAFE,
                            precondition = StepPrecondition.None,
                            rationale = StepRationale.MODEL_PROPOSED,
                        ),
                    ),
                    origins = mapOf(
                        0 to mapOf("query" to ValueOrigin.ModelGenerated),
                        1 to mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    ),
                    effects = mapOf(0 to ToolEffect.EXTERNAL, 1 to ToolEffect.LOCAL),
                    choices = emptyList(),
                ),
            ),
            outcome,
        )
        assertEquals("the whole reading is bound in one call", 1, binder.bindCallCount)
        assertEquals("the model is asked once", 1, planner.planCallCount)
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
     *
     * **A0's plan is taken in local-only mode since owner decision 14** (A4′ acceptance tail, task 2; review I1):
     * online, with a provider, «нет такого приложения» goes to the model first, so it is no longer a
     * deterministic path there; without the model it is, byte for byte.
     */
    @Test
    fun `the deterministic paths never get an interpretation card`() = runTest {
        planner.answer = ModelPlanning.Proposed(
            listOf(
                ProposedStep(
                    tool = ToolIds.PLAY_STORE_SEARCH,
                    args = mapOf("query" to "lumora"),
                    origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    risk = ActionRiskLevel.CONFIRM,
                    effect = ToolEffect.EXTERNAL,
                ),
            ),
        )

        driveConfidentShowApps()
        val fastPath = useCase().route("apps")

        driveAppLaunch()
        appsRepo.appsToReturn = emptyList()
        val a0 = useCase(localOnly = true).route("открой убер")

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
            listOf(
                ProposedStep(
                    tool = ToolIds.LAUNCH_APP,
                    args = mapOf("app" to "телегу"),
                    origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    risk = ActionRiskLevel.SAFE,
                    effect = ToolEffect.EXTERNAL,
                ),
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
            listOf(
                ProposedStep(
                    tool = ToolIds.LAUNCH_APP,
                    args = mapOf("app" to "lumora"),
                    origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    risk = ActionRiskLevel.SAFE,
                    effect = ToolEffect.EXTERNAL,
                ),
            ),
        )
        binder.binding = { _, _ -> Binding.Unavailable }

        val outcome = useCase().route("открой lumora")

        assertEquals(CommandOutcome.Failed(CommandFailure.Generic), outcome)
        assertEquals(1, planner.planCallCount)
    }

    // --- A4′ phase 3b, Task 5: R14-39 closed — a target step 2b claimed is answered on the device ----

    /**
     * A tool shaped like `uninstall_app` — a required app argument, an optional label, `CONFIRM` — but
     * test-local: what is under test is the route's answer to an [UnresolvedTarget], whichever planner
     * produced it. The production planner's half is `ToolMatchPlannerTest`'s; the seam end to end is
     * `AgentActingSeamTest`'s R14-39 test.
     */
    private val removeAppTool = ToolDescriptor(
        id = ToolId("fixture_remove_app"),
        level = ToolLevels.SYSTEM_INTENT,
        effect = ToolEffect.EXTERNAL,
        argSchema = listOf(
            ActionArg("app", description = "The package to remove"),
            ActionArg("app_label", required = false, description = "The words the user typed for it"),
        ),
        risk = ActionRiskLevel.CONFIRM,
        durability = ToolDurability.DURABLE,
    )
    private val removeRegistry = FakeToolRegistry(listOf(removeAppTool))
    private val tessarOne = InstalledApp("net.fixture.tessar.one", "Tessar")
    private val tessarTwo = InstalledApp("net.fixture.tessar.two", "Tessar")

    private fun removeTarget(
        command: String,
        words: String,
        candidates: List<InstalledApp>,
        unreadable: Boolean = false,
    ) = UnresolvedTarget(
        goal = AgentGoal(command, GoalShape.Free(command)),
        pending = PlanStep(0, ToolInvocation(removeAppTool.id), removeAppTool.risk, StepPrecondition.None, StepRationale.GOAL_DIRECT),
        arg = "app",
        labelArg = "app_label",
        sort = ArgSorts.APP,
        words = words,
        candidates = candidates,
        confirm = false,
        unreadable = unreadable,
    )

    /** A deterministic planner that claims exactly [target]'s goal, as `ToolMatchPlanner` claims a tool's text. */
    private fun claims(target: UnresolvedTarget): Planner = object : Planner {
        override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult =
            if (request.goal == target.goal) PlanningResult.Unresolved(target) else PlanningResult.NoPlan
    }

    /**
     * Phase 3b close, final review B-M8 (and deferred E5-F8): in local-only mode the target a registered tool
     * claimed is still the DEVICE's to answer — «нет такого приложения» in the user's own words, not the
     * local-only line, which would blame the mode for a name that is simply not installed — and the model is
     * not asked. The control is the same mode with a text no planner claims: the local-only line, as before — so
     * the answer above is step 2b's, not the mode's.
     *
     * **Since controller ruling K2 (A4′ acceptance tail, task 2) this is THE test of «answered on the device»**:
     * its online sibling, `an unresolved 2b target is answered on the device and the model is never asked`, was
     * deleted — online, with a provider, an empty readable app target now goes to the model, which may correct
     * the name (`online an unresolved 2b app with no candidates goes to the model`), and its refusal is still
     * answered here, on the device (K11). No provider and offline are held below
     * (`with no provider or offline an unresolved 2b app is still answered on the device`).
     */
    @Test
    fun `in local-only mode an unresolved 2b target is still answered on the device`() = runTest {
        driveUnknown()
        val target = removeTarget("удали приложение вельтрикс", words = "вельтрикс", candidates = emptyList())
        val route = useCase(localOnly = true, agentPlanner = claims(target), agentRegistry = removeRegistry)

        assertEquals(
            CommandOutcome.Message(CommandMessage.NoAppFound("вельтрикс")),
            route.route("удали приложение вельтрикс"),
        )
        assertEquals("the model is not asked in local-only mode", 0, planner.planCallCount)
        assertTrue("nothing is persisted for a target nobody picked", agentStore.saved.isEmpty())

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), route.route("do a barrel roll"))
    }

    /**
     * Controller ruling E5-P1. The app list (or the alias store) could not be read, so the device does
     * not know whether the app is there: «нет такого приложения» would be a lie, and falling through
     * would let a disk error decide what leaves the device. It is answered exactly as step 7 answers
     * `Binding.Unavailable` — a generic failure — with no new message (`CommandFailure` is frozen).
     */
    @Test
    fun `an unreadable 2b target fails on the device and the model is never asked`() = runTest {
        driveUnknown()
        val target = removeTarget("удали приложение вельтрикс", "вельтрикс", emptyList(), unreadable = true)

        val outcome = useCase(agentPlanner = claims(target), agentRegistry = removeRegistry).route("удали приложение вельтрикс")

        assertEquals(CommandOutcome.Failed(CommandFailure.Generic), outcome)
        assertEquals(0, planner.planCallCount)
        assertTrue(agentStore.saved.isEmpty())
    }

    /**
     * Plan §0.2 B-7, spec criterion 15 (second half): a pick at 2b is the device's own question, not a
     * reading of the model — so it is [CommandOutcome.ChooseTarget], never an interpretation card, and
     * nothing starts before the tap. The tap is [bind] (controller ruling E5-P2 — the one function that
     * writes the package and the user's words into the pending step) and then `startPlanned`, exactly
     * what the launcher's command session does; the engine still gates the `CONFIRM` step by its risk.
     */
    @Test
    fun `a 2b pick starts the session on the tap, without an interpretation card`() = runTest {
        driveUnknown()
        val target = removeTarget("удали приложение tessar", "tessar", listOf(tessarOne, tessarTwo))

        val outcome = useCase(agentPlanner = claims(target), agentRegistry = removeRegistry).route("удали приложение tessar")

        assertEquals(CommandOutcome.ChooseTarget(target), outcome)
        assertEquals(0, planner.planCallCount)
        assertTrue("nothing starts before the tap", agentStore.saved.isEmpty())

        assertNull("a package that was not offered binds nothing", target.bind("net.fixture.zantor"))
        val plan = checkNotNull(target.bind(tessarTwo.packageName))
        val started = StartAgentSessionUseCase(claims(target), agentStore, agentIds, removeRegistry)
            .startPlanned(target.goal, plan)

        assertEquals(target.goal, checkNotNull((started as OperationResult.Success).value).goal)
        assertEquals(
            ToolInvocation(
                removeAppTool.id,
                mapOf("app" to ArgSource.Literal("net.fixture.tessar.two"), "app_label" to ArgSource.Literal("tessar")),
            ),
            agentStore.saved.single().plan.steps.single().invocation,
        )
    }

    // --- A4′ phase 3b, Task 6: a value its sort cannot read is said in the user's words, on the device ---

    /**
     * A tool shaped like `set_timer` — one required duration-sorted argument, `SAFE` — but test-local, as
     * [removeAppTool] is: what is under test is the route's answer, whichever planner or binder produced
     * it. The production halves are `ToolMatchPlannerTest`'s and `DeviceMentionBinderTest`'s.
     */
    private val countdownTool = ToolDescriptor(
        id = ToolId("fixture_countdown"),
        level = ToolLevels.SYSTEM_INTENT,
        effect = ToolEffect.EXTERNAL,
        argSchema = listOf(ActionArg("duration", description = "How long")),
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    /**
     * Plan Task 6, controller ruling E6-P2 (E5-F3) — **a fail-safe since controller ruling E6-R1.** An
     * [UnresolvedTarget] of a non-app sort with nobody to pick, as a planner could hand it to 2b. No
     * production planner does any more: `ToolMatchPlanner` answers a value its sort cannot read with
     * `NoPlan` (`ToolMatchPlannerTest > an unreadable duration at 2b is left to the model`), so the test-local
     * [claims] planner is what produces it here. What it holds is the route's answer, should one arrive:
     * «не понял» in the user's OWN words — [UnresolvedTarget.words], not the whole command, compared by
     * equality — on the device, rather than a generic failure; the model is not asked and nothing is
     * persisted.
     */
    @Test
    fun `not understood is said in the user's words`() = runTest {
        driveUnknown()
        val command = "поставь таймер на полчасика"
        val target = UnresolvedTarget(
            goal = AgentGoal(command, GoalShape.Free(command)),
            pending = PlanStep(0, ToolInvocation(countdownTool.id), countdownTool.risk, StepPrecondition.None, StepRationale.GOAL_DIRECT),
            arg = "duration",
            labelArg = null,
            sort = ArgSorts.DURATION,
            words = "полчасика",
            candidates = emptyList(),
            confirm = false,
        )

        val outcome = useCase(agentPlanner = claims(target), agentRegistry = FakeToolRegistry(listOf(countdownTool)))
            .route(command)

        assertEquals(CommandOutcome.Message(CommandMessage.ValueNotUnderstood("полчасика")), outcome)
        assertEquals("the model was asked about a value the device had claimed", 0, planner.planCallCount)
        assertTrue("nothing is persisted for a value nobody could read", agentStore.saved.isEmpty())
    }

    /**
     * Plan Task 6, controller ruling E6-P2: the bridge's half. The model answered with a step, and the
     * device's binding could not read the value it wrote ([Binding.NotUnderstood]). The user is told in
     * the words written for that argument — which at the bridge may be the MODEL's words (controller
     * ruling E6-R2) — and that is the end of it: a value the device cannot read is not a reason to ask the
     * model again, and nothing the binding found goes back (D1). The model is asked exactly once.
     */
    @Test
    fun `a value the bridge's binding did not understand is said in the words written for it and the model is asked once`() = runTest {
        driveUnknown()
        planner.answer = ModelPlanning.Proposed(
            listOf(
                ProposedStep(
                    tool = countdownTool.id,
                    args = mapOf("duration" to "полчасика"),
                    origins = mapOf("duration" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    risk = ActionRiskLevel.SAFE,
                    effect = ToolEffect.EXTERNAL,
                ),
            ),
        )
        binder.binding = { _, _ -> Binding.NotUnderstood("полчасика") }

        val outcome = useCase().route("заведи таймер на полчасика")

        assertEquals(CommandOutcome.Message(CommandMessage.ValueNotUnderstood("полчасика")), outcome)
        assertEquals(1, planner.planCallCount)
        assertEquals(1, binder.bindCallCount)
        assertTrue("nothing starts before «Выполнить», and there is nothing to run", agentStore.saved.isEmpty())
    }

    // --- A4′ phase 4, Task 4: step 2c — a learned command replays on the device (plan §0.2 B-5…B-8) ----------

    /**
     * The learned-plan store and what the replay's binder can bind — a mention exactly as typed to the apps it
     * names ([LearnedPlanFixtures.bindApps], the rule `LearnedPlanReplayTest` binds by). Two «Vemblic» apps are a
     * pick; «все фотографии» names nothing. Every app is invented (R14-43).
     */
    private val learnedStore = FakeLearnedPlanStore()
    private val vemblic = InstalledApp("net.fixture.vemblic", "Vemblic")
    private val vemblicTwo = InstalledApp("net.fixture.vemblic.two", "Vemblic")
    private val installedByName = mutableMapOf("Vemblic" to listOf(vemblic))

    /**
     * The route with the learned-plan cache, as Task 6 will compose it: ONE [binder] in the graph, asked by the
     * replay and by step 7 alike (scripted here with the fixtures' rule), and the bridge fixtures' learned registry
     * — the offering a replay is judged against, and the registry `startPlanned` validates the started plan in.
     * [useCase] above is untouched (B-12): every earlier test still builds the route without this parameter.
     */
    private fun learnedRoute(
        localOnly: Boolean = false,
        agentPlanner: Planner = TemplatePlanner(),
        agentRegistry: ToolRegistry = BridgeFixtures.learnedRegistry,
    ): RouteCommandUseCase {
        binder.binding = { goal, steps -> LearnedPlanFixtures.bindApps(goal, steps) { installedByName[it].orEmpty() } }
        val learned: LearnedPlans = DefaultLearnedPlans(
            store = learnedStore,
            registry = BridgeFixtures.learnedRegistry,
            descriptions = BridgeFixtures.learnedDescriptions,
            levels = BridgeFilter.ANDROID_OFFERED_LEVELS,
            binder = binder,
            folding = PlainTextFolding,
        )
        return RouteCommandUseCase(
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
            startAgentSession = StartAgentSessionUseCase(agentPlanner, agentStore, agentIds, agentRegistry),
            learnedPlans = learned,
        )
    }

    private fun freeGoal(command: String) = AgentGoal(text = command, shape = GoalShape.Free(command))

    /** «сотри lumora» → `uninstall_app(app = lumora)`, confirmed three times: the form «сотри …» is pinned. */
    private suspend fun pinErase() = learnedStore.pin("сотри lumora", said(LEARNED_UNINSTALL, "app" to "lumora"))

    /** What a replay of «сотри Vemblic» starts: the package bound on the device, the user's words beside it. */
    private val erasesVemblic = ToolInvocation(
        LEARNED_UNINSTALL,
        mapOf("app" to ArgSource.Literal("net.fixture.vemblic"), "app_label" to ArgSource.Literal("Vemblic")),
    )

    /**
     * B-6, B-7: online, with a provider — every condition under which step 7 WOULD ask the model — a pinned form
     * the command fits is replayed on the device and its session started at once: no card (nothing in it the user
     * has not confirmed three times), the mark carried on the outcome, and the model is never asked.
     */
    @Test
    fun `a learned command starts its session without a card and the model is never asked`() = runTest {
        driveUnknown()
        pinErase()

        val outcome = learnedRoute().route("сотри Vemblic")

        assertEquals(
            CommandOutcome.AgentSessionStarted(AgentSessionId("s1"), learned = CommandForm.parse("сотри …")),
            outcome,
        )
        assertEquals("the model is never asked", 0, planner.planCallCount)
        assertEquals(freeGoal("сотри Vemblic"), agentStore.saved.single().goal)
        assertEquals(erasesVemblic, agentStore.saved.single().plan.steps.single().invocation)
    }

    /**
     * `DOC-ADL-3` applied: deterministic replay is part of the local path, so local-only mode does not stop it —
     * the model planner is not consulted and nothing leaves the device.
     */
    @Test
    fun `in local-only mode a learned command still runs and the model is never asked`() = runTest {
        driveUnknown()
        pinErase()

        val outcome = learnedRoute(localOnly = true).route("сотри Vemblic")

        assertEquals(
            CommandOutcome.AgentSessionStarted(AgentSessionId("s1"), learned = CommandForm.parse("сотри …")),
            outcome,
        )
        assertEquals("the model is never asked", 0, planner.planCallCount)
        assertEquals(erasesVemblic, agentStore.saved.single().plan.steps.single().invocation)
    }

    /**
     * B-7: a replay that owes a pick, or whose step sends the MODEL's text off the phone, is shown on the card
     * first — carrying its mark (`learned`) and teaching nothing new (`learnable` null: it is not a fresh reading).
     * Nothing starts, and the model is not asked.
     *
     * The model-text half is pinned from «поищи что-нибудь про орвелин» → `web_search(query = орвелин история)`:
     * the query is not the user's words, so the template keeps it as a constant and the form is every word — the
     * same command, the fourth time, is what fits it.
     */
    @Test
    fun `a learned command that owes a pick or shows model text gets the card`() = runTest {
        driveUnknown()
        pinErase()
        learnedStore.pin("поищи что-нибудь про орвелин", modelText(LEARNED_WEB_SEARCH, "query" to "орвелин история"))
        installedByName["Vemblic"] = listOf(vemblic, vemblicTwo)
        val route = learnedRoute()

        assertEquals(
            CommandOutcome.Interpreted(
                Interpretation.Act(
                    goal = freeGoal("сотри Vemblic"),
                    steps = listOf(
                        PlanStep(
                            index = 0,
                            invocation = ToolInvocation(LEARNED_UNINSTALL, mapOf("app_label" to ArgSource.Literal("Vemblic"))),
                            risk = ActionRiskLevel.CONFIRM,
                            precondition = StepPrecondition.None,
                            rationale = StepRationale.MODEL_PROPOSED,
                        ),
                    ),
                    origins = mapOf(0 to mapOf("app_label" to ValueOrigin.SaidBy(PrincipalId.USER))),
                    effects = mapOf(0 to ToolEffect.EXTERNAL),
                    choices = listOf(PendingChoice(0, AppChoice("app", "Vemblic", listOf(vemblic, vemblicTwo), learning = null))),
                ),
                learnable = null,
                learned = CommandForm.parse("сотри …"),
            ),
            route.route("сотри Vemblic"),
        )

        assertEquals(
            CommandOutcome.Interpreted(
                Interpretation.Act(
                    goal = freeGoal("поищи что-нибудь про орвелин"),
                    steps = listOf(
                        PlanStep(
                            index = 0,
                            invocation = ToolInvocation(LEARNED_WEB_SEARCH, mapOf("query" to ArgSource.Literal("орвелин история"))),
                            risk = ActionRiskLevel.SAFE,
                            precondition = StepPrecondition.None,
                            rationale = StepRationale.MODEL_PROPOSED,
                        ),
                    ),
                    origins = mapOf(0 to mapOf("query" to ValueOrigin.ModelGenerated)),
                    effects = mapOf(0 to ToolEffect.EXTERNAL),
                    choices = emptyList(),
                ),
                learnable = null,
                learned = CommandForm.parse("поищи что нибудь про орвелин"),
            ),
            route.route("поищи что-нибудь про орвелин"),
        )
        assertEquals("the model is never asked", 0, planner.planCallCount)
        assertTrue("nothing starts before «Выполнить»", agentStore.saved.isEmpty())
    }

    /**
     * Review Focus 1, B-5: the words fit «сотри …» but name nothing the device can bind. The cache does not answer
     * «нет такого приложения» on understanding's behalf: it misses, and the command goes on down the chain exactly
     * as if nothing were learned — online the model is asked, local-only the honest local-only line. The replay
     * WAS tried both times (the store read, the binder asked) — the miss is the binding's, not a form that did not fit.
     */
    @Test
    fun `a learned command that does not bind goes on down the chain`() = runTest {
        driveUnknown()
        pinErase()

        val online = learnedRoute().route("сотри все фотографии")

        assertEquals("the model declined: said so, not «unknown»", CommandOutcome.Message(CommandMessage.UnderstoodNoAction), online)
        assertEquals("online, the model is asked once", 1, planner.planCallCount)
        assertEquals("сотри все фотографии", planner.lastCommand)

        val localOnly = learnedRoute(localOnly = true).route("сотри все фотографии")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), localOnly)
        assertEquals("local-only, the model is not asked", 1, planner.planCallCount)
        assertEquals("the replay was tried both times", 2, learnedStore.reads)
        assertEquals("and both times the binder found nothing", 2, binder.bindCallCount)
        assertTrue("nothing is started for a miss", agentStore.saved.isEmpty())
    }

    /**
     * B-8, the route half: a fresh model reading bound on the device carries what it would teach — computed in step
     * 7 from the VALIDATED steps (`ModelPlanning.Proposed.steps`, before binding: no package, the user's words a
     * slot) — and no mark (`learned`: it was not replayed). A reading with no word of its own (the command is all
     * slot) teaches nothing.
     *
     * **Only a reading with a card carries a lesson since the D3 second amendment** (task 4 of the tail plan; owner
     * answer I5): a reading that needs no card starts at once and teaches nothing (`a session started without a card
     * teaches nothing`). So both readings here owe the user a pick — two «Vemblic» apps — and the lesson rides on the
     * card; the property under test is unchanged.
     */
    @Test
    fun `a fresh model reading carries what it teaches, and one that cannot be learned carries nothing`() = runTest {
        driveUnknown()
        installedByName["Vemblic"] = listOf(vemblic, vemblicTwo)
        val route = learnedRoute()

        planner.answer = ModelPlanning.Proposed(listOf(said(LEARNED_UNINSTALL, "app" to "Vemblic")))
        val erased = route.route("сотри Vemblic") as CommandOutcome.Interpreted

        assertEquals(
            LearnedPlanShape(
                CommandForm.parse("сотри …"),
                PlanTemplate(listOf(TemplateStep(LEARNED_UNINSTALL, mapOf("app" to TemplateArg.Slot(0))))),
            ),
            erased.learnable,
        )
        assertNull("a fresh reading is not a replay", erased.learned)

        planner.answer = ModelPlanning.Proposed(listOf(said(LEARNED_UNINSTALL, "app" to "Vemblic")))
        val bare = route.route("Vemblic") as CommandOutcome.Interpreted

        assertNull("a form with no word of its own is not learned", bare.learnable)
        assertNull(bare.learned)
        assertEquals("both were fresh readings of the model", 2, planner.planCallCount)
    }

    /**
     * B-6: 2c runs only when FastPath was undecided AND 2b answered `NoPlan`. A pinned form that would fit each text
     * here is never read: FastPath's decision, 2b's plan and 2b's claimed target each answer before 2c — and none of
     * those outcomes carries a mark.
     */
    @Test
    fun `a text FastPath or 2b decided never consults the learned plans`() = runTest {
        pinErase()
        learnedStore.pin("удали приложение lumora", said(LEARNED_UNINSTALL, "app" to "lumora"))

        driveConfidentShowApps()
        val fastPath = learnedRoute().route("сотри Vemblic")

        driveUnknown()
        val planned = learnedRoute(agentPlanner = plansOneStep()).route("сотри Vemblic")

        val target = removeTarget("удали приложение Vemblic", words = "Vemblic", candidates = emptyList())
        val claimed = learnedRoute(agentPlanner = claims(target), agentRegistry = removeRegistry).route("удали приложение Vemblic")

        assertEquals(CommandOutcome.ShowApps, fastPath)
        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), planned)
        assertEquals(CommandOutcome.Message(CommandMessage.NoAppFound("Vemblic")), claimed)
        assertEquals("the learned plans were never read", 0, learnedStore.reads)
        // Controller ruling K2 (A4′ acceptance tail, task 2): online, 2b's empty app target goes to the model — once —
        // which declined here, so the device answered it (K11). The learned plans are still not read on that way.
        assertEquals("only 2b's empty app target is asked of the model (K2)", 1, planner.planCallCount)
        assertEquals("удали приложение Vemblic", planner.lastCommand)
    }

    // --- A4′ acceptance tail, owner decision 13: Б — a FastPath search yields to a reachable model (K1, K14) -------

    /** FastPath decides «найди лучший рецепт шашлыка» confidently: a web search, exactly what the real matcher claims. */
    private fun driveConfidentSearch() {
        matcher.intentToReturn = LauncherIntent.SearchIntent(SEARCH_QUERY, SearchTarget.WEB)
        matcher.confidenceToReturn = 0.90f
    }

    /** What FastPath runs for that search — the executor's one action when FastPath (or `runYielded`) searches. */
    private val fastPathSearch = ExecutableAction.OpenSearchAction(SEARCH_QUERY, SearchTarget.WEB)

    /**
     * The real bridge planner over a scripted transport, counted: [asked] is the model being consulted, and
     * `transport.requests` is every body that would leave the device. The one scripted reply is a valid `web_search`
     * reading, so a route that wrongly asked would get an answer — the assertions fail, not a missing script.
     */
    private class BridgeModel {
        val transport = FakeBridgeTransport(
            listOf(TransportReply.Answer(BridgeFixtures.plan("web_search", "query" to SEARCH_QUERY), ReplyMeta.NONE)),
        )
        private val bridge = BridgeModelPlanner(
            BridgeFixtures.registry,
            BridgeFixtures.descriptions,
            transport,
            BridgeFilter.ANDROID_OFFERED_LEVELS,
        )
        var asked = 0
            private set
        val planner = ModelPlanner { command ->
            asked++
            bridge.plan(command)
        }
    }

    /**
     * Decision 13: Б: online, with a provider, local-only off — the model is reachable, so FastPath's confident search
     * is not run in the browser: the model is asked, and its reading — the search in the user's words, no card owed —
     * starts at once through the engine (task 4 of the tail plan, the D3 second amendment), marked as the model's.
     * Nothing is executed by the route.
     */
    @Test
    fun `online with a provider a FastPath search goes to the model, not the browser`() = runTest {
        driveConfidentSearch()
        planner.answer = ModelPlanning.Proposed(
            listOf(
                ProposedStep(
                    tool = WEB_SEARCH,
                    args = mapOf("query" to SEARCH_QUERY),
                    origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    risk = ActionRiskLevel.SAFE,
                    effect = ToolEffect.EXTERNAL,
                ),
            ),
        )

        val outcome = useCase().route("найди $SEARCH_QUERY")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1"), proposedByModel = true), outcome)
        assertEquals(freeGoal("найди $SEARCH_QUERY"), agentStore.saved.single().goal)
        assertEquals(
            listOf(ToolInvocation(WEB_SEARCH, mapOf("query" to ArgSource.Literal(SEARCH_QUERY)))),
            agentStore.saved.single().plan.steps.map { it.invocation },
        )
        assertTrue("FastPath's search is not run in the browser", executor.executedActions.isEmpty())
        assertEquals("the model is asked once", 1, planner.planCallCount)
        assertEquals("найди $SEARCH_QUERY", planner.lastCommand)
        assertEquals("FastPath ran once", 1, matcher.callCount)
    }

    /** `DOC-ADL-3` parity: local-only — no yield, FastPath searches byte for byte; no model, no body leaves. */
    @Test
    fun `local-only a search runs on FastPath and the model is never asked`() = runTest {
        driveConfidentSearch()
        val model = BridgeModel()

        val outcome = useCase(localOnly = true, modelPlanner = model.planner).route("найди $SEARCH_QUERY")

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(listOf(fastPathSearch), executor.executedActions)
        assertEquals("the model is never asked", 0, model.asked)
        assertTrue("nothing is transmitted", model.transport.requests.isEmpty())
    }

    @Test
    fun `no provider a search runs on FastPath and the model is never asked`() = runTest {
        driveConfidentSearch()
        provider.clearActiveConfig()
        val model = BridgeModel()

        val outcome = useCase(modelPlanner = model.planner).route("найди $SEARCH_QUERY")

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(listOf(fastPathSearch), executor.executedActions)
        assertEquals("the model is never asked", 0, model.asked)
        assertTrue("nothing is transmitted", model.transport.requests.isEmpty())
    }

    @Test
    fun `offline a search runs on FastPath and the model is never asked`() = runTest {
        driveConfidentSearch()
        connectivity.online = false
        val model = BridgeModel()

        val outcome = useCase(modelPlanner = model.planner).route("найди $SEARCH_QUERY")

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(listOf(fastPathSearch), executor.executedActions)
        assertEquals("the model is never asked", 0, model.asked)
        assertTrue("nothing is transmitted", model.transport.requests.isEmpty())
    }

    /**
     * K1: the model was reachable at the gate and could not answer — a broken key, a timeout, an unreadable reply.
     * The user gets the search FastPath would have run, not «model unavailable» in place of a working search. Every
     * reason, none collapsed; each time FastPath ran once (the search is `runYielded`, not a second `handle`).
     */
    @Test
    fun `a yielded search whose model is unavailable runs the FastPath search`() = runTest {
        driveConfidentSearch()
        val route = useCase()

        ModelUnavailableReason.entries.forEachIndexed { i, reason ->
            planner.answer = ModelPlanning.Unavailable(reason)

            assertEquals("$reason", CommandOutcome.Executed, route.route("найди $SEARCH_QUERY"))
            assertEquals("$reason", List(i + 1) { fastPathSearch }, executor.executedActions)
            assertEquals("$reason: FastPath matched once per command", i + 1, matcher.callCount)
        }
        assertEquals("every reason was asked of the model", ModelUnavailableReason.entries.size, planner.planCallCount)
    }

    /** K1: the model declines — the search FastPath would have run, run once. */
    @Test
    fun `a yielded search the model declines runs the FastPath search`() = runTest {
        driveConfidentSearch()
        planner.answer = ModelPlanning.Declined

        val outcome = useCase().route("найди $SEARCH_QUERY")

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(listOf(fastPathSearch), executor.executedActions)
        assertEquals("the model is asked once", 1, planner.planCallCount)
        assertEquals("FastPath matched once", 1, matcher.callCount)
    }

    /**
     * Review round 1, Important 1 (controller ruling (a)): online, with a provider — the model reachable — a search
     * FastPath made out of an unsafe address (`UrlDetector`'s `SearchFallback`; here the Cyrillic «а» of a homograph)
     * is not yielded: FastPath searches it, and the model — scripted to send it to the assistant, so asking it would
     * change the outcome — is never asked.
     */
    @Test
    fun `online an ambiguous address is searched by FastPath and the model is never asked`() = runTest {
        matcher.intentToReturn = LauncherIntent.SearchIntent(HOMOGRAPH_ADDRESS, SearchTarget.WEB)
        matcher.confidenceToReturn = 0.90f
        planner.answer = ModelPlanning.ToAssistant("что это за адрес")

        val outcome = useCase().route(HOMOGRAPH_ADDRESS)

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(listOf(ExecutableAction.OpenSearchAction(HOMOGRAPH_ADDRESS, SearchTarget.WEB)), executor.executedActions)
        assertEquals("the model is never asked", 0, planner.planCallCount)
    }

    /** The model reads the «search» as a question: the assistant, prefilled and unsent — the browser does not open. */
    @Test
    fun `a yielded search the model sends to the assistant opens the assistant`() = runTest {
        driveConfidentSearch()
        planner.answer = ModelPlanning.ToAssistant("какой рецепт шашлыка лучший")

        val outcome = useCase().route("найди $SEARCH_QUERY")

        assertEquals(CommandOutcome.OpenAssistant("какой рецепт шашлыка лучший"), outcome)
        assertTrue("nothing is executed", executor.executedActions.isEmpty())
        assertEquals(1, planner.planCallCount)
    }

    // --- A4′ acceptance tail, task 2 — owner decision 14 + amendment D1 (K1, K2, K3, K11) ---------------------
    // Online, with a provider, local-only off, «нет такого приложения» is asked of the model first, which may
    // correct the name («открой клод» → «Claude»); the store — only if the model did not find it either. «клод» is
    // installed nowhere here, and every model word is compared by equality.

    /**
     * The model's launch of an app it NAMED itself: its words are not the user's (`BridgeValidator`, amendment D1).
     * `EXTERNAL`, as production's `launch_app` is (`SystemIntentToolSource`: every projected tool reaches another app).
     */
    private fun modelLaunch(app: String) = ProposedStep(
        tool = ToolIds.LAUNCH_APP,
        args = mapOf("app" to app),
        origins = mapOf("app" to ValueOrigin.ModelGenerated),
        risk = ActionRiskLevel.SAFE,
        effect = ToolEffect.EXTERNAL,
    )

    /** The model's removal, through [removeAppTool], of an app it named itself. */
    private fun modelRemoval(app: String) = ProposedStep(
        tool = removeAppTool.id,
        args = mapOf("app" to app),
        origins = mapOf("app" to ValueOrigin.ModelGenerated),
        risk = ActionRiskLevel.CONFIRM,
        effect = ToolEffect.EXTERNAL,
    )

    /** A0's store plan for «открой клод», in the USER's words: the launch, then the store search bound to its result. */
    private val storePlanForKlod = listOf(
        ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("клод"))),
        ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
    )
    private val storeGoalForKlod = AgentGoal("открой клод", GoalShape.AppNotInstalled("клод"))

    /** The device's binding of the model's «Claude» to the one installed app labelled exactly so. */
    private val boundClaude = Interpretation.Act(
        goal = freeGoal("открой клод"),
        steps = listOf(
            PlanStep(
                index = 0,
                invocation = ToolInvocation(
                    ToolIds.LAUNCH_APP,
                    mapOf("query" to ArgSource.Literal("Claude"), "app" to ArgSource.Literal("net.fixture.qandril")),
                ),
                risk = ActionRiskLevel.SAFE,
                precondition = StepPrecondition.None,
                rationale = StepRationale.MODEL_PROPOSED,
            ),
        ),
        origins = mapOf(0 to mapOf("query" to ValueOrigin.ModelGenerated, "app" to ValueOrigin.Ours)),
        effects = mapOf(0 to ToolEffect.EXTERNAL),
        choices = emptyList(),
        // Controller ruling T2-R2 (task 4): the binder marks the model's «Claude» as the words naming the step's app.
        appWords = mapOf(0 to "query"),
    )

    /**
     * Decision 14: online, FastPath's «нет такого приложения» is handed to the model (`FastPathYield.MissingApp`),
     * the model writes the name it believes is meant, and the DEVICE binds it — here to the one app labelled
     * exactly so. A0's store plan does not start.
     *
     * **Controller ruling T2-R2 (task 4), the owner's C1 «исправление — сразу запуск»:** the reading starts at once,
     * marked as the model's. `launch_app` is `EXTERNAL` and its words («Claude») are the MODEL's, but they are the
     * words naming the step's app ([Interpretation.Act.appWords]) — what reaches the world is the package the device
     * bound, never the words — so `needsCard()` does not count them. Nothing is executed by the route.
     */
    @Test
    fun `online a missing app goes to the model, binds on the device and starts at once`() = runTest {
        driveAppLaunch("клод")
        appsRepo.appsToReturn = emptyList()
        planner.answer = ModelPlanning.Proposed(listOf(modelLaunch("Claude")))
        binder.binding = { goal, steps ->
            assertEquals("the binder gets the model's word, in the model's name", listOf(modelLaunch("Claude")), steps)
            Binding.Bound(boundClaude.copy(goal = goal))
        }

        val outcome = useCase().route("открой клод")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1"), proposedByModel = true), outcome)
        assertEquals("the store plan did not start: the session is the model's reading", freeGoal("открой клод"), agentStore.saved.single().goal)
        assertEquals(boundClaude.steps, agentStore.saved.single().plan.steps)
        assertEquals("the model is asked once", 1, planner.planCallCount)
        assertEquals("открой клод", planner.lastCommand)
        assertEquals(1, binder.bindCallCount)
        assertTrue("nothing is executed", executor.executedActions.isEmpty())
    }

    /**
     * Controller ruling K3: the model named an app that is not on this phone (`Binding.NotFound`). The user gets
     * A0's store plan — in THEIR words («клод»), never the model's («Claudex»): the model's word does not go to
     * the store (D1, egress). Compared whole: the goal and every saved invocation.
     */
    @Test
    fun `online the model names an app that is not here - the store plan, in the user's words`() = runTest {
        driveAppLaunch("клод")
        appsRepo.appsToReturn = emptyList()
        planner.answer = ModelPlanning.Proposed(listOf(modelLaunch("Claudex")))
        binder.binding = { _, _ -> Binding.NotFound("Claudex") }

        val outcome = useCase().route("открой клод")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
        assertEquals(storeGoalForKlod, agentStore.saved.single().goal)
        assertEquals(storePlanForKlod, agentStore.saved.single().plan.steps.map { it.invocation })
        assertEquals("the model is asked once", 1, planner.planCallCount)
    }

    /** K1: the model declines — the store plan FastPath's «нет такого приложения» would have started, as before. */
    @Test
    fun `online a missing app the model declines - the store plan`() = runTest {
        driveAppLaunch("клод")
        appsRepo.appsToReturn = emptyList()
        planner.answer = ModelPlanning.Declined

        val outcome = useCase().route("открой клод")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
        assertEquals(storeGoalForKlod, agentStore.saved.single().goal)
        assertEquals(storePlanForKlod, agentStore.saved.single().plan.steps.map { it.invocation })
        assertEquals("the model is asked once", 1, planner.planCallCount)
        assertEquals("открой клод", planner.lastCommand)
    }

    /**
     * K1: the model was reachable at the gate and could not answer — a broken key, a timeout, an unreadable reply.
     * The user gets the store plan, not «model unavailable» in place of it. Every reason, none collapsed.
     */
    @Test
    fun `online a missing app with the model unavailable - the store plan`() = runTest {
        driveAppLaunch("клод")
        appsRepo.appsToReturn = emptyList()
        val route = useCase()

        ModelUnavailableReason.entries.forEachIndexed { i, reason ->
            planner.answer = ModelPlanning.Unavailable(reason)

            assertEquals("$reason", CommandOutcome.AgentSessionStarted(AgentSessionId("s${i + 1}")), route.route("открой клод"))
            assertEquals("$reason", storeGoalForKlod, agentStore.saved.last().goal)
            assertEquals("$reason", storePlanForKlod, agentStore.saved.last().plan.steps.map { it.invocation })
        }
        assertEquals("every reason was asked of the model", ModelUnavailableReason.entries.size, planner.planCallCount)
    }

    /**
     * The other states, one at a time (`the agent runs offline and with no provider configured…` above holds both at
     * once): with no provider, and offline, the model is not reachable — no yield, and «нет такого приложения» is
     * A0's store plan at step (2), byte for byte; the model is never asked.
     */
    @Test
    fun `with no provider or offline a missing app is the store plan and the model is never asked`() = runTest {
        driveAppLaunch("клод")
        appsRepo.appsToReturn = emptyList()
        planner.answer = ModelPlanning.Proposed(listOf(modelLaunch("Claude")))

        connectivity.online = false
        val offline = useCase().route("открой клод")
        connectivity.online = true
        provider.clearActiveConfig()
        val noProvider = useCase().route("открой клод")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), offline)
        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s2")), noProvider)
        assertEquals(listOf(storeGoalForKlod, storeGoalForKlod), agentStore.saved.map { it.goal })
        assertEquals("the model is never asked", 0, planner.planCallCount)
    }

    /**
     * Controller ruling K2: step 2b claimed «удали приложение клод» for a tool, and its APP target — readable — has
     * no candidate at all. Online, with a provider, that is the same class as decision 14's «нет такого
     * приложения»: the command goes on to the model, which may correct the name. It partly reverses fork 3b-E
     * (R14-39 «never step 7») — online only, and for an empty app target only; local-only is held by `in
     * local-only mode an unresolved 2b target is still answered on the device`.
     */
    @Test
    fun `online an unresolved 2b app with no candidates goes to the model`() = runTest {
        driveUnknown()
        val target = removeTarget("удали приложение клод", words = "клод", candidates = emptyList())
        planner.answer = ModelPlanning.Proposed(listOf(modelRemoval("Claude")))

        val outcome = useCase(agentPlanner = claims(target), agentRegistry = removeRegistry).route("удали приложение клод")

        assertEquals(
            CommandOutcome.Interpreted(
                Interpretation.Act(
                    goal = freeGoal("удали приложение клод"),
                    steps = listOf(
                        PlanStep(
                            index = 0,
                            invocation = ToolInvocation(removeAppTool.id, mapOf("app" to ArgSource.Literal("Claude"))),
                            risk = ActionRiskLevel.CONFIRM,
                            precondition = StepPrecondition.None,
                            rationale = StepRationale.MODEL_PROPOSED,
                        ),
                    ),
                    origins = mapOf(0 to mapOf("app" to ValueOrigin.ModelGenerated)),
                    effects = mapOf(0 to ToolEffect.EXTERNAL),
                    choices = emptyList(),
                ),
            ),
            outcome,
        )
        assertEquals("the model is asked once", 1, planner.planCallCount)
        assertEquals("удали приложение клод", planner.lastCommand)
        assertTrue("nothing starts before «Выполнить»", agentStore.saved.isEmpty())
    }

    /**
     * Controller ruling K11: the 2b target K2 handed to the model, and the model declined, could not answer (every
     * reason), or named an app that is not here — the device answers as it did before K2: «нет такого приложения»
     * in the USER's word («клод»), never the model's («Claudex»), and nothing is persisted.
     */
    @Test
    fun `online an unresolved 2b app the model declines, cannot answer or names something absent - no such app in the user's words`() = runTest {
        driveUnknown()
        val target = removeTarget("удали приложение клод", words = "клод", candidates = emptyList())
        val route = useCase(agentPlanner = claims(target), agentRegistry = removeRegistry)
        binder.binding = { _, _ -> Binding.NotFound("Claudex") }
        val answers = listOf(ModelPlanning.Declined) +
            ModelUnavailableReason.entries.map { ModelPlanning.Unavailable(it) } +
            ModelPlanning.Proposed(listOf(modelRemoval("Claudex")))

        answers.forEach { answer ->
            planner.answer = answer

            assertEquals("$answer", CommandOutcome.Message(CommandMessage.NoAppFound("клод")), route.route("удали приложение клод"))
        }
        assertEquals("each was asked of the model once", answers.size, planner.planCallCount)
        assertEquals("only the model's answer that named an app was bound", 1, binder.bindCallCount)
        assertTrue("nothing is persisted for a target nobody found", agentStore.saved.isEmpty())
    }

    /**
     * K2 is for an EMPTY app target only. A target with candidates is the user's pick on the device, as before —
     * the model, scripted to answer with a step so that asking it would change the outcome, is never asked.
     */
    @Test
    fun `online an unresolved 2b target WITH candidates is still a pick on the device`() = runTest {
        driveUnknown()
        val target = removeTarget("удали приложение tessar", words = "tessar", candidates = listOf(tessarOne, tessarTwo))
        planner.answer = ModelPlanning.Proposed(listOf(modelRemoval("Tessar")))

        val outcome = useCase(agentPlanner = claims(target), agentRegistry = removeRegistry).route("удали приложение tessar")

        assertEquals(CommandOutcome.ChooseTarget(target), outcome)
        assertEquals("the model is never asked", 0, planner.planCallCount)
        assertTrue("nothing starts before the pick", agentStore.saved.isEmpty())
    }

    /**
     * Review round 1, I1 (controller ruling (a)): a reading the model gave for 2b's empty app target (K2) teaches
     * nothing — step 2c, where a lesson is replayed, is not on that target's way: online K2 passes 2c by (2c is 2b's
     * `NoPlan` arm), and without the model 2b answers the target on the device. Three confirmations would pin a
     * «learned command» that never runs. The control: the same reading for a text nothing claimed DOES carry a lesson,
     * so the `null` is the route's refusal, not a reading that could teach nothing anyway.
     */
    @Test
    fun `a reading reached by K2 teaches nothing`() = runTest {
        driveUnknown()
        val target = removeTarget("удали приложение клод", words = "клод", candidates = emptyList())
        planner.answer = ModelPlanning.Proposed(listOf(modelText(LEARNED_UNINSTALL, "app" to "Vemblic")))
        // Task 4 (D3, second amendment): a reading with no card owed starts at once and teaches nothing anyway (I5), so
        // the readings here owe a pick — two «Vemblic» apps — to keep them on the card, where a lesson could ride.
        installedByName["Vemblic"] = listOf(vemblic, vemblicTwo)

        val claimed = learnedRoute(agentPlanner = claims(target), agentRegistry = removeRegistry).route("удали приложение клод")
        val unclaimed = learnedRoute().route("удали приложение клод")

        assertEquals("the model was asked for the claimed target", "удали приложение клод", planner.lastCommand)
        assertNull("a reading reached by K2 teaches nothing", (claimed as CommandOutcome.Interpreted).learnable)
        assertTrue("the control: the same reading, unclaimed, would teach", (unclaimed as CommandOutcome.Interpreted).learnable != null)
        assertEquals(2, planner.planCallCount)
    }

    /**
     * Review round 1, I1 (controller ruling (a)): a reading the model gave after FastPath YIELDED «нет такого
     * приложения» teaches nothing — without the model the same command is FastPath's decided `NoAppFound`, which
     * step (2) answers with the store plan before 2c is reached. The control, as above: the same reading for a text
     * FastPath did not decide carries a lesson.
     */
    @Test
    fun `a reading after a missing-app yield teaches nothing`() = runTest {
        driveAppLaunch("клод")
        appsRepo.appsToReturn = emptyList()
        // Task 4 (D3, second amendment): two «Claude» apps owe a pick, so the readings stay on the card (see above).
        installedByName["Claude"] = listOf(InstalledApp("net.fixture.qandril", "Claude"), InstalledApp("net.fixture.qandril.two", "Claude"))
        planner.answer = ModelPlanning.Proposed(listOf(modelLaunch("Claude")))

        val yielded = learnedRoute().route("открой клод")
        driveUnknown()
        val undecided = learnedRoute().route("открой клод")

        assertNull("a reading after a missing-app yield teaches nothing", (yielded as CommandOutcome.Interpreted).learnable)
        assertTrue("the control: the same reading, FastPath undecided, would teach", (undecided as CommandOutcome.Interpreted).learnable != null)
        assertTrue("the store plan did not start", agentStore.saved.isEmpty())
        assertEquals(2, planner.planCallCount)
    }

    /** K2 is online-with-a-provider only: with no provider, and offline, the empty 2b target is answered on the device. */
    @Test
    fun `with no provider or offline an unresolved 2b app is still answered on the device`() = runTest {
        driveUnknown()
        val target = removeTarget("удали приложение клод", words = "клод", candidates = emptyList())
        planner.answer = ModelPlanning.Proposed(listOf(modelRemoval("Claude")))

        connectivity.online = false
        val offline = useCase(agentPlanner = claims(target), agentRegistry = removeRegistry).route("удали приложение клод")
        connectivity.online = true
        provider.clearActiveConfig()
        val noProvider = useCase(agentPlanner = claims(target), agentRegistry = removeRegistry).route("удали приложение клод")

        assertEquals(CommandOutcome.Message(CommandMessage.NoAppFound("клод")), offline)
        assertEquals(CommandOutcome.Message(CommandMessage.NoAppFound("клод")), noProvider)
        assertEquals("the model is never asked", 0, planner.planCallCount)
        assertTrue(agentStore.saved.isEmpty())
    }

    // --- A4′ acceptance tail, task 3 — owner decision 3, K10: an alias before the model, before a label -----------
    // The phone, 2026-11-21: the bare «tg» / «телега» — the aliases themselves — were `UNKNOWN` and went on to the
    // model; the alias decorator (`ResolveCommandWithAliasUseCase`) answers only an `Unknown` the route no longer
    // returns. Step 1a reads the alias on the device, only for a command FastPath did not decide, above 2b.

    private val telegramPlus = InstalledApp("org.telegram.plus", "Plus Messenger", activityName = ".Main")

    private suspend fun aliased(vararg phrases: Pair<String, String>): FakeAliasStore = FakeAliasStore().apply {
        phrases.forEach { (phrase, pkg) -> upsert(Alias(phrase, AliasTarget.App(pkg), createdAtEpochMs = 0L)) }
    }

    /** What step 1a starts for [words]: one `launch_app` pinned to the alias's package, the user's words beside it. */
    private fun aliasLaunch(words: String, pkg: String) = ExecutionPlan(
        listOf(
            PlanStep(
                index = 0,
                invocation = ToolInvocation(
                    ToolIds.LAUNCH_APP,
                    mapOf("query" to ArgSource.Literal(words), "app" to ArgSource.Literal(pkg)),
                ),
                risk = ActionRiskLevel.SAFE,
                precondition = StepPrecondition.None,
                rationale = StepRationale.GOAL_DIRECT,
            ),
        ),
    )

    /**
     * Decision 3 (the owner accepted aliases in local-only): the bare alias «tg» starts its launch through the engine
     * — online, where step 7 WOULD ask the model (scripted to answer, so asking it would change the outcome), and in
     * local-only. The model is never asked; the plan is the one launch of the alias's package.
     */
    @Test
    fun `a bare alias starts its launch on the device and the model is never asked`() = runTest {
        driveUnknown()
        appsRepo.appsToReturn = listOf(telegramPlus)
        val aliases = aliased("tg" to "org.telegram.plus")
        planner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

        val online = useCase(aliases = aliases).route("tg")
        val localOnly = useCase(localOnly = true, aliases = aliases).route(" tg ")

        assertEquals("the model is never asked", 0, planner.planCallCount)
        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), online)
        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s2")), localOnly)
        assertEquals(listOf(freeGoal("tg"), freeGoal("tg")), agentStore.saved.map { it.goal })
        assertEquals(listOf(aliasLaunch("tg", "org.telegram.plus"), aliasLaunch("tg", "org.telegram.plus")), agentStore.saved.map { it.plan })
        assertTrue("nothing is executed by the route", executor.executedActions.isEmpty())
    }

    /**
     * The alias row outlives its app (review round 1, Important 1): «tg» still names `org.telegram.plus`, which is no
     * longer installed — only `org.telegram.messenger` is. Step 1a answers `null`, so the chain runs exactly as if there
     * were no alias: online the model is asked (scripted to answer, and its reading is what comes back), no plan of
     * step 1a is started; in local-only the honest local-only line. Never a launch of a package that is not here.
     */
    @Test
    fun `a bare alias whose app is gone goes on as if there were no alias`() = runTest {
        driveUnknown()
        appsRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        val aliases = aliased("tg" to "org.telegram.plus")
        planner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

        val online = useCase(aliases = aliases).route("tg")
        val localOnly = useCase(localOnly = true, aliases = aliases).route("tg")

        assertTrue("no plan of step 1a is started: ${agentStore.saved}", agentStore.saved.isEmpty())
        assertEquals("online the model is asked, as for any undecided text", 1, planner.planCallCount)
        assertEquals(CommandOutcome.Interpreted(Interpretation.Navigate(SidrDestination.APP_GRID)), online)
        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), localOnly)
    }

    /**
     * Step 1a reads only a command FastPath did NOT decide: «помощь» is FastPath's `Help`, answered byte for byte,
     * though the user also made «помощь» an alias. A decided outcome is never shadowed.
     */
    @Test
    fun `a bare alias never shadows a decided FastPath outcome`() = runTest {
        matcher.intentToReturn = LauncherIntent.SimpleCommandIntent(SimpleCommand.HELP)
        matcher.confidenceToReturn = 0.99f
        appsRepo.appsToReturn = listOf(telegramPlus)

        val outcome = useCase(aliases = aliased("помощь" to "org.telegram.plus")).route("помощь")

        assertTrue("no session is started for a decided outcome", agentStore.saved.isEmpty())
        assertEquals(CommandOutcome.Message(CommandMessage.Help), outcome)
    }

    /**
     * K10: the alias is the user's explicit memory, so it is read ABOVE step 2b — «камера» as an alias opens its app,
     * and the tool 2b would have planned for the same word (`open_camera`) is never planned: the 2b planner is never
     * consulted.
     */
    @Test
    fun `a bare alias shadows a 2b tool trigger`() = runTest {
        driveUnknown()
        val lumora = InstalledApp("net.fixture.lumora", "Lumora")
        appsRepo.appsToReturn = listOf(lumora)
        val openCamera = ToolDescriptor(
            id = ToolId("open_camera"),
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        )
        var consulted = 0
        val camera2b = object : Planner {
            override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
                consulted++
                return PlanningResult.Planned(
                    ExecutionPlan(
                        listOf(
                            PlanStep(
                                index = 0,
                                invocation = ToolInvocation(openCamera.id, emptyMap()),
                                risk = ActionRiskLevel.SAFE,
                                precondition = StepPrecondition.None,
                                rationale = StepRationale.GOAL_DIRECT,
                            ),
                        ),
                    ),
                )
            }
        }
        val registry = FakeToolRegistry(FakeToolRegistry.withA0Tools().all() + openCamera)

        val outcome = useCase(agentPlanner = camera2b, agentRegistry = registry, aliases = aliased("камера" to lumora.packageName))
            .route("камера")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
        assertEquals(listOf(aliasLaunch("камера", lumora.packageName)), agentStore.saved.map { it.plan })
        assertEquals("the 2b planner is never consulted", 0, consulted)
        assertEquals(0, planner.planCallCount)
    }

    /**
     * Principle 2 («whatever can run on the device runs there»): the words of an alias are answered on the phone, so
     * they never reach the model. The real bridge planner over a counted transport, online, with a provider: not one
     * body is sent for «tg».
     */
    @Test
    fun `the words of an alias never reach the model`() = runTest {
        driveUnknown()
        appsRepo.appsToReturn = listOf(telegramPlus)
        val model = BridgeModel()

        val outcome = useCase(modelPlanner = model.planner, aliases = aliased("tg" to "org.telegram.plus")).route("tg")

        assertTrue("nothing is transmitted: ${model.transport.requests}", model.transport.requests.isEmpty())
        assertEquals("the model is never asked", 0, model.asked)
        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1")), outcome)
    }

    /**
     * Decision 14 meets an alias: «открой tg» resolves the alias in FastPath's own launch (`IntentActionResolver`), so
     * there is no «нет такого приложения» to yield — FastPath launches the alias's app, online, and the model (scripted
     * to answer) is never asked; no store plan starts.
     */
    @Test
    fun `online open with an alias launches it and the model is never asked`() = runTest {
        driveAppLaunch("tg")
        appsRepo.appsToReturn = listOf(telegramPlus)
        planner.answer = ModelPlanning.Proposed(listOf(modelLaunch("Telegram")))

        val outcome = useCase(aliases = aliased("tg" to "org.telegram.plus")).route("открой tg")

        assertEquals("the model is never asked", 0, planner.planCallCount)
        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(listOf(ExecutableAction.LaunchAppAction("org.telegram.plus", ".Main")), executor.executedActions)
        assertTrue("no store plan starts", agentStore.saved.isEmpty())
    }

    /**
     * A command FastPath decided and YIELDED (decision 13: Б) is not undecided for step 1a: without the model the same
     * command is FastPath's decided search, which 1a never reads, so online it is not read either. Here the user's
     * alias table holds the whole command «найди tg» as a phrase — the one way 1a could ever match a yielded command,
     * since it compares the WHOLE command with a phrase — and still the model gets the search, and on its refusal
     * FastPath searches (K1). No session starts.
     */
    @Test
    fun `a yielded search never becomes a bare alias`() = runTest {
        matcher.intentToReturn = LauncherIntent.SearchIntent("tg", SearchTarget.WEB)
        matcher.confidenceToReturn = 0.90f
        appsRepo.appsToReturn = listOf(telegramPlus)
        planner.answer = ModelPlanning.Declined

        val outcome = useCase(aliases = aliased("найди tg" to "org.telegram.plus", "tg" to "org.telegram.plus")).route("найди tg")

        assertTrue("no alias session starts for a yielded command", agentStore.saved.isEmpty())
        assertEquals("the yielded search is the model's", 1, planner.planCallCount)
        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(listOf(ExecutableAction.OpenSearchAction("tg", SearchTarget.WEB)), executor.executedActions)
    }

    private companion object {
        val LEARNED_UNINSTALL = ToolId("uninstall_app")
        val LEARNED_WEB_SEARCH = ToolId("web_search")
        val WEB_SEARCH = ToolId("web_search")
        const val SEARCH_QUERY = "лучший рецепт шашлыка"

        /** «apple.com» with a Cyrillic «а» (U+0430): a non-ASCII host, which `UrlDetector` routes to a search. */
        const val HOMOGRAPH_ADDRESS = "https://\u0430pple.com"
    }
}
