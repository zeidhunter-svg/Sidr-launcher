package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ResolutionResult
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo

/** One step that acted, and how it is undone. [stepIndex] is its index in the finished plan. */
data class StepUndo(val stepIndex: Int, val undo: Undo)

/**
 * "What to undo", derived from what a finished session recorded (A4′ phase 2, spec §6.2, `B14`).
 * Pure: no store, no clock, and no engine — the engine never sees any of this (spec §6.3).
 *
 * **Coverage is small, and said so** (spec §6.2): on Android only `launcher_memory`'s two alias tools
 * have an inverse, and every Android plan that reaches them is one step (plan §0.3 P8).
 */
object Compensation {

    /**
     * Every step whose act this session knows happened, newest first, each with how it is undone:
     *  - `Effected` → [inverses];
     *  - `HandedOff` → [Undo.NotUndoable]: the act left the launcher and its outcome was never seen, so
     *    an inverse could act on something that did not happen (a cancelled uninstall). Never offered;
     *  - `Observed`, `Failed`, skipped, never reached → absent: nothing happened.
     *
     * A plan that is itself an undo reviews to nothing: the undo of an undo is a redo.
     */
    fun review(session: AgentSession, inverses: ToolInverses): List<StepUndo> {
        if (isUndo(session.plan)) return emptyList()
        return session.plan.steps.reversed().mapNotNull { step ->
            when (val result = session.observations[step.index]) {
                is ToolResult.Effected -> StepUndo(step.index, undoOfEffected(step, result, session, inverses))
                is ToolResult.HandedOff -> StepUndo(step.index, Undo.NotUndoable)
                is ToolResult.Observed, is ToolResult.Failed, null -> null
            }
        }
    }

    /**
     * The plan that runs [review]'s inverses in the review's order — newest first — or `null` when
     * nothing in it can be undone.
     *
     * **Risk is the registry's, never lower** (spec §6.3): an undo step asks exactly what the same tool
     * asks anywhere else. The engine takes the higher of this and the registry anyway (A0 finding F2),
     * so a plan-level mistake here cannot lower a gate — M3e shows that.
     */
    fun plan(review: List<StepUndo>, registry: ToolRegistry): ExecutionPlan? {
        val inverses = review.mapNotNull { (it.undo as? Undo.By)?.invocation }
        if (inverses.isEmpty()) return null
        return ExecutionPlan(
            inverses.mapIndexed { index, invocation ->
                PlanStep(
                    index = index,
                    invocation = invocation,
                    risk = registry.find(invocation.id)?.risk ?: ActionRiskLevel.DANGEROUS,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.COMPENSATION,
                )
            },
        )
    }

    /**
     * Whether [plan] undoes another. Such a plan never offers an undo of its own, and is never planned
     * again from its goal on resume (§6.4): its goal is the ORIGINAL goal, and re-planning it would
     * redo what it undoes.
     */
    fun isUndo(plan: ExecutionPlan): Boolean = plan.steps.any { it.rationale == StepRationale.COMPENSATION }

    private fun undoOfEffected(
        step: PlanStep,
        result: ToolResult.Effected,
        session: AgentSession,
        inverses: ToolInverses,
    ): Undo = when (val resolved = InvocationValidator.resolve(step.invocation, session.observations)) {
        is ResolutionResult.Resolved -> inverses.undoOf(resolved.invocation, result)
        // Unreachable for a step that ran: it was bound from these same observations. If it cannot be
        // bound now, the values an inverse needs are unknown, and a guessed undo is worse than none.
        is ResolutionResult.Rejected -> Undo.NotUndoable
    }
}
