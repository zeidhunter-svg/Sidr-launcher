package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.db.entity.AgentPlanStepEntity
import com.sidr.launcher.data.repository.db.entity.AgentSessionEntity
import com.sidr.launcher.data.repository.db.entity.AgentTraceEventEntity
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A row that cannot be read back into a domain value. Never recovered from with a guessed default:
 * [RoomAgentSessionStore] turns this into `OperationResult.Failure`, because a corrupt row silently
 * becoming a runnable plan is worse than an honest failure.
 */
internal class CorruptAgentRowException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * The serializable half of `ArgSource`, and the reason it exists here rather than in `:domain`:
 * `:domain` is stdlib + coroutines across two KMP targets and carries no serialization plugin, so the
 * domain type cannot be `@Serializable`. The mapping is two explicit `when`s instead.
 *
 * The **encoding** direction is exhaustive by compiler: `ArgSource` is a sealed interface, so a third
 * variant will not compile until [toDto] handles it — which is where the author meets [toDomain] too.
 * The **decoding** direction is a `when` over a string and cannot be exhaustive; an unrecognised
 * `kind` throws rather than defaulting.
 */
@Serializable
internal data class ArgSourceDto(
    val kind: String,
    val value: String? = null,
    val stepIndex: Int? = null,
    val key: String? = null,
)

/**
 * Row <-> domain for the agent session. Pure, and total in one direction only: everything on the way
 * **out** is exhaustive over a sealed type or an enum, and everything on the way **in** is a `when`
 * over a string with an `else` that throws [CorruptAgentRowException].
 *
 * **The named fidelity gap (A0 spec §7).** A persisted `ToolResult.Failed` keeps its type but not the
 * `CommandFailure` variant, and restores as [CommandFailure.Generic]. In A0 that never round-trips: a
 * failed step ends the session, and a terminal session is deleted, so the value is written and then
 * dropped rather than written and then read back wrong. `Failed` also carries no output by
 * construction, so nothing is lost on the F6 side of the same row. **If A4' starts keeping failed
 * sessions, `observation_type` must widen to carry the variant** — this comment is the marker for
 * that, and `AgentSessionMappersTest` pins the current behaviour so the widening is a deliberate edit.
 */
internal object AgentSessionMappers {

    private const val KIND_LITERAL = "literal"
    private const val KIND_FROM_STEP = "from_step"

    private const val OBSERVATION_EFFECTED = "Effected"
    private const val OBSERVATION_OBSERVED = "Observed"
    private const val OBSERVATION_FAILED = "Failed"

    private const val SHAPE_APP_NOT_INSTALLED = "AppNotInstalled"
    private const val SHAPE_FREE = "Free"

    private val json = Json
    private val argsSerializer = MapSerializer(String.serializer(), ArgSourceDto.serializer())
    private val outputSerializer = MapSerializer(String.serializer(), String.serializer())

    // ---------------------------------------------------------------- domain -> rows

