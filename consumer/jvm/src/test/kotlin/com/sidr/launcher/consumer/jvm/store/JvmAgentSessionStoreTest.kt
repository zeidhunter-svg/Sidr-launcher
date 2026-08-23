package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
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
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JvmAgentSessionStoreTest {

    @get:Rule val temp = TemporaryFolder()

    private fun store() = JvmAgentSessionStore(temp.root.toPath().resolve("session.json"))

    private val id = AgentSessionId("s-1")

    private fun session(
        state: ExecutionState = ExecutionState.Running,
        cursor: Int = 0,
        observations: Map<Int, ToolResult> = emptyMap(),
        consents: Map<Int, Boolean> = emptyMap(),
        trace: ExecutionTrace = ExecutionTrace(listOf(TraceEvent.PlanCreated(3))),
    ) = AgentSession(
        id = id,
        goal = AgentGoal("remove stale.lock", GoalShape.Free("remove stale.lock")),
        plan = ExecutionPlan(
            listOf(
                PlanStep(0, ToolInvocation(SandboxToolIds.WORKSPACE_INFO), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT),
                PlanStep(
                    1,
                    ToolInvocation(
                        SandboxToolIds.FIND_FILE,
                        mapOf(
                            SandboxKeys.QUERY to ArgSource.Literal("stale.lock"),
                            SandboxKeys.ROOT to ArgSource.FromStep(0, SandboxKeys.ROOT),
                        ),
                    ),
                    ActionRiskLevel.SAFE,
                    StepPrecondition.None,
                    StepRationale.GOAL_DIRECT,
                ),
                PlanStep(
                    2,
                    ToolInvocation(SandboxToolIds.DELETE_FILE, mapOf(SandboxKeys.PATH to ArgSource.FromStep(1, SandboxKeys.RESOLVED_PATH))),
                    ActionRiskLevel.DANGEROUS,
                    StepPrecondition.None,
                    StepRationale.GOAL_DIRECT,
                ),
            ),
        ),
        cursor = cursor,
        state = state,
        observations = observations,
        consents = consents,
        trace = trace,
    )

    private fun <T> OperationResult<T>.value(): T = (this as OperationResult.Success).value

    @Test
    fun `an empty store has no active session`() = runTest {
        assertNull(store().active().value())
    }

    @Test
    fun `a saved session round-trips byte-for-byte through the file`() = runTest {
        val original = session(
            state = ExecutionState.AwaitingConsent,
            cursor = 2,
            observations = mapOf(
                0 to ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.ROOT to "/tmp/box"))),
                1 to ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.RESOLVED_PATH to "/tmp/box/stale.lock"))),
            ),
            consents = mapOf(1 to true),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.PlanCreated(3),
                    TraceEvent.StepStarted(0),
                    TraceEvent.ToolInvoked(0, SandboxToolIds.WORKSPACE_INFO),
                    TraceEvent.ToolObserved(0, ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.ROOT to "/tmp/box")))),
                    TraceEvent.StepSkipped(1, StepPrecondition.None),
                    TraceEvent.ConsentRequested(2, ConsentReason.RISK_LEVEL),
                    TraceEvent.SessionPaused,
                    TraceEvent.SessionResumed,
                ),
            ),
        )
        val s = store()
        s.save(original)
        assertEquals(original, s.active().value())
    }

    /**
     * The Android mapper drops a persisted `Failed`'s `CommandFailure` variant and restores it as
     * `Generic` — a named A0 gap. This consumer preserves it, which shows the loss is a **mapper
     * choice**, not a limitation of the contract. Useful to A4' and recorded in the ADR.
     */
    @Test
    fun `a persisted Failed keeps its CommandFailure variant here`() = runTest {
        val s = store()
        s.save(session(observations = mapOf(0 to ToolResult.Failed(CommandFailure.NoStoreApp))))
        assertEquals(
            ToolResult.Failed(CommandFailure.NoStoreApp),
            s.active().value()!!.observations[0],
        )
    }

    @Test
    fun `delete leaves the store empty - no history at rest`() = runTest {
        val s = store()
        s.save(session())
        s.delete(id)
        assertNull(s.active().value())
    }

    @Test
    fun `recordConsentIfPending applies once when the session awaits a decision`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))
        assertTrue(s.recordConsentIfPending(id, 2, granted = true).value())
        assertEquals(mapOf(2 to true), s.active().value()!!.consents)
    }

    @Test
    fun `recordConsentIfPending does not apply twice for the same step`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))
        assertTrue(s.recordConsentIfPending(id, 2, granted = true).value())
        assertTrue("a second decision must not apply", !s.recordConsentIfPending(id, 2, granted = false).value())
        assertEquals("the first decision stands", mapOf(2 to true), s.active().value()!!.consents)
    }

    @Test
    fun `recordConsentIfPending does not apply when the session is not awaiting`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.Running, cursor = 2))
        assertTrue(!s.recordConsentIfPending(id, 2, granted = true).value())
    }

    @Test
    fun `recordConsentIfPending does not apply to a different session id`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))
        assertTrue(!s.recordConsentIfPending(AgentSessionId("other"), 2, granted = true).value())
    }

    /**
     * **The property this whole task exists to test.** `recordConsentIfPending` is a compare-and-set
     * contract that exists because Room can express `UPDATE … WHERE state = pending` in one statement.
     * Whether a file store can honour it *honestly* — rather than by weakening it to a whole-object
     * write — is what decides whether `AgentSessionStore` is a portable port or a Room shape wearing an
     * interface (spec §8, §11.2).
     *
     * Two taps that race must not both win. The whole-object-write pattern is what already cost this
     * project the `autoHideNavBar` bug (DS-11), which only surfaced on device.
     */
    @Test
    fun `two concurrent consent writes for the same step - exactly one applies`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))

        val results = withContext(Dispatchers.Default) {
            List(2) { async { s.recordConsentIfPending(id, 2, granted = true) } }.awaitAll()
        }

        assertEquals(
            "exactly one of two racing consent writes may apply",
            1,
            results.count { it.value() },
        )
        assertEquals(mapOf(2 to true), s.active().value()!!.consents)
    }
}
