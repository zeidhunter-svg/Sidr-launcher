package com.sidr.launcher.feature.launcher.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.StepUndo
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.agent.UndoAgentSessionUseCase
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    private val rerouted = mutableListOf<String>()

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

    private fun collaborator(
        tools: FakeToolExecutor,
        scope: CoroutineScope,
        registry: ToolRegistry = this.registry,
        inverses: ToolInverses = ToolInverses { _, _ -> Undo.NotUndoable },
    ): LauncherAgentSession {
        val runner = RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), store)
        return LauncherAgentSession(
            runSession = runner,
            resolveConsent = ResolveConsentUseCase(store, runner),
            cancelSession = CancelAgentSessionUseCase(store),
            store = store,
            scope = scope,
            resumeSession = ResumeAgentSessionUseCase(StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry), runner),
            undoSession = UndoAgentSessionUseCase(StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry), inverses, registry),
            onReroute = { rerouted += it },
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

    /**
     * The second restart is not a second pause. `pausedForRestore` records `SessionPaused`, and the
     * trace is 1:1 with reality (`DOC-ILM-3`) — a session that paused once carries one pause, not one
     * per process death. This launcher is the home screen, so the unguarded version grew the persisted
     * trace by a row on every start for as long as the plan sat unanswered.
     *
     * Found by probe: three restarts produced three `SessionPaused` events.
     */
    @Test
    fun `a second restart presents the same paused session without recording another pause`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        seedRunningAtCursorOne(tools)

        collaborator(tools, this).restoreOnStart()
        advanceUntilIdle()
        // The process dies again; a fresh collaborator reads the same row on the next start.
        val second = collaborator(tools, this)
        second.restoreOnStart()
        advanceUntilIdle()

        assertEquals(ExecutionState.Paused, second.session.value?.state)
        assertEquals(ExecutionState.Paused, store.active.state)
        assertEquals(
            "a session that paused once must carry exactly one SessionPaused",
            1,
            store.active.trace.events.count { it == TraceEvent.SessionPaused },
        )
    }

    /**
     * A terminal session on disk is finished, not interrupted. `RunAgentSessionUseCase.persist` saves
     * the terminal state and then deletes it; a death between those two leaves the row behind. Offering
     * to continue it says "you left before this plan finished" about a plan that did finish.
     *
     * Found by probe: an on-disk `Completed` was presented as `Paused`.
     */
    @Test
    fun `a terminal session left on disk is deleted, not offered as a paused plan`() = runTest {
        // One Effected step: step 0 succeeds, step 1's APP_NOT_INSTALLED precondition is unsatisfied
        // and it is skipped, so the session ends Completed.
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val runner = RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), store)
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        runner.run(store.active)
        // Put the terminal row back: this is the state the dead process left, delete not yet done.
        store.save(store.saved.last { it.state.isTerminal })
        check(store.active.state.isTerminal) { "seed expected a terminal row, was ${store.active.state}" }

        val agent = collaborator(tools, this)
        agent.restoreOnStart()
        advanceUntilIdle()

        assertNull("a finished plan is not an interrupted one", agent.session.value)
        assertNull("and the delete the dead process owed is completed", store.activeOrNull)
    }

    /**
     * A4′ phase 2 (owner fork F2-3.1). This test used to pin «resuming re-evaluates rather than steps
     * past, and the world is still untouched». Its seed is exactly A0's §12.8 stale shape — step 0 has
     * only LOOKED — and the owner decided such a plan looks again: a fresh session re-runs the launch
     * and, still finding nothing, returns to the store's consent gate. The world is touched once more,
     * on purpose; the gate is still there.
     */
    @Test
    fun `continuing a plan that has only looked looks at the world again before it asks`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), notInstalled()))
        seedRunningAtCursorOne(tools)
        val agent = collaborator(tools, this)

        agent.restoreOnStart()
        advanceUntilIdle()
        agent.continueSession()
        advanceUntilIdle()

        assertEquals(ExecutionState.AwaitingConsent, agent.session.value?.state)
        assertEquals(AgentSessionId("s2"), agent.session.value?.id)
        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
    }

    /** A0 acceptance §12.8, through the product's own path: the app appeared while the plan waited. */
    @Test
    fun `continuing a stale plan opens an app installed while it waited, and offers no store`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        seedRunningAtCursorOne(tools)
        val agent = collaborator(tools, this)

        agent.restoreOnStart()
        advanceUntilIdle()
        agent.continueSession()
        advanceUntilIdle()

        assertEquals(ExecutionState.Completed, agent.session.value?.state)
        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
    }

    /** Review Focus 1 (plan §0.3 P6): each tap would re-plan into a new id, so two taps = two looks. */
    @Test
    fun `a second Continue tap while the first is still running starts nothing`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), notInstalled(), notInstalled()))
        seedRunningAtCursorOne(tools)
        val agent = collaborator(tools, this)
        agent.restoreOnStart()
        advanceUntilIdle()

        agent.continueSession()
        agent.continueSession()
        advanceUntilIdle()

        assertEquals("two taps, one fresh look", 2, tools.invocations.size)
    }

    // A4′ phase 2: the resume re-plans (F2-3.1), so the fresh session's own look takes the second script entry.
    @Test
    fun `confirming the same step twice invokes the tool once`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), notInstalled(), ToolResult.Effected()))
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

    /**
     * **The seam this class shares with the engine, end to end** (review finding F1, 2026-08-23).
     *
     * A process death *during* a tool call leaves the one shape the split exists to record:
     * `ToolInvoked(0)` with no `ToolObserved(0)`. This drives the real product path over it —
     * [LauncherAgentSession.restoreOnStart], which appends `SessionPaused`, then
     * [LauncherAgentSession.continueSession], which appends `SessionResumed` — and requires the pending
     * call to be *resumed*, not re-issued.
     *
     * Nothing covered this before: the domain test simulates the restart by calling `advance` straight
     * on the prepared session, so both restore events were missing from every test that existed, and
     * the two events are precisely what used to hide the pending call from the engine.
     */
    @Test
    fun `continuing a session interrupted mid-tool-call runs the pending step exactly once`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, RuntimeBudget.Default)

        // The mid-call shape, written the way `RunAgentSessionUseCase` writes it: prepare, save, die.
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        store.save(executor.prepare(store.active))
        check(store.active.trace.events.last() is TraceEvent.ToolInvoked)
        check(tools.invocations.isEmpty()) { "the tool must not have run before the crash" }

        val agent = collaborator(tools, this)
        agent.restoreOnStart()
        advanceUntilIdle()
        assertEquals(ExecutionState.Paused, agent.session.value?.state)

        agent.continueSession()
        advanceUntilIdle()

        assertEquals(
            "the pending call must be performed once, not re-issued: ${tools.invocations}",
            1,
            tools.invocations.size,
        )
        val events = store.active.trace.events
        assertEquals(
            "one pending call is one ToolInvoked, however many times the app was restarted: $events",
            1,
            events.count { it is TraceEvent.ToolInvoked && it.index == 0 },
        )
        assertEquals(1, events.count { it is TraceEvent.StepStarted && it.index == 0 })
        assertEquals(ExecutionState.AwaitingConsent, agent.session.value?.state)
    }

    // --- «Отменить» (A4′ phase 2, spec §6.6, owner fork F2-3.3) --------------------------------

    private val note = ToolId("fixture_note")
    private val unnote = ToolId("fixture_unnote")
    private val undoRegistry = FakeToolRegistry(
        listOf(note, unnote).map {
            ToolDescriptor(id = it, level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL, argSchema = listOf(ActionArg("name", description = "fixture")), risk = ActionRiskLevel.SAFE, durability = ToolDurability.TRANSIENT)
        },
    )
    private val undoInverses = ToolInverses { forward, _ ->
        if (forward.id == note) Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal(forward.args.getValue("name"))))) else Undo.NotUndoable
    }
    private val noteGoal = AgentGoal("запомни вельтрикс", GoalShape.Free("запомни вельтрикс"))
    private val notePlan = ExecutionPlan(
        listOf(PlanStep(0, ToolInvocation(note, mapOf("name" to ArgSource.Literal("вельтрикс"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT)),
    )

    /** A finished one-step note plan on screen, the way a real one arrives: started, then attached. */
    private suspend fun TestScope.finishedNote(tools: FakeToolExecutor): LauncherAgentSession {
        val agent = collaborator(tools, this, registry = undoRegistry, inverses = undoInverses)
        val started = StartAgentSessionUseCase(TemplatePlanner(), store, ids, undoRegistry).startPlanned(noteGoal, notePlan)
        agent.attach((started as OperationResult.Success).value!!.id)
        advanceUntilIdle()
        check(agent.session.value?.state == ExecutionState.Completed) { "seed expected Completed, was ${agent.session.value?.state}" }
        return agent
    }

    @Test
    fun `the undo review follows the session on screen`() = runTest {
        val agent = finishedNote(FakeToolExecutor(listOf(ToolResult.Effected())))

        assertEquals(
            listOf(StepUndo(0, Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("вельтрикс")))))),
            agent.undoReview.value,
        )

        agent.cancel()
        advanceUntilIdle()
        assertEquals(emptyList<StepUndo>(), agent.undoReview.value)
    }

    @Test
    fun `Undo starts a session that undoes the finished plan and shows it`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected(), ToolResult.Effected()))
        val agent = finishedNote(tools)

        agent.undo()
        advanceUntilIdle()

        assertEquals(listOf(note, unnote), tools.invocations.map { it.id })
        assertEquals(ExecutionState.Completed, agent.session.value?.state)
        assertEquals(listOf(StepRationale.COMPENSATION), agent.session.value?.plan?.steps?.map { it.rationale })
        assertEquals("an undo offers no undo of its own", emptyList<StepUndo>(), agent.undoReview.value)
    }

    /** Review Focus 1: two taps, two new sessions otherwise — the store's conditional write cannot see them. */
    @Test
    fun `a second Undo tap while the first is running starts nothing`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected(), ToolResult.Effected(), ToolResult.Effected()))
        val agent = finishedNote(tools)

        agent.undo()
        agent.undo()
        advanceUntilIdle()

        assertEquals(listOf(note, unnote), tools.invocations.map { it.id })
    }

    /** A one-step store search the MODEL proposed, stopped at its consent card, the process killed. */
    private suspend fun pausedModelPlan(): AgentSession {
        val session = AgentSession(
            id = AgentSessionId("m1"),
            goal = AgentGoal("найди lumora в магазине", GoalShape.Free("найди lumora в магазине")),
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("lumora"))),
                        risk = ActionRiskLevel.CONFIRM,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                ),
            ),
            cursor = 0,
            state = ExecutionState.Paused,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.PlanCreated(1),
                    TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL)),
                    TraceEvent.SessionPaused,
                ),
            ),
        )
        store.save(session)
        return session
    }

    /** Spec §7.12.3 condition 4: the stale session is closed and its words go through the route again. */
    @Test
    fun `continuing a paused plan the model wrote closes it and takes its words through the route again`() = runTest {
        store.save(pausedModelPlan())
        val agent = collaborator(FakeToolExecutor(emptyList()), this)
        agent.restoreOnStart()
        advanceUntilIdle()

        agent.continueSession()
        advanceUntilIdle()

        assertEquals(listOf("найди lumora в магазине"), rerouted)
        assertNull(store.activeOrNull)
        assertNull(agent.session.value)
    }

    @Test
    fun `going back through the route performs nothing on the way`() = runTest {
        store.save(pausedModelPlan())
        val tools = FakeToolExecutor(emptyList())
        val agent = collaborator(tools, this)
        agent.restoreOnStart()
        advanceUntilIdle()

        agent.continueSession()
        advanceUntilIdle()

        assertEquals(emptyList<Any>(), tools.invocations)
    }
}
