package com.sidr.launcher.domain.trace

import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.tool.RejectionReason
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolResult

/**
 * What actually happened, in order. `DOC-ILM-3`: no step executes without an entry, and the trace is
 * 1:1 with reality — including the steps that were refused or skipped, which is why [StepRejected] and
 * [StepSkipped] exist.
 *
 * Events carry **no timestamp**: the domain has no clock (`commonMain` is stdlib + coroutines across
 * two targets), and the data layer stamps rows when it persists them. The goal text is stored once, on
 * the session, and never repeated per event.
 */
sealed interface TraceEvent {
    data class PlanCreated(val stepCount: Int) : TraceEvent
    data class StepStarted(val index: Int) : TraceEvent
    data class StepSkipped(val index: Int, val precondition: StepPrecondition) : TraceEvent
    data class StepRejected(val index: Int, val reason: RejectionReason) : TraceEvent
    data class ConsentRequested(val index: Int, val reason: ConsentReason) : TraceEvent
    data class ConsentResolved(val index: Int, val granted: Boolean) : TraceEvent
    data class ToolInvoked(val index: Int, val toolId: ToolId) : TraceEvent
    data class ToolObserved(val index: Int, val result: ToolResult) : TraceEvent
    data object SessionPaused : TraceEvent
    data object SessionResumed : TraceEvent
    data class SessionEnded(val state: ExecutionState) : TraceEvent
}

/** Ordered, append-only. */
data class ExecutionTrace(val events: List<TraceEvent> = emptyList())
