package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/** Cancels and deletes. A cancel that lands between transitions guarantees the next step never starts. */
class CancelAgentSessionUseCase(private val store: AgentSessionStore) {
    suspend fun cancel(id: AgentSessionId): OperationResult<Unit> = store.delete(id)
}
