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
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.SidrDestination
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A0's agent has no path off the device: the planner is deterministic and both tools are system
 * intents. This proves it rather than asserting it — a sentinel planted in the goal text must reach no
 * outbound channel from the session, in every state; and in the four local states it reaches none at all.
 *
 * A4′ phase 3a added the model planner (`ModelPlanner`, asked at `RouteCommandUseCase` step 7 only);
 * this test is where its outbound path is reconciled with the agent branch. **In the four local states**
 * (local-only on, online or offline; local-only off and offline; no provider) the branch returns before
 * step 7 and the seam is never asked. **Online, with a provider, local-only off** — since owner decision 14
 * (A4′ acceptance tail, task 2; ADR «2026-11-22 — D1, поправка: имя приложения, исправленное моделью») — the
 * missing app is asked of the model ONCE, with the command, and the session that follows sends nothing more:
 * one body in all. What the bridge sends when it IS asked is `BridgePayloadGuardTest`'s subject (`:app`), read
 * off the payload.
 *
 * **Which channel this watches, and why it is the only one.** The agent branch is entered from
 * [RouteCommandUseCase], and that use case takes exactly one dependency that can leave the device: a
 * [com.sidr.launcher.domain.ai.bridge.ModelPlanner] (its `MentionBinder` binds on the device, after the
 * model has answered, and sends nothing back). It takes **no** `GenerativeAiEngine` — the
 * assistant's transport is a different contour entirely — so wiring a `FakeGenerativeAiEngine` here
 * and asserting it recorded nothing would be a fake connected to no seam, which records zero calls no
 * matter what the code under test does. That is the definition of a vacuous assertion, and this test
 * deliberately does not make it. The *structural* half of the claim — that the engine cannot name
 * `GenerativeAiEngine`, `ModelPlanner`, `BridgeTransport`, Ktor or an `HttpClient` at all — is held mechanically by
 * `AgentVocabularyGuardTest` over `domain/agent` and `domain/tool`. Between them the two guards cover
 * both halves: nothing may be reachable, and what is reachable is never called.
 *
 * Each case runs the **whole** session, not just its start: route → plan → step 0 → consent → step 1 →
 * `Completed`. A guard that only checked the cut site would miss a model consulted mid-run.
 *
 * Two assertions keep it from passing for the wrong reason:
 *  - `the sentinel does reach the on-device tool boundary` — the sentinel really is carried through
 *    the session, so "it never left" is not "it was never there".
 *  - `the outbound seam is live when the agent branch is not taken` — the same fake planner, wired the
 *    same way, does receive the sentinel on the path where consulting a model is correct. So
 *    `planCallCount == 0` in the local states below (and `== 1` online) is a property of the route and the
 *    agent branch, not of a disconnected fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentEgressSentinelGuardTest {

    private val sentinel = "SIDR-EGRESS-SENTINEL-A0"

    private val matcher = FakeIntentMatcher()
    private val appsRepo = FakeInstalledAppsRepository()
    private val actionExecutor = FakeActionExecutor()
    private val connectivity = FakeConnectivityChecker(initiallyOnline = true)
    private val provider = configuredProvider()

    /** The one outbound seam [RouteCommandUseCase] has. Every assertion below is about this object. */
    private val modelPlanner = FakeModelPlanner()

    private val agentStore = FakeAgentSessionStore()
    private val registry = FakeToolRegistry.withA0Tools()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    /** `launch_app`'s missing-app result, carrying the `resolved_query` step 1 binds to (F6). */
    private fun notInstalled() = ToolResult.Observed(
        ObservedFact.APP_NOT_INSTALLED,
        ToolOutput(mapOf("resolved_query" to sentinel)),
    )

    private fun useCase(localOnly: Boolean): RouteCommandUseCase = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = matcher,
            resolver = IntentActionResolver(appsRepo),
            executor = actionExecutor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(SupervisorJob()),
        ),
        modelPlanner = modelPlanner,
        binder = FakeMentionBinder(),
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = localOnly)),
        providerConfigRepository = provider,
        connectivityChecker = connectivity,
        startAgentSession = StartAgentSessionUseCase(TemplatePlanner(), agentStore, ids, registry),
    )

    /** FastPath DECIDES "open <sentinel>" and, with no such app installed, cannot ACHIEVE it. */
    private fun driveSentinelLaunch() {
        matcher.intentToReturn = LauncherIntent.LaunchAppIntent(displayNameQuery = sentinel)
        matcher.confidenceToReturn = 0.99f
        appsRepo.appsToReturn = emptyList()
    }

    /** How many bodies the ROUTE had sent when the session started — what the run then adds is the difference. */
    private var bodiesAtRoute = -1

    /**
     * Route the sentinel command and then drive the whole session to a terminal state through the real
     * engine, granting the one consent the A0 plan asks for. Returns the tools that were called.
     */
    private suspend fun runFullSession(localOnly: Boolean): FakeToolExecutor {
        driveSentinelLaunch()

        val outcome = useCase(localOnly).route("открой $sentinel")
        assertTrue("expected an agent session, was $outcome", outcome is CommandOutcome.AgentSessionStarted)
        bodiesAtRoute = modelPlanner.planCallCount

        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val run = RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), agentStore)
        run.run(agentStore.active)

        val resolved = ResolveConsentUseCase(agentStore, run)
            .resolve((outcome as CommandOutcome.AgentSessionStarted).id, 1, granted = true)
        assertEquals(
            "the session must reach a terminal state, or the walk stopped before the later steps",
            ExecutionState.Completed,
            (resolved as OperationResult.Success).value?.state,
        )
        return tools
    }

    /**
     * Nothing in the fake's record may carry the sentinel, and the seam must never have been asked.
     *
     * There is deliberately no `lastCommand?.contains(sentinel) != true` follow-up here. It used to be
     * there and it could never fail: if the [assertNull] above passes then `lastCommand` is null,
     * `null?.contains(…)` is null, and `null != true` is true — an assertion reporting a safety it
     * never checked, in the file whose whole subject is that distinction. The live-seam control at the
     * bottom of this class makes the same-shaped check against a *non-null* payload, where it is the
     * load-bearing one.
     */
    private fun assertNothingLeft() {
        assertEquals("the model planner was consulted", 0, modelPlanner.planCallCount)
        assertNull("an outbound payload was recorded: ${modelPlanner.lastCommand}", modelPlanner.lastCommand)
    }

    /**
     * **Rewritten on purpose, not weakened** (A4′ acceptance tail, task 2 — owner decision 14, ADR «2026-11-22 —
     * D1, поправка: имя приложения, исправленное моделью»; plan review C2). Online, with a provider and local-only
     * off — the one state of the five in which the model is reachable — a recognised launch of an app that is not
     * installed is now asked of the model ONCE, with the command, before the store plan: the owner-accepted
     * outbound widening «в сети распознанный запуск отсутствующего приложения уходит модели». The fake declines,
     * so the store plan starts exactly as before (K1). What this test still holds, as hard as before: **the
     * session that follows sends nothing more** — one body for the whole session, the command, and not one ask
     * from the run, its consent or its later steps. The four local states below keep zero bodies.
     */
    @Test
    fun `local-only off and online - asks the model once and the session sends nothing more`() = runTest {
        connectivity.online = true
        runFullSession(localOnly = false)
        assertEquals("the route asks the model once, before the store plan", 1, bodiesAtRoute)
        assertEquals("one body for the whole session: the run, its consent and its steps send none", 1, modelPlanner.planCallCount)
        assertEquals("the one body is the command itself", "открой $sentinel", modelPlanner.lastCommand)
    }

    @Test
    fun `local-only off and offline - the whole session runs and nothing leaves the device`() = runTest {
        connectivity.online = false
        runFullSession(localOnly = false)
        assertNothingLeft()
    }

    @Test
    fun `local-only on and online - the whole session runs and nothing leaves the device`() = runTest {
        connectivity.online = true
        runFullSession(localOnly = true)
        assertNothingLeft()
    }

    @Test
    fun `local-only on and offline - the whole session runs and nothing leaves the device`() = runTest {
        connectivity.online = false
        runFullSession(localOnly = true)
        assertNothingLeft()
    }

    /**
     * The state a reader would suspect of being the loophole: no provider configured is the one gate
     * condition that sits *below* the agent branch in the chain, so the branch is taken with the
     * outbound path unconfigured. Nothing may leave here either — for the stronger reason that nothing
     * is asked at all.
     */
    @Test
    fun `no provider configured - the agent still runs and the seam is still never asked`() = runTest {
        provider.clearActiveConfig()
        connectivity.online = false
        runFullSession(localOnly = false)
        assertNothingLeft()
    }

    /**
     * Non-vacuity, half one. If the sentinel never made it into the session, every assertion above
     * would hold trivially. It does: it is the literal step 0 binds and the value step 1 re-binds from
     * step 0's output (F6). Both of those boundaries are on-device — a system intent — which is exactly
     * the distinction this guard exists to draw.
     */
    @Test
    fun `the sentinel does reach the on-device tool boundary`() = runTest {
        val tools = runFullSession(localOnly = true)

        assertEquals(2, tools.invocations.size)
        assertTrue(
            "step 0 must carry the sentinel as a bound argument: ${tools.invocations}",
            tools.invocations.all { it.args.values.any { value -> value.contains(sentinel) } },
        )
    }

    /**
     * Non-vacuity, half two — the assertion this guard would be worthless without. The same
     * [FakeModelPlanner] instance, constructed by the same [useCase] wiring, DOES record the sentinel
     * when FastPath decides nothing and a model is legitimately consulted. So a zero call count in the
     * four local-state tests above is a fact about the agent branch, not about a fake that was never
     * connected — and so is the online test's count of exactly one.
     */
    @Test
    fun `the outbound seam is live when the agent branch is not taken`() = runTest {
        matcher.intentToReturn = LauncherIntent.UnknownIntent(originalInput = sentinel, reason = "x")
        matcher.confidenceToReturn = 0.0f
        connectivity.online = true
        modelPlanner.answer = ModelPlanning.Navigate(SidrDestination.APP_GRID)

        useCase(localOnly = false).route("сделай $sentinel")

        assertEquals(1, modelPlanner.planCallCount)
        assertTrue(
            "the control case must show the sentinel reaching the seam, or it proves nothing",
            modelPlanner.lastCommand?.contains(sentinel) == true,
        )
        assertTrue("no session may be started for an undecided command", agentStore.saved.isEmpty())
    }
}
