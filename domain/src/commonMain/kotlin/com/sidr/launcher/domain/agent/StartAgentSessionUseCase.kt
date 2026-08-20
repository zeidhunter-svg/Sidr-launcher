package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent

/** Goal -> plan -> a persisted `Running` session. `NoPlan` returns `null` and writes nothing. */
class StartAgentSessionUseCase(
    private val planner: Planner,
    private val store: AgentSessionStore,
    private val ids: AgentSessionIdFactory,
    private val registry: ToolRegistry,
) {
    suspend fun start(goal: AgentGoal): OperationResult<AgentSessionId?> {
        val planned = planner.plan(goal, registry)
        if (planned !is PlanningResult.Planned) return OperationResult.Success(null)

        val id = ids.newId()
        val session = AgentSession(
            id = id,
            goal = goal,
            plan = planned.plan,
            cursor = 0,
            state = ExecutionState.Running,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(listOf(TraceEvent.PlanCreated(planned.plan.steps.size))),
        )

        return when (val saved = store.save(session)) {
            is OperationResult.Failure -> saved
            is OperationResult.Success -> OperationResult.Success(id)
        }
    }
}
