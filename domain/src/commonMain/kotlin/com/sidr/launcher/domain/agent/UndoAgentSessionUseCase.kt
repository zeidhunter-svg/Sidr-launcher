package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolRegistry

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
     * is finished: a paused or waiting plan has nothing final to undo.
     *
     * **A cancelled plan is finished too** (A4′ phase 3b, Task 12 — phase 2's N2, answered «partial» in
     * `DOC-ILM-4`'s sense, plan §0.2 B-11; the owner may overturn). The user refused a step AFTER an
     * earlier one acted, and what that one did is real — the most natural moment to ask «what to undo».
     * So a `Cancelled` session reviews like a part-way failure: [Compensation.review] lists every step
     * that acted, and a plan refused before anything acted reviews to nothing by the same rule. Until
     * Task 12 every `Cancelled` session reviewed to nothing («a cancelled one draws no card»).
     *
     * Also empty for a plan the clock stopped (owner fork F2-1b): its cut call may still be acting,
     * and an undo is a world call too — [cutCallMayStillAct], the one evidence «Повторить остаток» reads
     * as well (A4′ phase 3b, ruling E11-R1); its KDoc names the second shape that pair also covers.
     */
    fun review(session: AgentSession): List<StepUndo> = when {
        !session.state.isTerminal -> emptyList()
        session.cutCallMayStillAct() -> emptyList()
        else -> Compensation.review(session, inverses)
    }

    /** A persisted `Running` undo session, or `Success(null)` when there is nothing to undo. */
    suspend fun undo(finished: AgentSession): OperationResult<AgentSession?> {
        val plan = Compensation.plan(review(finished), registry) ?: return OperationResult.Success(null)
        return start.startPlanned(finished.goal, plan)
    }
}
