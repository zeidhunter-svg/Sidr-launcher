package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.RejectionReason
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * Domain ↔ [SessionDto]. **Exhaustive in both directions, with no silent default** — every `when` here
 * covers the full set of its type's variants and ends in a throw rather than in a fallback value.
 *
 * Exhaustive is not the same as *total*, and this mapper is deliberately not total. There are two
 * throws, for two unrelated reasons:
 *  - **Decode side — unreadability.** A persisted value naming no known variant throws
 *    [IllegalArgumentException]. [JvmAgentSessionStore] deletes the undecodable file and reports the
 *    fixed token `file_agent_session_corrupt`.
 *  - **Encode side — provenance.** [toDto] refuses [GoalShape.AppNotInstalled], which is a perfectly
 *    well-formed value and readable in every sense. It is refused because *this* consumer's planner
 *    cannot have produced it. The rule is "each consumer persists only its own planner's shapes", and
 *    it is unchanged; what changed is the other side of it. `AgentSessionMappers.toSessionEntity` used
 *    to refuse [GoalShape.Free] as the exact mirror of this arm, and since A1' Task 9 it **encodes**
 *    it, because `CompositePlanner(TemplatePlanner, ToolMatchPlanner)` makes `Free` a shape the
 *    Android planner really does produce. `FilePlanner` still never produces `AppNotInstalled`, so
 *    this arm stands as it is — the asymmetry is now in the planners, not in the mappers' reasoning.
 *
 * **Failing loudly is deliberate.** A store that guesses at a value it cannot read produces a session
 * that looks whole and is not — the failure mode `RoomAgentSessionStoreTest` guards against on the
 * other side with "a `ToolObserved` with no observation on its step is a `Failure`, not a guessed
 * state".
 *
 * **This consumer preserves a `Failed` observation's [CommandFailure] variant.** The Android mapper
 * drops it and restores `Generic` — a named A0 gap. Keeping it here shows that loss is a mapper choice
 * rather than a limitation of the contract.
 */
internal object SessionMapper {

    fun toDto(session: AgentSession): SessionDto = SessionDto(
        id = session.id.value,
        goalText = session.goal.text,
        goalFreeText = when (val shape = session.goal.shape) {
            is GoalShape.Free -> shape.text
            is GoalShape.AppNotInstalled -> throw IllegalArgumentException(
                "JvmAgentSessionStore persists GoalShape.Free only; this consumer never produces " +
                    "AppNotInstalled, and writing one would mean the wrong planner built this session.",
            )
        },
        steps = session.plan.steps.map(::stepDto),
        cursor = session.cursor,
        state = session.state.name,
        observations = session.observations.mapValues { (_, r) -> resultDto(r) },
        consents = session.consents,
        trace = session.trace.events.map(::traceDto),
    )

    fun fromDto(dto: SessionDto): AgentSession = AgentSession(
        id = AgentSessionId(dto.id),
        goal = AgentGoal(dto.goalText, GoalShape.Free(dto.goalFreeText)),
        plan = ExecutionPlan(dto.steps.map(::step)),
        cursor = dto.cursor,
        state = enum(ExecutionState.entries, dto.state, "ExecutionState"),
        observations = dto.observations.mapValues { (_, d) -> result(d) },
        consents = dto.consents,
        trace = ExecutionTrace(dto.trace.map(::traceEvent)),
    )

    private fun stepDto(step: PlanStep) = StepDto(
        index = step.index,
        toolId = step.invocation.id.value,
        args = step.invocation.args.mapValues { (_, s) -> argDto(s) },
        risk = step.risk.name,
        precondition = preconditionDto(step.precondition),
        rationale = step.rationale.name,
    )

    private fun step(dto: StepDto) = PlanStep(
        index = dto.index,
        invocation = ToolInvocation(ToolId(dto.toolId), dto.args.mapValues { (_, d) -> arg(d) }),
        risk = enum(ActionRiskLevel.entries, dto.risk, "ActionRiskLevel"),
        precondition = precondition(dto.precondition),
        rationale = enum(StepRationale.entries, dto.rationale, "StepRationale"),
    )

    private fun argDto(source: ArgSource) = when (source) {
        is ArgSource.Literal -> ArgSourceDto(kind = "Literal", value = source.value)
        is ArgSource.FromStep -> ArgSourceDto(kind = "FromStep", stepIndex = source.stepIndex, key = source.key)
    }

    private fun arg(dto: ArgSourceDto): ArgSource = when (dto.kind) {
        "Literal" -> ArgSource.Literal(require(dto.value, "ArgSource.Literal.value"))
        "FromStep" -> ArgSource.FromStep(
            require(dto.stepIndex, "ArgSource.FromStep.stepIndex"),
            require(dto.key, "ArgSource.FromStep.key"),
        )
        else -> throw IllegalArgumentException("unknown ArgSource kind: ${dto.kind}")
    }

    private fun preconditionDto(p: StepPrecondition) = when (p) {
        StepPrecondition.None -> PreconditionDto(kind = "None")
        is StepPrecondition.PreviousStepObserved -> PreconditionDto("PreviousStepObserved", p.fact.name)
    }

    private fun precondition(dto: PreconditionDto): StepPrecondition = when (dto.kind) {
        "None" -> StepPrecondition.None
        "PreviousStepObserved" -> StepPrecondition.PreviousStepObserved(
            enum(ObservedFact.entries, require(dto.fact, "precondition.fact"), "ObservedFact"),
        )
        else -> throw IllegalArgumentException("unknown StepPrecondition kind: ${dto.kind}")
    }

