package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult

/**
 * «Отменить» (A4′ phase 2, spec §6.6, owner fork F2-3.3): a NEW session whose plan undoes a finished
 * one, run through the unchanged engine — the same gates, the same trace, deleted at rest like any
 * session. Never automatic; only on the user's tap.
 *
 * **Nothing about it asks less than today** (spec §6.3, owner): the undo plan carries the registry's
 * risk, and the engine cannot see that a step is an undo (`CompensationOutsideTheEngineGuardTest`).
 */
class UndoAgentSessionUseCase(
    private val start: StartAgentSessionUseCase,
    private val inverses: ToolInverses,
    private val registry: ToolRegistry,
) {
    /**
     * What «Отменить» on [session]'s card would undo, and what it could not. Empty unless the session
     * is finished: a paused or waiting plan has nothing final to undo, and a cancelled one draws no card.
     *
     * Also empty for a plan the clock stopped (owner fork F2-1b): its cut call may still be acting,
     * and an undo is a world call too. A `Blocked` session whose last step is `HandedOff` is exactly
     * that shape, but it is not the only one `AgentExecutor` produces with that pair: a step-budget
     * stop after a genuine `HandedOff` (today unreachable on Android while plans are one step, P8)
     * ends there too. The direction is still safe either way — both withhold undo on the same
     * evidence, an outcome that was never seen.
     */
    fun review(session: AgentSession): List<StepUndo> = when {
        !session.state.isTerminal || session.state == ExecutionState.Cancelled -> emptyList()
        session.state == ExecutionState.Blocked && session.observations[session.cursor - 1] is ToolResult.HandedOff -> emptyList()
        else -> Compensation.review(session, inverses)
    }

    /** A persisted `Running` undo session, or `Success(null)` when there is nothing to undo. */
    suspend fun undo(finished: AgentSession): OperationResult<AgentSession?> {
        val plan = Compensation.plan(review(finished), registry) ?: return OperationResult.Success(null)
        return start.startPlanned(finished.goal, plan)
    }
}
