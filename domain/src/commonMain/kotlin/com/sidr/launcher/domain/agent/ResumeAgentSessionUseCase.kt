package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/**
 * «Продолжить» on a paused plan (A4′ phase 2, spec §6.4 as amended by owner fork F2-3.1).
 *
 * If nothing in the plan can have acted yet ([AgentSession.replansOnResume]), the goal is planned
 * AFRESH and run as a new session. What it saw before the pause may be stale — A0's acceptance
 * (§12.8) opened the store for an app the user had installed meanwhile. The new session **replaces**
 * the stale one in the store, because the store keeps one session at rest (plan §0.3 P5). A re-run
 * under the same step index would have made the trace lie (§0.3 P4), which is why this is a new
 * session and not a revision.
 *
 * Otherwise — something may have acted, a call is in flight, the plan is an undo, or the goal no
 * longer plans at all — the paused session continues from its cursor exactly as before phase 2.
 *
 * Re-planning after a FAILURE is not here: none of the deterministic planners can do anything better
 * after one, and its first consumer is phase 3's model planner (the amendment).
 */
class ResumeAgentSessionUseCase(
    private val start: StartAgentSessionUseCase,
    private val run: RunAgentSessionUseCase,
) {
    suspend fun resume(paused: AgentSession): OperationResult<AgentSession> {
        if (!paused.replansOnResume()) return run.run(paused.resumed())
        val fresh = start.plan(paused.goal) ?: return run.run(paused.resumed())
        val started = when (val saved = start.startPlanned(paused.goal, fresh)) {
            is OperationResult.Failure -> return saved
            // Practically unreachable: `plan` validated against the same registry just above, but the
            // federation is derived per call (A1″), so a source's tool set can change between that
            // `plan` and this `startPlanned`. Continuing the stale session is the honest fallback — it
            // is still the one in the store.
            is OperationResult.Success -> saved.value ?: return run.run(paused.resumed())
        }
        return run.run(started)
    }
}
