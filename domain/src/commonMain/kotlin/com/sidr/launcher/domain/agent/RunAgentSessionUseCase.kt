package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/**
 * Turns the crank: one transition at a time, persisting after every one, until the session must stop.
 * Every transition is saved before the next one begins, which is what makes `am force-stop`
 * recoverable rather than lossy.
 *
 * It drives [AgentExecutor.prepare] and [AgentExecutor.perform] **separately, not through
 * [AgentExecutor.advance]** — that is the whole reason the engine is split. A session persisted
 * between the halves carries `ToolInvoked` with no matching `ToolObserved`, which is exactly the "the
 * process died during a tool call" signal the resume path reads back. Composing the two halves here
 * would leave the split as decoration: a crash inside the tool call would lose the record entirely,
 * and on resume the step would be silently re-run, executing its side effect a second time.
 *
 * A failed save between the halves therefore returns before the tool is called, not after. Not being
 * able to record the intent is grounds to not perform the effect.
 */
class RunAgentSessionUseCase(
    private val executor: AgentExecutor,
    private val store: AgentSessionStore,
) {
    suspend fun run(session: AgentSession): OperationResult<AgentSession> {
        var current = session
        while (current.state == ExecutionState.Running) {
            // Half one: decide, and record the decision. `prepare` deliberately does not advance the
            // cursor, so this save is replay-safe — resuming from it goes through `perform`, not
            // through a second `prepare`.
            val prepared = executor.prepare(current)
            if (prepared != current) {
                when (val saved = store.save(prepared)) {
                    is OperationResult.Failure -> return saved
                    is OperationResult.Success -> Unit
                }
            }

            // Half two: the one call to the world.
            val next = executor.perform(prepared)

            // Fixed point: neither half moved anything and the state is still `Running`, so every
            // further iteration would do exactly the same nothing. This is a reachable branch, not
            // speculative defence: `perform` returns the session UNCHANGED when the resolved step
            // does not match the trace — the tail names an index the plan does not contain, or names
            // a different tool at that index — which is what a persisted trace replayed against a
            // swapped plan (a restore against a different build, a shorter re-plan) looks like. On
            // that shape `prepare` short-circuits on the mid-step tail and `perform` refuses the
            // lookup, so the composition has no way forward. Without this net that is a silent
            // permanent stall: no trace event, no state change, no progress. `Blocked` says it aloud.
            if (next == current) {
                current = next.ended(ExecutionState.Blocked)
                return persist(current)
            }

            current = next
            // Skipped only when `perform` was a no-op, in which case `prepared` differs from `current`
            // (the fixed-point branch above caught the other case) and was already saved above.
            if (next != prepared) {
                when (val saved = store.save(current)) {
                    is OperationResult.Failure -> return saved
                    is OperationResult.Success -> Unit
                }
            }
        }
        return persist(current)
    }

    private suspend fun persist(session: AgentSession): OperationResult<AgentSession> {
        val saved = store.save(session)
        if (saved is OperationResult.Failure) return saved
        if (session.state.isTerminal) {
            val deleted = store.delete(session.id)
            if (deleted is OperationResult.Failure) return deleted
        }
        return OperationResult.Success(session)
    }
}