    /**
     * Persists exactly the shapes **this** consumer's planner produces — a set A1' Task 9 widened, and
     * this arm is the correction that came with it.
     *
     * **What changed.** A0 bound `TemplatePlanner` alone, which answers `NoPlan` to [GoalShape.Free],
     * so no Android session could carry that shape; this arm therefore **threw**, on the argument that
     * extending the `agent_session.goal_shape` vocabulary for a value this surface cannot construct is
     * the contract for an absent consumer that Master Plan §3.4 forbids. That argument died with its
     * premise: `AgentProvidesModule` now binds `CompositePlanner(TemplatePlanner, ToolMatchPlanner)`
     * and `RouteCommandUseCase` step 2b builds a `GoalShape.Free` goal for every undecided command, so
     * the Android planner does produce `Free`. The refusal was silently killing the block's headline
     * capability — `RoomAgentSessionStore.save` contained the throw as a `Failure`, step 2b fails open
     * on anything that is not `Success`, and a matched tool started no session, ran nothing and
     * reported nothing. `FreeTextGoalEndToEndTest` reproduces that end to end and now holds it closed;
     * it exists because every layer of the path had a green test against a *fake* of the next one.
     *
     * **What still holds.** §3.4 is satisfied by whether the shape is produced here, not by the
     * persisted vocabulary staying at one value — so this is an additive entry in an existing column's
     * vocabulary, not a schema change: `goal_shape` has been a discriminator `TEXT NOT NULL` column
     * since schema 4 precisely so a second shape is additive rather than a reinterpretation of rows
     * already on disk. The mirror in the second consumer is unchanged and still correct:
     * `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionMapper.kt` still throws
     * `IllegalArgumentException` on [GoalShape.AppNotInstalled], because `FilePlanner` never produces
     * that shape. Each consumer persists only its own planner's shapes and refuses the other's; what
     * moved is which shapes the *Android* planner produces, not the rule.
     *
     * **[readShape] learns the same value in the same commit.** The old KDoc argued `readShape` could
     * be left alone because a throwing encode writes no row for a decode to fail on. Once rows exist
     * that reasoning inverts: an encode/decode pair out of step is exactly the failure it claimed
     * impossible.
     *
     * **`goal_shape_arg` for `Free` is the shape's own `text`**, duplicated with `goal_text` rather
     * than stored empty and rebuilt from it on the way back. [AgentGoal] `text` and `GoalShape.Free`
     * `text` are independent fields: today's one construction site sets them equal, but nothing in the
     * type says so, and `AgentSessionPresentationTest` deliberately builds them *different* to pin
     * which of the two the surface reads. Rebuilding the shape from `goal_text` would therefore be a
     * lossy encode that silently rewrites the shape's text whenever the two differ — the same class of
     * round-trip defect as the one fix round 2 caught, where a `Free` goal persisted under the
     * `AppNotInstalled` string decoded back as the wrong shape. The cost is one duplicated column of
     * text that is already in the row and is deleted with it on any terminal state.
     */
    fun toSessionEntity(session: AgentSession, now: Long): AgentSessionEntity {
        val (shape, arg) = when (val goalShape = session.goal.shape) {
            is GoalShape.AppNotInstalled -> SHAPE_APP_NOT_INSTALLED to goalShape.query
            is GoalShape.Free -> SHAPE_FREE to goalShape.text
        }
        return AgentSessionEntity(
            id = session.id.value,
            goalText = session.goal.text,
            goalShape = shape,
            goalShapeArg = arg,
            state = session.state.name,
            cursor = session.cursor,
            createdAt = now,
        )
    }

    fun toStepEntities(session: AgentSession): List<AgentPlanStepEntity> =
        session.plan.steps.map { step ->
            val observation = session.observations[step.index]
            AgentPlanStepEntity(
                sessionId = session.id.value,
                stepIndex = step.index,
                toolId = step.invocation.id.value,
                argsJson = encodeArgs(step.invocation.args),
                risk = step.risk.name,
                preconditionFact = when (val precondition = step.precondition) {
                    StepPrecondition.None -> null
                    is StepPrecondition.PreviousStepObserved -> precondition.fact.name
                },
                rationale = step.rationale.name,
                observationType = when (observation) {
                    null -> null
                    is ToolResult.Effected -> OBSERVATION_EFFECTED
                    is ToolResult.Observed -> OBSERVATION_OBSERVED
                    is ToolResult.Failed -> OBSERVATION_FAILED
                },
                observationFact = (observation as? ToolResult.Observed)?.fact?.name,
                // `null` means "produced nothing", `{}` means "produced an empty map" — two different
                // facts, kept different. `Failed` produces no output by construction.
                observationOutputJson = when (observation) {
                    null, is ToolResult.Failed -> null
                    is ToolResult.Effected -> encodeOutput(observation.output)
                    is ToolResult.Observed -> encodeOutput(observation.output)
                },
                consent = session.consents[step.index],
            )
        }

    fun toTraceEntities(session: AgentSession, now: Long): List<AgentTraceEventEntity> =
        session.trace.events.mapIndexed { seq, event ->
            val (stepIndex, detail) = when (event) {
                is TraceEvent.PlanCreated -> null to event.stepCount.toString()
                is TraceEvent.StepStarted -> event.index to null
                is TraceEvent.StepSkipped -> event.index to when (val p = event.precondition) {
                    StepPrecondition.None -> null
                    is StepPrecondition.PreviousStepObserved -> p.fact.name
                }
                is TraceEvent.StepRejected -> event.index to event.reason.name
                is TraceEvent.ConsentRequested -> event.index to event.reason.name
                is TraceEvent.ConsentResolved -> event.index to event.granted.toString()
                is TraceEvent.ToolInvoked -> event.index to event.toolId.value
                // The result is NOT repeated here — it is on the step row's observation columns, so
                // one fact has one home and the two cannot disagree. See [readTrace].
                is TraceEvent.ToolObserved -> event.index to null
                TraceEvent.SessionPaused -> null to null
                TraceEvent.SessionResumed -> null to null
                is TraceEvent.SessionEnded -> null to event.state.name
            }
            AgentTraceEventEntity(
                sessionId = session.id.value,
                seq = seq,
                type = typeNameOf(event),
                stepIndex = stepIndex,
                detail = detail,
                at = now,
            )
        }

