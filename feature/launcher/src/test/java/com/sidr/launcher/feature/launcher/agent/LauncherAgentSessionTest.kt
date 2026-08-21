package com.sidr.launcher.feature.launcher.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The surface is a function of state. These tests pin the two behaviours a user would notice and that
 * no unit test elsewhere covers: a session that survived process death is presented as `Paused` with an
 * offer, and confirming twice does not run the step twice.
 *
 * Everything here is a port, so no Android and no Compose is involved: the collaborator is driven over
 * `FakeAgentSessionStore` / `FakeToolExecutor` / `FakeToolRegistry` and the real domain use cases, and
 * the `StandardTestDispatcher` behind `runTest` means every `scope.launch` runs only when the test says
 * `advanceUntilIdle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherAgentSessionTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val store = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }
    private val goal = AgentGoal("открой убер", GoalShape.AppNotInstalled("убер"))

    /** `launch_app`'s missing-app result, carrying the `resolved_query` step 1 binds to (F6). */
    private fun notInstalled() = ToolResult.Observed(
        ObservedFact.APP_NOT_INSTALLED,
        ToolOutput(mapOf("resolved_query" to "убер")),
    )

    /**
     * Drives the real A0 plan to its consent checkpoint, then writes back the one shape a process
     * death actually leaves behind: `Running`, cursor 1, step 0's observation recorded, step 1's
     * consent unanswered. That is the row `restoreOnStart()` reads on the next launch.
     */
    private suspend fun seedRunningAtCursorOne(tools: FakeToolExecutor) {
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), store)
            .run(store.active)
        check(store.active.cursor == 1) { "seed expected cursor 1, was ${store.active.cursor}" }
        store.save(store.active.copy(state = ExecutionState.Running))
    }

    private fun collaborator(tools: FakeToolExecutor, scope: CoroutineScope): LauncherAgentSession {
        val runner = RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), store)
        return LauncherAgentSession(
            runSession = runner,
            resolveConsent = ResolveConsentUseCase(store, runner),
            cancelSession = CancelAgentSessionUseCase(store),
            store = store,
            scope = scope,
        )
    }

    @Test
    fun `a session found at startup is presented as Paused, never silently resumed`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        seedRunningAtCursorOne(tools)
        val invocationsAfterSeed = tools.invocations.size
        val agent = collaborator(tools, this)

        agent.restoreOnStart()
        advanceUntilIdle()

        assertEquals(ExecutionState.Paused, agent.session.value?.state)
        // Not merely presented as paused — persisted as paused, so a second death cannot resurrect a
        // Running row, and the trace records the pause rather than skipping silently over it.
        assertEquals(ExecutionState.Paused, store.active.state)
        assertEquals(TraceEvent.SessionPaused, store.active.trace.events.last())
        assertEquals(invocationsAfterSeed, tools.invocations.size)
    }

    @Test
    fun `continuing a paused session that owes consent returns to AwaitingConsent`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        seedRunningAtCursorOne(tools)
        val invocationsAfterSeed = tools.invocations.size
        val agent = collaborator(tools, this)

        agent.restoreOnStart()
        advanceUntilIdle()
        agent.continueSession()
        advanceUntilIdle()

        // Resuming re-evaluates rather than steps past: the CONFIRM-risk step 1 puts its consent
        // checkpoint back on screen, and the world is still untouched.
        assertEquals(ExecutionState.AwaitingConsent, agent.session.value?.state)
        assertEquals(invocationsAfterSeed, tools.invocations.size)
        assertTrue(
            "resume must be recorded in the trace",
            agent.session.value!!.trace.events.contains(TraceEvent.SessionResumed),
        )
    }

    @Test
    fun `confirming the same step twice invokes the tool once`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        seedRunningAtCursorOne(tools)
        val agent = collaborator(tools, this)
        agent.restoreOnStart()
        advanceUntilIdle()
        agent.continueSession()
        advanceUntilIdle()

        agent.confirm(stepIndex = 1)
        advanceUntilIdle()
        agent.confirm(stepIndex = 1)
        advanceUntilIdle()

        assertEquals(
            "the second tap must resolve to no row, not to a second invocation",
            1,
            tools.invocations.count { it.id == ToolIds.PLAY_STORE_SEARCH },
        )
        // The already-honoured tap keeps the surface it produced rather than blanking it.
        assertEquals(ExecutionState.Completed, agent.session.value?.state)
    }

    @Test
    fun `cancelling clears the surface and deletes the session`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        seedRunningAtCursorOne(tools)
        val agent = collaborator(tools, this)
        agent.restoreOnStart()
        advanceUntilIdle()

        agent.cancel()
        advanceUntilIdle()

        assertNull(agent.session.value)
        assertNull(store.activeOrNull)
    }
}
