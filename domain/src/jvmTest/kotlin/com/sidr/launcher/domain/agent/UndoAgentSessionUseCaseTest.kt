package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.trace.ExecutionTrace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 2 — «Отменить» is a NEW session that runs through the unchanged engine. Spec §6.3 (owner,
 * 2026-09-21): compensation is built as a pure addition, and no step asks less than it does today.
 */
class UndoAgentSessionUseCaseTest {

    private val note = ToolId("fixture_note")
    private val unnote = ToolId("fixture_unnote")
    private val store = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("u${++n}")
    }
    private val inverses = ToolInverses { forward, _ ->
        if (forward.id == note) {
            Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal(forward.args.getValue("name")))))
        } else {
            Undo.NotUndoable
        }
    }

    private fun registry(unnoteRisk: ActionRiskLevel) = FakeToolRegistry(
        listOf(
            ToolDescriptor(id = note, level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL, argSchema = listOf(com.sidr.launcher.domain.action.ActionArg("name", description = "fixture")), risk = ActionRiskLevel.SAFE, durability = ToolDurability.TRANSIENT),
            ToolDescriptor(id = unnote, level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL, argSchema = listOf(com.sidr.launcher.domain.action.ActionArg("name", description = "fixture")), risk = unnoteRisk, durability = ToolDurability.TRANSIENT),
        ),
    )

    private val noPlanner = object : Planner {
        override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult = PlanningResult.NoPlan
    }

    private fun useCase(registry: ToolRegistry) =
        UndoAgentSessionUseCase(StartAgentSessionUseCase(noPlanner, store, ids, registry), inverses, registry)

    private fun finished(state: ExecutionState = ExecutionState.Completed) = AgentSession(
        id = AgentSessionId("s-finished"),
        goal = AgentGoal("запомни вельтрикс", GoalShape.Free("запомни вельтрикс")),
        plan = ExecutionPlan(
            listOf(PlanStep(0, ToolInvocation(note, mapOf("name" to ArgSource.Literal("вельтрикс"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT)),
        ),
        cursor = 1,
        state = state,
        observations = mapOf(0 to ToolResult.Effected()),
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    @Test
    fun `undoing a finished plan starts a new session of its inverses and runs it through the same engine`() = runTest {
        val registry = registry(ActionRiskLevel.SAFE)
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val started = (useCase(registry).undo(finished()) as OperationResult.Success).value!!
        val ran = (RunAgentSessionUseCase(AgentExecutor(registry, tools), store).run(started) as OperationResult.Success).value

        assertEquals(AgentSessionId("u1"), started.id)
        assertEquals(listOf(StepRationale.COMPENSATION), started.plan.steps.map { it.rationale })
        assertEquals(ExecutionState.Completed, ran.state)
        assertEquals(listOf(unnote), tools.invocations.map { it.id })
        assertEquals(mapOf("name" to "вельтрикс"), tools.invocations.single().args)
    }

    /**
     * Spec §6.3 and §10 criterion 5: undo NEVER lowers a gate. Here the inverse is CONFIRM, and the
     * undo stops at consent exactly as that tool would anywhere else. M3a plants the lowering this
     * exists to catch — a branch in `checkpointFor` that waves undo steps through.
     */
    @Test
    fun `an undo whose inverse asks for consent stops at the gate - undo never lowers a gate`() = runTest {
        val registry = registry(ActionRiskLevel.CONFIRM)
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))

        val started = (useCase(registry).undo(finished()) as OperationResult.Success).value!!
        val ran = (RunAgentSessionUseCase(AgentExecutor(registry, tools), store).run(started) as OperationResult.Success).value

        assertEquals(ExecutionState.AwaitingConsent, ran.state)
        assertTrue("nothing may run before consent", tools.invocations.isEmpty())
    }

    /** Spec §6.6: a plan that failed or stopped part-way is exactly where «что откатить» matters. */
    @Test
    fun `a plan that failed or stopped part-way still offers what it did`() = runTest {
        val useCase = useCase(registry(ActionRiskLevel.SAFE))
        for (state in listOf(ExecutionState.Failed, ExecutionState.Blocked)) {
            assertEquals(
                "review for $state",
                listOf(StepUndo(0, Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("вельтрикс")))))),
                useCase.review(finished(state)),
            )
        }
    }

    /**
     * Owner fork F2-1b, applied to undo: a plan the clock stopped may still have its cut call acting,
     * and an undo is a world call too. The last step here was cut (`HandedOff` at the cursor's last
     * step of a `Blocked` session), so nothing is offered — not even the undoable step before it.
     */
    @Test
    fun `a plan stopped by a cut offers no undo while the cut call may still act`() = runTest {
        val cut = finished(ExecutionState.Blocked).let { done ->
            done.copy(
                plan = ExecutionPlan(
                    done.plan.steps + PlanStep(1, ToolInvocation(note, mapOf("name" to ArgSource.Literal("лумора"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT),
                ),
                cursor = 2,
                observations = mapOf(0 to ToolResult.Effected(), 1 to ToolResult.HandedOff()),
            )
        }

        assertEquals(emptyList<StepUndo>(), useCase(registry(ActionRiskLevel.SAFE)).review(cut))
    }

    @Test
    fun `a cancelled, paused or unfinished session has nothing to undo`() = runTest {
        val useCase = useCase(registry(ActionRiskLevel.SAFE))
        for (state in listOf(ExecutionState.Cancelled, ExecutionState.Paused, ExecutionState.AwaitingConsent, ExecutionState.Running)) {
            val session = finished(state)

            assertEquals("review for $state", emptyList<StepUndo>(), useCase.review(session))
            assertNull("undo for $state", (useCase.undo(session) as OperationResult.Success).value)
        }
        assertTrue("nothing may be persisted", store.saved.isEmpty())
    }
}