    /**
     * The persisted discriminator of a [TraceEvent]. Written out longhand rather than taken from
     * `this::class.simpleName` so the on-disk vocabulary does not move when a class is renamed —
     * a rename is then a migration decision, not a silent format change.
     */
    fun typeNameOf(event: TraceEvent): String = when (event) {
        is TraceEvent.PlanCreated -> "PlanCreated"
        is TraceEvent.StepStarted -> "StepStarted"
        is TraceEvent.StepSkipped -> "StepSkipped"
        is TraceEvent.StepRejected -> "StepRejected"
        is TraceEvent.ConsentRequested -> "ConsentRequested"
        is TraceEvent.ConsentResolved -> "ConsentResolved"
        is TraceEvent.ToolInvoked -> "ToolInvoked"
        is TraceEvent.ToolObserved -> "ToolObserved"
        TraceEvent.SessionPaused -> "SessionPaused"
        TraceEvent.SessionResumed -> "SessionResumed"
        is TraceEvent.SessionEnded -> "SessionEnded"
    }

    fun encodeArgs(args: Map<String, ArgSource>): String =
        json.encodeToString(argsSerializer, args.mapValues { (_, source) -> source.toDto() })

    private fun ArgSource.toDto(): ArgSourceDto = when (this) {
        is ArgSource.Literal -> ArgSourceDto(kind = KIND_LITERAL, value = value)
        is ArgSource.FromStep -> ArgSourceDto(kind = KIND_FROM_STEP, stepIndex = stepIndex, key = key)
    }

    private fun encodeOutput(output: ToolOutput): String =
        json.encodeToString(outputSerializer, output.values)

    // ---------------------------------------------------------------- rows -> domain

    fun toDomain(
        session: AgentSessionEntity,
        steps: List<AgentPlanStepEntity>,
        trace: List<AgentTraceEventEntity>,
    ): AgentSession = try {
        assemble(session, steps, trace)
    } catch (e: CorruptAgentRowException) {
        throw e
    } catch (e: Exception) {
        // Covers a malformed `args_json` (SerializationException) and `ExecutionPlan`'s own
        // `index == position` requirement (IllegalArgumentException). Both mean the same thing here:
        // what is on disk is not a plan.
        throw CorruptAgentRowException("agent session \"${session.id}\" is not readable", e)
    }

    private fun assemble(
        session: AgentSessionEntity,
        steps: List<AgentPlanStepEntity>,
        trace: List<AgentTraceEventEntity>,
    ): AgentSession {
        // A persisted plan always has at least one step: `TemplatePlanner` returns either two steps or
        // `NoPlan`, and `StartAgentSessionUseCase` writes nothing for `NoPlan`. So zero steps under a
        // live session row is not an empty plan — it is the two tables disagreeing, which is what a
        // `delete` landing between two reads looks like. Refusing it here is the load-bearing half of
        // the pair; `AgentSessionDao.loadActive`'s transaction is the other. Without this an empty
        // `ExecutionPlan` is constructible, `AgentExecutor.prepare` finds no step at the cursor, and
        // the run reports `Completed` for a goal on which nothing ran and nothing was traced.
        if (steps.isEmpty()) {
            throw CorruptAgentRowException("agent session \"${session.id}\" has a row but no steps")
        }

        val observations = steps.mapNotNull { row -> readObservation(row)?.let { row.stepIndex to it } }.toMap()
        val consents = steps.mapNotNull { row -> row.consent?.let { row.stepIndex to it } }.toMap()

        return AgentSession(
            id = AgentSessionId(session.id),
            goal = AgentGoal(text = session.goalText, shape = readShape(session)),
            plan = ExecutionPlan(steps.map(::readStep)),
            cursor = session.cursor,
            state = readState(session.state),
            observations = observations,
            consents = consents,
            trace = ExecutionTrace(trace.map { readTrace(it, observations) }),
        )
    }

    /**
     * The decode half of [toSessionEntity]'s vocabulary, and it must be edited in the same commit as
     * that one: a shape this can encode but not read back is an outage one layer later rather than
     * none. Not exhaustive by compiler — it is a `when` over a `String` — so an unrecognised
     * discriminator throws rather than falling back to a shape that happens to be constructible.
     */
    private fun readShape(session: AgentSessionEntity): GoalShape = when (session.goalShape) {
        SHAPE_APP_NOT_INSTALLED -> GoalShape.AppNotInstalled(session.goalShapeArg)
        SHAPE_FREE -> GoalShape.Free(session.goalShapeArg)
        else -> throw CorruptAgentRowException("unknown goal shape \"${session.goalShape}\"")
    }

