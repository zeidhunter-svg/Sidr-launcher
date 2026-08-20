package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * Records one consent decision and resumes. Returns `null` when the decision did not apply — the step
 * was not awaiting one, which is exactly what a second tap on the same button looks like.
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

        val resumed = current
            .record(TraceEvent.ConsentResolved(stepIndex, granted))
            .copy(state = ExecutionState.Running)

        return when (val ran = run.run(resumed)) {
            is OperationResult.Failure -> ran
            is OperationResult.Success -> OperationResult.Success(ran.value)
        }
    }
}
