package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/**
 * Port: persistence for the **one** active session. There is no history here — a terminal state
 * deletes the record, so at rest the store is empty. A durable trace journal is A5's decision, not
 * something A0 quietly introduces.
 */
interface AgentSessionStore {

    /** The active session, or `null` when none is in flight. */
    suspend fun active(): OperationResult<AgentSession?>

    suspend fun save(session: AgentSession): OperationResult<Unit>

    suspend fun delete(id: AgentSessionId): OperationResult<Unit>

    /**
     * Conditional write: records [granted] for [stepIndex] **only if** that step is still awaiting a
     * decision. Returns `true` iff it applied.
     *
     * This is where "a double confirmation must not execute a step twice" is actually solved. Not a
     * flag the UI checks and not a debounce — a conditional `UPDATE`, so two taps that race cannot
     * both win. Whole-object writes were avoided deliberately: that pattern already cost this project
     * the `autoHideNavBar` bug (DS-11), which only surfaced on device.
     */
    suspend fun recordConsentIfPending(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<Boolean>
}