    private fun readStep(row: AgentPlanStepEntity): PlanStep = PlanStep(
        index = row.stepIndex,
        invocation = ToolInvocation(ToolId(row.toolId), decodeArgs(row.argsJson)),
        risk = readRisk(row.risk),
        precondition = row.preconditionFact
            ?.let { StepPrecondition.PreviousStepObserved(readFact(it)) }
            ?: StepPrecondition.None,
        rationale = readRationale(row.rationale),
    )

    private fun decodeArgs(raw: String): Map<String, ArgSource> =
        json.decodeFromString(argsSerializer, raw).mapValues { (name, dto) -> dto.toDomain(name) }

    private fun ArgSourceDto.toDomain(name: String): ArgSource = when (kind) {
        KIND_LITERAL -> ArgSource.Literal(
            value ?: throw CorruptAgentRowException("arg \"$name\": a literal without a value"),
        )
        KIND_FROM_STEP -> ArgSource.FromStep(
            stepIndex ?: throw CorruptAgentRowException("arg \"$name\": a binding without a step index"),
            key ?: throw CorruptAgentRowException("arg \"$name\": a binding without a key"),
        )
        else -> throw CorruptAgentRowException("arg \"$name\": unknown ArgSource kind \"$kind\"")
    }

    private fun readObservation(row: AgentPlanStepEntity): ToolResult? = when (row.observationType) {
        null -> null
        OBSERVATION_EFFECTED -> ToolResult.Effected(readOutput(row))
        OBSERVATION_OBSERVED -> ToolResult.Observed(
            fact = row.observationFact
                ?.let(::readFact)
                ?: throw CorruptAgentRowException("step ${row.stepIndex}: an Observed result without a fact"),
            output = readOutput(row),
        )
        // The named fidelity gap: the variant was not persisted, so it comes back as Generic.
        OBSERVATION_FAILED -> ToolResult.Failed(CommandFailure.Generic)
        else -> throw CorruptAgentRowException(
            "step ${row.stepIndex}: unknown observation type \"${row.observationType}\"",
        )
    }

    private fun readOutput(row: AgentPlanStepEntity): ToolOutput {
        val raw = row.observationOutputJson
            ?: throw CorruptAgentRowException(
                "step ${row.stepIndex}: a ${row.observationType} result must carry an output object, " +
                    "even an empty one — null means \"produced nothing\"",
            )
        return ToolOutput(json.decodeFromString(outputSerializer, raw))
    }

    private fun readTrace(
        row: AgentTraceEventEntity,
        observations: Map<Int, ToolResult>,
    ): TraceEvent = when (row.type) {
        "PlanCreated" -> TraceEvent.PlanCreated(row.detail.requireInt(row, "step count"))
        "StepStarted" -> TraceEvent.StepStarted(row.requireStepIndex())
        "StepSkipped" -> TraceEvent.StepSkipped(
            row.requireStepIndex(),
            row.detail?.let { StepPrecondition.PreviousStepObserved(readFact(it)) } ?: StepPrecondition.None,
        )
        "StepRejected" -> TraceEvent.StepRejected(row.requireStepIndex(), readRejection(row.requireDetail()))
        "ConsentRequested" -> TraceEvent.ConsentRequested(row.requireStepIndex(), readConsentReason(row.requireDetail()))
        "ConsentResolved" -> TraceEvent.ConsentResolved(row.requireStepIndex(), readBoolean(row.requireDetail()))
        "ToolInvoked" -> TraceEvent.ToolInvoked(row.requireStepIndex(), ToolId(row.requireDetail()))
        // Read back from the step row rather than from this row: the observation columns are the one
        // place a result is stored. Missing means the two tables disagree, which is a corrupt read and
        // not something to paper over with a placeholder result.
        "ToolObserved" -> row.requireStepIndex().let { index ->
            TraceEvent.ToolObserved(
                index,
                observations[index] ?: throw CorruptAgentRowException(
                    "trace ${row.seq}: ToolObserved($index) has no observation on step $index",
                ),
            )
        }
        "SessionPaused" -> TraceEvent.SessionPaused
        "SessionResumed" -> TraceEvent.SessionResumed
        "SessionEnded" -> TraceEvent.SessionEnded(readState(row.requireDetail()))
        else -> throw CorruptAgentRowException("trace ${row.seq}: unknown event type \"${row.type}\"")
    }

