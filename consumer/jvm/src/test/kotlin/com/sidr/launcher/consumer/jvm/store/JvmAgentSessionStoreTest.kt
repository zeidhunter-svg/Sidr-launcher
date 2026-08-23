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
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.RejectionReason
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

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
        precondition: StepPrecondition = StepPrecondition.None,
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
                    precondition,
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
    fun `a saved session round-trips - every TraceEvent variant and every ToolResult`() = runTest {
        val original = session(
            state = ExecutionState.AwaitingConsent,
            cursor = 2,
            observations = mapOf(
                0 to ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.ROOT to "/tmp/box"))),
                1 to ToolResult.Observed(
                    ObservedFact.APP_AMBIGUOUS,
                    ToolOutput(mapOf(SandboxKeys.RESOLVED_PATH to "/tmp/box/stale.lock")),
                ),
                2 to ToolResult.Failed(CommandFailure.CantOpenUrl),
            ),
            consents = mapOf(1 to true, 2 to false),
            precondition = StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.PlanCreated(3),
                    TraceEvent.StepStarted(0),
                    TraceEvent.ToolInvoked(0, SandboxToolIds.WORKSPACE_INFO),
                    TraceEvent.ToolObserved(0, ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.ROOT to "/tmp/box")))),
                    TraceEvent.ToolObserved(
                        1,
                        ToolResult.Observed(
                            ObservedFact.APP_AMBIGUOUS,
                            ToolOutput(mapOf(SandboxKeys.RESOLVED_PATH to "/tmp/box/stale.lock")),
                        ),
                    ),
                    TraceEvent.ToolObserved(2, ToolResult.Failed(CommandFailure.CantOpenUrl)),
                    TraceEvent.StepSkipped(1, StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED)),
                    TraceEvent.StepRejected(1, RejectionReason.UNRESOLVED_ARG_SOURCE),
                    TraceEvent.ConsentRequested(2, ConsentReason.RISK_LEVEL),
                    TraceEvent.ConsentResolved(2, granted = false),
                    TraceEvent.SessionPaused,
                    TraceEvent.SessionResumed,
                    TraceEvent.SessionEnded(ExecutionState.Cancelled),
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

    /**
     * Scoped to what this class actually decides. "No history at rest" is a property of
     * `RunAgentSessionUseCase`, which saves the terminal session and *then* deletes it; nothing here
     * inspects `ExecutionState.isTerminal`, and this test hands `delete` a `Running` session. What is
     * verified is the half the store owns: after [JvmAgentSessionStore.delete] there is no file, and
     * [JvmAgentSessionStore.active] reports no session. Renamed after the 2026-08-24 review (F4), which
     * caught the old name claiming the use-case's property as this class's.
     */
    @Test
    fun `after delete the file is gone and active reports no session`() = runTest {
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

    /**
     * The gap the first round left open, and the reason it is not merely cosmetic.
     *
     * A [Mutex] held in a per-instance field excludes coroutines sharing **one** store object; a
     * [java.nio.channels.FileLock] excludes a **second process**. Neither covers two store objects over
     * one file inside one JVM — and `FileChannel.lock()` is JVM-wide, so the second acquisition throws
     * `OverlappingFileLockException` rather than blocking. The CAS still failed closed, but the loser
     * was handed `OperationResult.Failure(UnknownError(null))` instead of `Success(false)`.
     *
     * That distinction is the whole contract. `recordConsentIfPending` returns `true` iff it applied,
     * so "another tap won" is `Success(false)`; a `Failure` says "the store broke" and propagates as a
     * technical error, which is precisely the weakening spec §8 asks whether a file store can avoid.
     * Asserting the **type** and not only the boolean is deliberate: a test that read only the boolean
     * would have passed against the defect.
     */
    @Test
    fun `two instances over one file - the loser is told false, not handed a failure`() = runTest {
        val file = temp.root.toPath().resolve("session.json")
        JvmAgentSessionStore(file).save(session(state = ExecutionState.AwaitingConsent, cursor = 2))
        val stores = listOf(JvmAgentSessionStore(file), JvmAgentSessionStore(file))

        val results = withContext(Dispatchers.Default) {
            List(2) { i -> async { stores[i].recordConsentIfPending(id, 2, granted = true) } }.awaitAll()
        }

        assertTrue(
            "a losing consent write must be Success(false), never a Failure — got $results",
            results.all { it is OperationResult.Success },
        )
        assertEquals(
            "exactly one of two instances racing one file may apply",
            1,
            results.count { it.value() },
        )
        assertEquals(mapOf(2 to true), stores[0].active().value()!!.consents)
    }

    /**
     * **A refusal must persist as a refusal.** Every other test in this class records `granted = true`,
     * and the one `false` call is the one the CAS deliberately rejects — so it never reaches the write.
     * That left the *value* asserted by nothing: a store that wrote `stepIndex to true` unconditionally
     * passed the entire suite, including every test with "consent" in its name.
     *
     * What that costs, concretely: the user refuses the `DANGEROUS` `delete_file` step, the store
     * records a grant, `AgentExecutor.prepare` reads `consents[2] == true`, and the file is deleted on
     * a decision the user made the other way. Found by the 2026-08-24 fresh-eyes review (F1).
     */
    @Test
    fun `a refused consent is persisted as false, not merely as decided`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))

        assertTrue("the refusal itself applies", s.recordConsentIfPending(id, 2, granted = false).value())
        assertEquals(
            "a refusal must come back a refusal — a grant here executes what the user declined",
            mapOf(2 to false),
            s.active().value()!!.consents,
        )
    }

    /**
     * Parity with the Room sibling. Its DAO says `UPDATE … WHERE step_index = :stepIndex`, which matches
     * no row for a step the plan does not have, so Android answers `false`. Without the same check the
     * file store persisted a phantom `consents[99] = true` and answered `true` — after which
     * `ResolveConsentUseCase` flips the session to `Running` on a decision that decided nothing.
     *
     * On-topic for this task rather than incidental: the block's question is whether
     * `AgentSessionStore` is a portable **port**, and two implementations answering differently for the
     * same call is exactly what that question is about. Found by the 2026-08-24 review (F6).
     */
    /**
     * **A planted sentinel, because F5 was a real leak and not a style point.** `guarded` used to build
     * its `OperationError` from the caught exception's own `message`, and kotlinx-serialization appends
     * the offending *input* to a `JsonDecodingException` — so a half-written `session.json` carried the
     * user's raw goal text and workspace paths out inside an `OperationError`, which the surface is
     * free to log or display. `CommandFailure`'s rule is explicit: "display-safe by construction, so no
     * stack trace or PII can ride along". `RoomAgentSessionStore.guarded` takes a fixed token for this
     * exact reason; this asserts the file store now does the same.
     *
     * It also pins F8's other half: an undecodable file is **deleted** before the failure is reported,
     * mirroring the Room store's F10 fix. An unreadable recovery record is not a recovery record —
     * nothing can resume or show it — so left alone it would sit on disk holding the goal text.
     */
    @Test
    fun `an undecodable file is deleted, and the failure names a token rather than its content`() = runTest {
        val sentinel = "SENTINEL-goal-must-not-leak-7f3a"
        val file = temp.root.toPath().resolve("session.json")
        val s = JvmAgentSessionStore(file)
        s.save(session().copy(goal = AgentGoal(sentinel, GoalShape.Free(sentinel))))

        val whole = Files.readString(file)
        assertTrue("the fixture must really plant the sentinel", whole.contains(sentinel))
        val truncated = whole.take(whole.length / 2)
        assertTrue("the truncated file must still carry it, or this proves nothing", truncated.contains(sentinel))
        Files.writeString(file, truncated)

        val outcome = s.active()
        assertTrue("an undecodable file is a Failure, never a guessed session", outcome is OperationResult.Failure)
        val reason = ((outcome as OperationResult.Failure).error as OperationError.UnknownError).reason
        assertEquals("file_agent_session_corrupt", reason)
        assertFalse(
            "no persisted content may ride out inside an OperationError — got: $reason",
            reason!!.contains(sentinel),
        )
        assertFalse(
            "an unreadable recovery record must not sit on disk holding the goal text",
            Files.exists(file),
        )
    }

    @Test
    fun `recordConsentIfPending does not apply to a step the plan does not have`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))

        assertTrue(!s.recordConsentIfPending(id, 99, granted = true).value())
        assertEquals(emptyMap<Int, Boolean>(), s.active().value()!!.consents)
    }
}
