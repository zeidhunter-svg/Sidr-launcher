package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.core.testing.FakeModelPlanner
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.toPlanStep
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The D3 second amendment's floor, held by the ENGINE, not the router** (A4′ acceptance tail, task 4; plan review
 * I4; Review Focus 2). A fresh model reading that owes no pick and sends no model text out ([com.sidr.launcher.domain.memory.plan.needsCard]
 * false) starts its session with no interpretation card — so the one thing standing between a risky step the model
 * proposed and the world is the engine's consent checkpoint (`requiresConsent(risk)`, `DOC-ADL-1`). Each case here
 * walks the whole way the launcher walks: the route → the session it saved → [RunAgentSessionUseCase] over the real
 * [AgentExecutor] and a [FakeToolExecutor] that would answer `Effected` — and ends `AwaitingConsent` with the tool
 * never invoked. A route test alone could not show this: «started» is not «ran».
 *
 * Two risky shapes: a removal shaped like `uninstall_app` (`CONFIRM`, `DURABLE`) — the review focus — and `open_url`
 * (`CONFIRM`) carrying a homograph host, the task-1 residual («найди https://аpple.com» reaches the model, and its
 * `open_url` must stop at the consent card that names the host). Same build as `AgentEgressSentinelGuardTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelReadingConsentTest {

    private val matcher = FakeIntentMatcher().apply {
        intentToReturn = LauncherIntent.UnknownIntent(originalInput = "x", reason = "x")
        confidenceToReturn = 0.0f
    }
    private val modelPlanner = FakeModelPlanner()
    private val binder = FakeMentionBinder()
    private val agentStore = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    /** Shaped like `uninstall_app`: a required package, the user's words beside it, `CONFIRM`, `DURABLE`. Test-local. */
    private val removeApp = ToolDescriptor(
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
    private val registry = FakeToolRegistry(FakeToolRegistry.withA0Tools().all() + removeApp)

    private fun route(): RouteCommandUseCase = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = matcher,
            resolver = IntentActionResolver(FakeInstalledAppsRepository()),
            executor = FakeActionExecutor(),
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(SupervisorJob()),
        ),
        modelPlanner = modelPlanner,
        binder = binder,
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = false)),
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
        startAgentSession = StartAgentSessionUseCase(TemplatePlanner(), agentStore, ids, registry),
    )

    /** Routes [command], asserts it started at once as the model's, then runs the saved session through the engine. */
    private suspend fun routeAndRun(command: String, tools: FakeToolExecutor): AgentSession {
        val outcome = route().route(command)
        assertEquals(
            "the reading needs no card, so it starts at once",
            CommandOutcome.AgentSessionStarted(AgentSessionId("s1"), proposedByModel = true),
            outcome,
        )
        val run = RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), agentStore)
        return checkNotNull((run.run(agentStore.active) as OperationResult.Success).value)
    }

    @Test
    fun `a model reading with a risky step starts and stops at the engine's consent`() = runTest {
        val proposed = ProposedStep(
            tool = removeApp.id,
            args = mapOf("app" to "Vemblic"),
            origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
            risk = ActionRiskLevel.CONFIRM,
            effect = ToolEffect.EXTERNAL,
        )
        modelPlanner.answer = ModelPlanning.Proposed(listOf(proposed))
        // The device's binding: the package into `app`, the user's words beside it — nothing for a card to show.
        binder.binding = { goal, _ ->
            Binding.Bound(
                Interpretation.Act(
                    goal = goal,
                    steps = listOf(proposed.toPlanStep(0, mapOf("app" to "net.fixture.vemblic", "app_label" to "Vemblic"))),
                    origins = mapOf(0 to mapOf("app" to ValueOrigin.Ours, "app_label" to ValueOrigin.SaidBy(PrincipalId.USER))),
                    effects = mapOf(0 to ToolEffect.EXTERNAL),
                    choices = emptyList(),
                ),
            )
        }
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val session = routeAndRun("сотри Vemblic", tools)

        assertEquals(ExecutionState.AwaitingConsent, session.state)
        assertEquals("nothing reached the world before consent", 0, tools.invocations.size)
        assertEquals(
            listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL, ConsentReason.DURABLE_EFFECT))),
            session.trace.events.filterIsInstance<TraceEvent.ConsentRequested>(),
        )
        assertEquals(
            mapOf("app" to ArgSource.Literal("net.fixture.vemblic"), "app_label" to ArgSource.Literal("Vemblic")),
            session.plan.steps.single().invocation.args,
        )
    }

    /**
     * The task-1 residual: a verb and an address whose host is a homograph («аpple.com», a Cyrillic «а») reaches the
     * model, which may read it back as `open_url` in the user's own words — no card owed. The session starts, and the
     * engine stops it at the consent card; the step it stops on carries the host exactly, for that card to name it.
     */
    @Test
    fun `a model reading that opens an address starts and stops at the engine's consent, the host on the step`() = runTest {
        val proposed = ProposedStep(
            tool = ToolId("open_url"),
            args = mapOf("url" to HOMOGRAPH_ADDRESS),
            origins = mapOf("url" to ValueOrigin.SaidBy(PrincipalId.USER)),
            risk = ActionRiskLevel.CONFIRM,
            effect = ToolEffect.EXTERNAL,
        )
        modelPlanner.answer = ModelPlanning.Proposed(listOf(proposed))
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val session = routeAndRun("найди $HOMOGRAPH_ADDRESS", tools)

        assertEquals(ExecutionState.AwaitingConsent, session.state)
        assertEquals("nothing reached the world before consent", 0, tools.invocations.size)
        assertEquals(
            listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL))),
            session.trace.events.filterIsInstance<TraceEvent.ConsentRequested>(),
        )
        assertEquals(mapOf("url" to ArgSource.Literal(HOMOGRAPH_ADDRESS)), session.plan.steps.single().invocation.args)
    }

    private companion object {
        /** «apple.com» with a Cyrillic «а» (U+0430). */
        const val HOMOGRAPH_ADDRESS = "https://аpple.com"
    }
}
