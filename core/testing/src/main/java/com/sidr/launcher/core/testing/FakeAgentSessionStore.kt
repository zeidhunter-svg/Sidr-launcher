package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult

/**
 * In-memory store mirroring the Room contract, including the conditional consent write — the property
 * the double-confirmation test depends on. [saved] keeps every write so tests can assert the sequence,
 * not only the end state.
 */
class FakeAgentSessionStore : AgentSessionStore {

    val saved = mutableListOf<AgentSession>()
    private var current: AgentSession? = null
    var failNextSave: Boolean = false

    val activeOrNull: AgentSession? get() = current
    val active: AgentSession get() = requireNotNull(current) { "no active session" }

    override suspend fun active(): OperationResult<AgentSession?> = OperationResult.Success(current)

    override suspend fun save(session: AgentSession): OperationResult<Unit> {
        if (failNextSave) {
            failNextSave = false
            return OperationResult.Failure(OperationError.UnknownError("save failed"))
        }
        saved += session
        current = session
        return OperationResult.Success(Unit)
    }

    override suspend fun delete(id: AgentSessionId): OperationResult<Unit> {
        if (current?.id == id) current = null
        return OperationResult.Success(Unit)
    }

    override suspend fun recordConsentIfPending(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<Boolean> {
        val session = current
        if (session == null || session.id != id) return OperationResult.Success(false)
        if (session.state != ExecutionState.AwaitingConsent) return OperationResult.Success(false)
        if (session.consents.containsKey(stepIndex)) return OperationResult.Success(false)

        current = session.copy(consents = session.consents + (stepIndex to granted))
        return OperationResult.Success(true)
    }
}