    private fun AgentTraceEventEntity.requireStepIndex(): Int =
        stepIndex ?: throw CorruptAgentRowException("trace $seq: \"$type\" without a step index")

    private fun AgentTraceEventEntity.requireDetail(): String =
        detail ?: throw CorruptAgentRowException("trace $seq: \"$type\" without a detail")

    private fun String?.requireInt(row: AgentTraceEventEntity, what: String): Int =
        this?.toIntOrNull() ?: throw CorruptAgentRowException("trace ${row.seq}: \"${row.type}\" without a $what")

    private fun readBoolean(raw: String): Boolean = when (raw) {
        true.toString() -> true
        false.toString() -> false
        else -> throw CorruptAgentRowException("expected a boolean, found \"$raw\"")
    }

    // Enum reads, one explicit `when` each. No `enumValueOf` on unvalidated input, no reflection: an
    // unknown string is a corrupt row, never a guessed default. `AgentSessionMappersTest` drives
    // `values()` over each enum, so a constant added without a branch here turns that test red.

    private fun readState(raw: String): ExecutionState = when (raw) {
        ExecutionState.Planning.name -> ExecutionState.Planning
        ExecutionState.Running.name -> ExecutionState.Running
        ExecutionState.AwaitingConsent.name -> ExecutionState.AwaitingConsent
        ExecutionState.Paused.name -> ExecutionState.Paused
        ExecutionState.Completed.name -> ExecutionState.Completed
        ExecutionState.Cancelled.name -> ExecutionState.Cancelled
        ExecutionState.Failed.name -> ExecutionState.Failed
        ExecutionState.Blocked.name -> ExecutionState.Blocked
        else -> throw CorruptAgentRowException("unknown execution state \"$raw\"")
    }

    private fun readRisk(raw: String): ActionRiskLevel = when (raw) {
        ActionRiskLevel.SAFE.name -> ActionRiskLevel.SAFE
        ActionRiskLevel.CONFIRM.name -> ActionRiskLevel.CONFIRM
        ActionRiskLevel.DANGEROUS.name -> ActionRiskLevel.DANGEROUS
        else -> throw CorruptAgentRowException("unknown risk level \"$raw\"")
    }

    private fun readFact(raw: String): ObservedFact = when (raw) {
        ObservedFact.APP_NOT_INSTALLED.name -> ObservedFact.APP_NOT_INSTALLED
        ObservedFact.APP_AMBIGUOUS.name -> ObservedFact.APP_AMBIGUOUS
        else -> throw CorruptAgentRowException("unknown observed fact \"$raw\"")
    }

    private fun readRationale(raw: String): StepRationale = when (raw) {
        StepRationale.GOAL_DIRECT.name -> StepRationale.GOAL_DIRECT
        StepRationale.APP_NOT_INSTALLED_FALLBACK.name -> StepRationale.APP_NOT_INSTALLED_FALLBACK
        else -> throw CorruptAgentRowException("unknown step rationale \"$raw\"")
    }

    private fun readRejection(raw: String): RejectionReason = when (raw) {
        RejectionReason.UNKNOWN_TOOL.name -> RejectionReason.UNKNOWN_TOOL
        RejectionReason.UNDECLARED_ARG.name -> RejectionReason.UNDECLARED_ARG
        RejectionReason.MISSING_REQUIRED_ARG.name -> RejectionReason.MISSING_REQUIRED_ARG
        RejectionReason.FORWARD_ARG_SOURCE.name -> RejectionReason.FORWARD_ARG_SOURCE
        RejectionReason.UNDECLARED_OUTPUT.name -> RejectionReason.UNDECLARED_OUTPUT
        RejectionReason.UNRESOLVED_ARG_SOURCE.name -> RejectionReason.UNRESOLVED_ARG_SOURCE
        else -> throw CorruptAgentRowException("unknown rejection reason \"$raw\"")
    }

    private fun readConsentReason(raw: String): ConsentReason = when (raw) {
        ConsentReason.RISK_LEVEL.name -> ConsentReason.RISK_LEVEL
        ConsentReason.RISK_RAISED.name -> ConsentReason.RISK_RAISED
        ConsentReason.MISSING_PERMISSION.name -> ConsentReason.MISSING_PERMISSION
        ConsentReason.DURABLE_EFFECT.name -> ConsentReason.DURABLE_EFFECT
        else -> throw CorruptAgentRowException("unknown consent reason \"$raw\"")
    }
}
