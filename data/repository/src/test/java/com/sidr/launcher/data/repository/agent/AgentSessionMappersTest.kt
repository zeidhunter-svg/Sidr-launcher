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
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The mapper's exhaustiveness, tested without a database because the mapper is pure.
 *
 * The **encoding** direction is exhaustive by compiler — every `when` out is over a sealed type or an
 * enum. The **decoding** direction cannot be: it is a `when` over a string. These tests are that
 * direction's mechanism. Each one is driven by `values()` (or, for `TraceEvent`, by the sealed
 * hierarchy itself), so a constant or a variant added without a matching branch in the mapper turns a
 * test red instead of quietly becoming an unreadable row on someone's device.
 */
class AgentSessionMappersTest {

    private val observed = ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED, ToolOutput(mapOf("k" to "v")))

    private fun sessionRow(state: String = ExecutionState.Running.name) = AgentSessionEntity(
        id = "s1", goalText = "открой убер", goalShape = "AppNotInstalled", goalShapeArg = "убер",
        state = state, cursor = 0, createdAt = 1L,
    )

    private fun stepRow(
        index: Int = 0,
        risk: String = ActionRiskLevel.SAFE.name,
        rationale: String = StepRationale.GOAL_DIRECT.name,
        preconditionFact: String? = null,
        observationType: String? = null,
        observationFact: String? = null,
        observationOutputJson: String? = null,
    ) = AgentPlanStepEntity(
        sessionId = "s1", stepIndex = index, toolId = "launch_app",
        argsJson = """{"query":{"kind":"literal","value":"убер"}}""",
        risk = risk, preconditionFact = preconditionFact, rationale = rationale,
        observationType = observationType, observationFact = observationFact,
        observationOutputJson = observationOutputJson, consent = null,
    )

    private fun traceRow(type: String, stepIndex: Int? = null, detail: String? = null) =
        AgentTraceEventEntity("s1", seq = 0, type = type, stepIndex = stepIndex, detail = detail, at = 1L)

    private fun read(
        session: AgentSessionEntity = sessionRow(),
        steps: List<AgentPlanStepEntity> = listOf(stepRow()),
        trace: List<AgentTraceEventEntity> = emptyList(),
    ) = AgentSessionMappers.toDomain(session, steps, trace)

    // ------------------------------------------------------------------ the encoded arg form

    /**
     * Pins the exact on-disk form of both `ArgSource` variants. `AgentSessionDaoTest` seeds these
     * strings by hand; this is what keeps the two from drifting apart without anything noticing.
     */
    @Test
    fun `an ArgSource encodes to its documented form`() {
        assertEquals(
            """{"a":{"kind":"literal","value":"убер"},"b":{"kind":"from_step","stepIndex":0,"key":"resolved_query"}}""",
            AgentSessionMappers.encodeArgs(
                mapOf(
                    "a" to ArgSource.Literal("убер"),
                    "b" to ArgSource.FromStep(0, "resolved_query"),
                ),
            ),
        )
    }

    @Test
    fun `both ArgSource variants survive the round trip`() {
        val args = mapOf("a" to ArgSource.Literal("убер"), "b" to ArgSource.FromStep(0, "resolved_query"))
        val row = stepRow().copy(argsJson = AgentSessionMappers.encodeArgs(args))

        assertEquals(args, read(steps = listOf(row)).plan.steps.single().invocation.args)
    }

    @Test
    fun `an unknown ArgSource kind is corrupt, not a default`() {
        val row = stepRow().copy(argsJson = """{"query":{"kind":"whatever","value":"убер"}}""")

        assertThrows(CorruptAgentRowException::class.java) { read(steps = listOf(row)) }
    }

    @Test
    fun `malformed args json is corrupt, not an empty argument map`() {
        val row = stepRow().copy(argsJson = "not json")

        assertThrows(CorruptAgentRowException::class.java) { read(steps = listOf(row)) }
    }

    // ------------------------------------------------------------------ enums, driven by values()

    @Test
    fun `every ExecutionState round-trips`() {
        ExecutionState.entries.forEach { state ->
            assertEquals(state, read(session = sessionRow(state = state.name)).state)
        }
    }

    @Test
    fun `every ActionRiskLevel round-trips`() {
        ActionRiskLevel.entries.forEach { risk ->
            assertEquals(risk, read(steps = listOf(stepRow(risk = risk.name))).plan.steps.single().risk)
        }
    }

    @Test
    fun `every StepRationale round-trips`() {
        StepRationale.entries.forEach { rationale ->
            assertEquals(
                rationale,
                read(steps = listOf(stepRow(rationale = rationale.name))).plan.steps.single().rationale,
            )
        }
    }

    @Test
    fun `every ObservedFact round-trips as a precondition and as an observation`() {
        ObservedFact.entries.forEach { fact ->
            val row = stepRow(
                preconditionFact = fact.name,
                observationType = "Observed",
                observationFact = fact.name,
                observationOutputJson = "{}",
            )
            val restored = read(steps = listOf(row))

            assertEquals(StepPrecondition.PreviousStepObserved(fact), restored.plan.steps.single().precondition)
            assertEquals(ToolResult.Observed(fact, ToolOutput(emptyMap())), restored.observations[0])
        }
    }

    @Test
    fun `every RejectionReason round-trips through a StepRejected event`() {
        RejectionReason.entries.forEach { reason ->
            assertEquals(
                TraceEvent.StepRejected(0, reason),
                read(trace = listOf(traceRow("StepRejected", 0, reason.name))).trace.events.single(),
            )
        }
    }

    @Test
    fun `every ConsentReason round-trips through a ConsentRequested event`() {
        ConsentReason.entries.forEach { reason ->
            assertEquals(
                TraceEvent.ConsentRequested(0, reason),
                read(trace = listOf(traceRow("ConsentRequested", 0, reason.name))).trace.events.single(),
            )
        }
    }

    // ------------------------------------------------------------------ the trace vocabulary

    /**
     * One instance of every [TraceEvent] variant. [`the sample covers every TraceEvent variant`] is
     * what keeps this list honest: adding a variant to the sealed interface without adding it here
     * fails that test, and adding it here without teaching the mapper to read it fails this one.
     */
    private val oneOfEachEvent = listOf(
        TraceEvent.PlanCreated(stepCount = 2),
        TraceEvent.StepStarted(0),
        TraceEvent.StepSkipped(1, StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED)),
        TraceEvent.StepRejected(1, RejectionReason.UNRESOLVED_ARG_SOURCE),
        TraceEvent.ConsentRequested(1, ConsentReason.RISK_LEVEL),
        TraceEvent.ConsentResolved(1, granted = true),
        TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
        TraceEvent.ToolObserved(0, observed),
        TraceEvent.SessionPaused,
        TraceEvent.SessionResumed,
        TraceEvent.SessionEnded(ExecutionState.Completed),
    )

    private fun sessionWith(events: List<TraceEvent>) = AgentSession(
        id = AgentSessionId("s1"),
        goal = AgentGoal("открой убер", GoalShape.AppNotInstalled("убер")),
        plan = ExecutionPlan(
            listOf(
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("убер"))),
                    risk = ActionRiskLevel.SAFE,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.GOAL_DIRECT,
                ),
                PlanStep(
                    index = 1,
                    invocation = ToolInvocation(
                        ToolIds.PLAY_STORE_SEARCH,
                        mapOf("query" to ArgSource.FromStep(0, "resolved_query")),
                    ),
                    risk = ActionRiskLevel.CONFIRM,
                    precondition = StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
                    rationale = StepRationale.APP_NOT_INSTALLED_FALLBACK,
                ),
            ),
        ),
        cursor = 1,
        state = ExecutionState.Running,
        observations = mapOf(0 to observed),
        consents = mapOf(1 to true),
        trace = ExecutionTrace(events),
    )

    @Test
    fun `every TraceEvent variant round-trips`() {
        val original = sessionWith(oneOfEachEvent)

        val restored = AgentSessionMappers.toDomain(
            AgentSessionMappers.toSessionEntity(original, now = 1L),
            AgentSessionMappers.toStepEntities(original),
            AgentSessionMappers.toTraceEntities(original, now = 1L),
        )

        assertEquals(original, restored)
    }

    /**
     * The tripwire for the list above. `sealedSubclasses` is reflection over a sealed hierarchy the
     * compiler already knows about, so this cannot go stale the way a hand-maintained count can.
     *
     * **Coverage is compared by class, not by name** (review finding F9, 2026-08-23). It used to
     * compare `simpleName` against `typeNameOf`, which quietly required the on-disk vocabulary to
     * equal the Kotlin class name for ever — the exact coupling `typeNameOf`'s KDoc says the longhand
     * exists to avoid ("a rename is then a migration decision, not a silent format change"). Renaming
     * a variant while keeping its persisted discriminator would have turned this red on correct code,
     * and the natural fix — edit `typeNameOf` to match — is the silent format change itself.
     */
    @Test
    fun `the sample covers every TraceEvent variant`() {
        assertEquals(
            TraceEvent::class.sealedSubclasses.toSet(),
            oneOfEachEvent.map { it::class }.toSet(),
        )
    }

    @Test
    fun `a skipped step with no precondition round-trips as None`() {
        val original = sessionWith(listOf(TraceEvent.StepSkipped(0, StepPrecondition.None)))

        val restored = AgentSessionMappers.toDomain(
            AgentSessionMappers.toSessionEntity(original, now = 1L),
            AgentSessionMappers.toStepEntities(original),
            AgentSessionMappers.toTraceEntities(original, now = 1L),
        )

        assertEquals(original.trace, restored.trace)
    }

    @Test
    fun `an unknown trace type is corrupt, not a dropped event`() {
        assertThrows(CorruptAgentRowException::class.java) { read(trace = listOf(traceRow("Whatever", 0, null))) }
    }

    @Test
    fun `an unknown goal shape is corrupt, not the only shape A0 happens to have`() {
        assertThrows(CorruptAgentRowException::class.java) {
            read(session = sessionRow().copy(goalShape = "SomethingElse"))
        }
    }

    /**
     * `ExecutionPlan` requires `steps[i].index == i` and `AgentExecutor` depends on it in three
     * places. A row set that violates it must fail the read rather than build a plan the engine will
     * silently truncate.
     */
    @Test
    fun `a step whose index does not match its position is corrupt`() {
        assertThrows(CorruptAgentRowException::class.java) { read(steps = listOf(stepRow(index = 1))) }
    }

    @Test
    fun `an Observed row without a fact is corrupt`() {
        assertThrows(CorruptAgentRowException::class.java) {
            read(steps = listOf(stepRow(observationType = "Observed", observationOutputJson = "{}")))
        }
    }

    @Test
    fun `an Effected row without an output object is corrupt`() {
        assertThrows(CorruptAgentRowException::class.java) {
            read(steps = listOf(stepRow(observationType = "Effected")))
        }
    }

    /** The named fidelity gap (A0 spec §7), pinned at the mapper it lives on. */
    @Test
    fun `a Failed observation restores as Generic because the variant is not persisted`() {
        assertEquals(
            ToolResult.Failed(CommandFailure.Generic),
            read(steps = listOf(stepRow(observationType = "Failed"))).observations[0],
        )
    }

    @Test
    fun `a ToolInvoked event keeps its tool id`() {
        assertEquals(
            TraceEvent.ToolInvoked(0, ToolId("play_store_search")),
            read(trace = listOf(traceRow("ToolInvoked", 0, "play_store_search"))).trace.events.single(),
        )
    }
}
