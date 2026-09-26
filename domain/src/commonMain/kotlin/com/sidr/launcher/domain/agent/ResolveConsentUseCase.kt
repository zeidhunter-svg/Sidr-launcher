package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/**
 * Records one consent decision and resumes. Returns `null` when the decision did not apply — the step
 * was not awaiting one, which is exactly what a second tap on the same button looks like.
 *
 * **It does not write the trace event.** The decision is persisted by the conditional write, and
 * `AgentExecutor.prepare` — which reads `consents` on its way past the checkpoint — is the single
 * writer of `TraceEvent.ConsentResolved`. Until 2026-08-23 both wrote, and on a refusal the trace
 * carried the same `ConsentResolved(i, false)` twice: `prepare` records it before ending the session
 * `Cancelled`, and this class had already recorded it (review finding F4). One decision is one event;
 * a trace that is 1:1 with reality (`DOC-ILM-3`) cannot double-count the one thing the user actually
 * did.
 */
class ResolveConsentUseCase(
    private val store: AgentSessionStore,
    private val run: RunAgentSessionUseCase,
) {
    suspend fun resolve(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<AgentSession?> {
        val applied = when (val write = store.recordConsentIfPending(id, stepIndex, granted)) {
            is OperationResult.Failure -> return write
            is OperationResult.Success -> write.value
        }
        if (!applied) return OperationResult.Success(null)

        val current = when (val active = store.active()) {
            is OperationResult.Failure -> return active
            is OperationResult.Success -> active.value ?: return OperationResult.Success(null)
        }

        val resumed = current.copy(state = ExecutionState.Running)

        return when (val ran = run.run(resumed)) {
            is OperationResult.Failure -> ran
            is OperationResult.Success -> OperationResult.Success(ran.value)
        }
    }
}