    private fun resultDto(result: ToolResult) = when (result) {
        is ToolResult.Effected -> ResultDto("Effected", output = result.output.values)
        is ToolResult.Observed -> ResultDto("Observed", fact = result.fact.name, output = result.output.values)
        is ToolResult.Failed -> ResultDto("Failed", failure = failureName(result.failure))
    }

    private fun result(dto: ResultDto): ToolResult = when (dto.kind) {
        "Effected" -> ToolResult.Effected(ToolOutput(dto.output))
        "Observed" -> ToolResult.Observed(
            enum(ObservedFact.entries, require(dto.fact, "result.fact"), "ObservedFact"),
            ToolOutput(dto.output),
        )
        "Failed" -> ToolResult.Failed(failure(require(dto.failure, "result.failure")))
        else -> throw IllegalArgumentException("unknown ToolResult kind: ${dto.kind}")
    }

    private fun failureName(failure: CommandFailure) = when (failure) {
        CommandFailure.Generic -> "Generic"
        CommandFailure.CantOpenApp -> "CantOpenApp"
        CommandFailure.NoSearchApp -> "NoSearchApp"
        CommandFailure.CantOpenUrl -> "CantOpenUrl"
        CommandFailure.NoStoreApp -> "NoStoreApp"
    }

    private fun failure(name: String): CommandFailure = when (name) {
        "Generic" -> CommandFailure.Generic
        "CantOpenApp" -> CommandFailure.CantOpenApp
        "NoSearchApp" -> CommandFailure.NoSearchApp
        "CantOpenUrl" -> CommandFailure.CantOpenUrl
        "NoStoreApp" -> CommandFailure.NoStoreApp
        else -> throw IllegalArgumentException("unknown CommandFailure: $name")
    }

    private fun traceDto(event: TraceEvent): TraceDto = when (event) {
        is TraceEvent.PlanCreated -> TraceDto("PlanCreated", stepCount = event.stepCount)
        is TraceEvent.StepStarted -> TraceDto("StepStarted", index = event.index)
        is TraceEvent.StepSkipped -> TraceDto("StepSkipped", index = event.index, precondition = preconditionDto(event.precondition))
        is TraceEvent.StepRejected -> TraceDto("StepRejected", index = event.index, reason = event.reason.name)
        is TraceEvent.ConsentRequested -> TraceDto("ConsentRequested", index = event.index, reason = event.reason.name)
        is TraceEvent.ConsentResolved -> TraceDto("ConsentResolved", index = event.index, granted = event.granted)
        is TraceEvent.ToolInvoked -> TraceDto("ToolInvoked", index = event.index, toolId = event.toolId.value)
        is TraceEvent.ToolObserved -> TraceDto("ToolObserved", index = event.index, result = resultDto(event.result))
        TraceEvent.SessionPaused -> TraceDto("SessionPaused")
        TraceEvent.SessionResumed -> TraceDto("SessionResumed")
        is TraceEvent.SessionEnded -> TraceDto("SessionEnded", state = event.state.name)
    }

    private fun traceEvent(dto: TraceDto): TraceEvent = when (dto.kind) {
        "PlanCreated" -> TraceEvent.PlanCreated(require(dto.stepCount, "PlanCreated.stepCount"))
        "StepStarted" -> TraceEvent.StepStarted(require(dto.index, "StepStarted.index"))
        "StepSkipped" -> TraceEvent.StepSkipped(
            require(dto.index, "StepSkipped.index"),
            precondition(require(dto.precondition, "StepSkipped.precondition")),
        )
        "StepRejected" -> TraceEvent.StepRejected(
            require(dto.index, "StepRejected.index"),
            enum(RejectionReason.entries, require(dto.reason, "StepRejected.reason"), "RejectionReason"),
        )
        "ConsentRequested" -> TraceEvent.ConsentRequested(
            require(dto.index, "ConsentRequested.index"),
            enum(ConsentReason.entries, require(dto.reason, "ConsentRequested.reason"), "ConsentReason"),
        )
        "ConsentResolved" -> TraceEvent.ConsentResolved(
            require(dto.index, "ConsentResolved.index"),
            require(dto.granted, "ConsentResolved.granted"),
        )
        "ToolInvoked" -> TraceEvent.ToolInvoked(
            require(dto.index, "ToolInvoked.index"),
            ToolId(require(dto.toolId, "ToolInvoked.toolId")),
        )
        "ToolObserved" -> TraceEvent.ToolObserved(
            require(dto.index, "ToolObserved.index"),
            result(require(dto.result, "ToolObserved.result")),
        )
        "SessionPaused" -> TraceEvent.SessionPaused
        "SessionResumed" -> TraceEvent.SessionResumed
        "SessionEnded" -> TraceEvent.SessionEnded(
            enum(ExecutionState.entries, require(dto.state, "SessionEnded.state"), "ExecutionState"),
        )
        else -> throw IllegalArgumentException("unknown TraceEvent kind: ${dto.kind}")
    }

    private fun <T> require(value: T?, what: String): T =
        value ?: throw IllegalArgumentException("missing $what in the persisted session")

    private fun <E : Enum<E>> enum(all: List<E>, name: String, what: String): E =
        all.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("unknown $what: $name")
}
