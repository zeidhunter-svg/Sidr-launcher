package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.trace.ExecutionTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 2, spec §6.2 — "what to undo", derived from what a finished session actually recorded.
 * Pure: the engine is not involved, and `CompensationOutsideTheEngineGuardTest` (`:app`) holds that
 * it never will be.
 */
class CompensationTest {

    private val note = ToolId("fixture_note")       // reversible: undone by fixture_unnote
    private val unnote = ToolId("fixture_unnote")
    private val open = ToolId("fixture_open")       // leaves nothing that stays
    private val erase = ToolId("fixture_erase")     // irreversible

    private fun tool(id: ToolId, risk: ActionRiskLevel, durability: ToolDurability = ToolDurability.TRANSIENT) =
        ToolDescriptor(id = id, level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL, risk = risk, durability = durability)

    /** `fixture_unnote` is CONFIRM on purpose: the undo plan must carry the REGISTRY's risk. */
    private val registry = FakeToolRegistry(
        listOf(
            tool(note, ActionRiskLevel.SAFE),
            tool(unnote, ActionRiskLevel.CONFIRM),
            tool(open, ActionRiskLevel.SAFE),
            tool(erase, ActionRiskLevel.DANGEROUS, ToolDurability.DURABLE),
        ),
    )

    private val inverses = ToolInverses { forward, _ ->
        when (forward.id) {
            note -> Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal(forward.args.getValue("name")))))
            open -> Undo.NothingToUndo
            else -> Undo.NotUndoable
        }
    }

    private fun step(index: Int, id: ToolId, name: String, rationale: StepRationale = StepRationale.GOAL_DIRECT) =
        PlanStep(index, ToolInvocation(id, mapOf("name" to ArgSource.Literal(name))), ActionRiskLevel.SAFE, StepPrecondition.None, rationale)

    private fun finished(steps: List<PlanStep>, observations: Map<Int, ToolResult>) = AgentSession(
        id = AgentSessionId("s-finished"),
        goal = AgentGoal("разбери вельтрикс", GoalShape.Free("разбери вельтрикс")),
        plan = ExecutionPlan(steps),
        cursor = steps.size,
        state = ExecutionState.Completed,
        observations = observations,
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    @Test
    fun `review lists every step that acted, newest first, with how each is undone`() {
        val session = finished(
            listOf(step(0, note, "вельтрикс"), step(1, open, "лумора"), step(2, erase, "ledger")),
            mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Effected(), 2 to ToolResult.Effected()),
        )

        assertEquals(
            listOf(
                StepUndo(2, Undo.NotUndoable),
                StepUndo(1, Undo.NothingToUndo),
                StepUndo(0, Undo.By(ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("вельтрикс"))))),
            ),
            Compensation.review(session, inverses),
        )
    }

    /** Review Focus 5. A cancelled uninstall is exactly this: the act left the launcher, unseen. */
    @Test
    fun `a handed-off step is never undone, because its outcome was never seen`() {
        val session = finished(listOf(step(0, note, "вельтрикс")), mapOf(0 to ToolResult.HandedOff()))

        assertEquals(listOf(StepUndo(0, Undo.NotUndoable)), Compensation.review(session, inverses))
    }

    @Test
    fun `a step that observed, failed, was skipped or never ran is not in the review`() {
        val session = finished(
            listOf(step(0, note, "а"), step(1, note, "б"), step(2, note, "в"), step(3, note, "г")),
            mapOf(
                0 to ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED),
                1 to ToolResult.Failed(CommandFailure.Generic),
            ),
        )

        assertEquals(emptyList<StepUndo>(), Compensation.review(session, inverses))
    }

    @Test
    fun `the undo plan runs the inverses newest first, as undo steps, at the registry's risk`() {
        val session = finished(
            listOf(step(0, note, "вельтрикс"), step(1, note, "лумора")),
            mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Effected()),
        )

        val plan = Compensation.plan(Compensation.review(session, inverses), registry)

        assertEquals(
            ExecutionPlan(
                listOf(
                    PlanStep(0, ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("лумора"))), ActionRiskLevel.CONFIRM, StepPrecondition.None, StepRationale.COMPENSATION),
                    PlanStep(1, ToolInvocation(unnote, mapOf("name" to ArgSource.Literal("вельтрикс"))), ActionRiskLevel.CONFIRM, StepPrecondition.None, StepRationale.COMPENSATION),
                ),
            ),
            plan,
        )
    }

    @Test
    fun `nothing that can be undone gives no plan at all`() {
        assertNull(
            Compensation.plan(listOf(StepUndo(1, Undo.NotUndoable), StepUndo(0, Undo.NothingToUndo)), registry),
        )
    }

    /** The undo of an undo is a redo, and nobody asked for one. */
    @Test
    fun `a plan that is itself an undo offers no undo of its own`() {
        val undo = finished(
            listOf(step(0, note, "вельтрикс", rationale = StepRationale.COMPENSATION)),
            mapOf(0 to ToolResult.Effected()),
        )

        assertTrue(Compensation.isUndo(undo.plan))
        assertEquals(emptyList<StepUndo>(), Compensation.review(undo, inverses))
    }
}
