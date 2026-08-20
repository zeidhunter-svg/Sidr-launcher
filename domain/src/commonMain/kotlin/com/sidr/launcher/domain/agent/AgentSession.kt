package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * The eight states A0 can actually produce. `PartiallyCompleted` from the 2026-07-11 A4 spec is
 * deliberately absent: without re-planning A0 cannot reach it, and a state the engine cannot produce
 * is a lie in the type. A step skipped by an unsatisfied precondition is part of a normal [Completed].
 */
enum class ExecutionState {
    Planning, Running, AwaitingConsent, Paused, Completed, Cancelled, Failed, Blocked;

    val isTerminal: Boolean
        get() = this == Completed || this == Cancelled || this == Failed || this == Blocked
}

enum class ConsentReason { RISK_LEVEL, RISK_RAISED, MISSING_PERMISSION, DURABLE_EFFECT }

data class ConsentCheckpoint(val stepIndex: Int, val reason: ConsentReason)

/** Loop bounds. A wall-clock limit needs a clock port and is honestly deferred to A4'. */
data class RuntimeBudget(val maxSteps: Int, val maxConsecutiveFailures: Int) {
    companion object {
        /** A0's default: two steps of headroom over the one plan shape that exists. */
        val Default = RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2)
    }
}

/** Port: the domain must not know about UUIDs, and tests must be deterministic. */
interface AgentSessionIdFactory {
    fun newId(): AgentSessionId
}

/**
 * An immutable snapshot. Keeping state as a value and behaviour as a function ([AgentExecutor]) is what
 * lets every test drive the machine without coroutine timing, and what makes persistence a plain save.
 */
data class AgentSession(
    val id: AgentSessionId,
    val goal: AgentGoal,
    val plan: ExecutionPlan,
    val cursor: Int,
    val state: ExecutionState,
    val observations: Map<Int, ToolResult>,
    val consents: Map<Int, Boolean>,
    val trace: ExecutionTrace,
) {
    internal fun record(event: TraceEvent): AgentSession =
        copy(trace = ExecutionTrace(trace.events + event))

    internal fun ended(next: ExecutionState): AgentSession =
        copy(state = next).record(TraceEvent.SessionEnded(next))
}
