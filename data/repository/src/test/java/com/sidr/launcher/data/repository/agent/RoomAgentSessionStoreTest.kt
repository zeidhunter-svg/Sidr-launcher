package com.sidr.launcher.data.repository.agent

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.data.repository.db.SidrDatabase
import com.sidr.launcher.data.repository.db.dao.AgentSessionDao
import com.sidr.launcher.data.repository.db.entity.AgentSessionEntity
import com.sidr.launcher.data.repository.db.entity.AgentTraceEventEntity
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The store's half of the A0 persistence contract: a session survives a process, the plan it comes
 * back as is the plan that was written, and a row that is not readable is a failure rather than a
 * guess.
 *
 * The plan under test is built by the **real** [TemplatePlanner], over `FakeToolRegistry.withA0Tools()`
 * — which mirrors what `SystemIntentToolSource` projects, and is not pinned to it by any test. So what
 * round-trips here is the production plan *shape* — a `Literal` in step 0 and a `FromStep` binding in
 * step 1 — rather than a hand-written stand-in that might be easier to persist than the real thing. The
 * descriptors themselves being a mirror does not weaken that: the F6 assertion below is about the
 * reference surviving as a reference, which holds whatever the arguments are called.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomAgentSessionStoreTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: AgentSessionDao
    private lateinit var store: RoomAgentSessionStore
    private var clock: Long = 100L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.agentSessionDao()
        store = RoomAgentSessionStore(dao, Dispatchers.Unconfined) { clock }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val observedNotInstalled = ToolResult.Observed(
        fact = ObservedFact.APP_NOT_INSTALLED,
        output = ToolOutput(mapOf("resolved_query" to "убер")),
    )

    private val goal = AgentGoal(text = "открой убер", shape = GoalShape.AppNotInstalled("убер"))

    private suspend fun session(
        id: String = "s1",
        state: ExecutionState = ExecutionState.AwaitingConsent,
        cursor: Int = 1,
        observations: Map<Int, ToolResult> = mapOf(0 to observedNotInstalled),
        consents: Map<Int, Boolean> = emptyMap(),
        trace: List<TraceEvent> = listOf(
            TraceEvent.PlanCreated(stepCount = 2),
            TraceEvent.StepStarted(0),
            TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
            TraceEvent.ToolObserved(0, observedNotInstalled),
            TraceEvent.ConsentRequested(1, ConsentReason.RISK_LEVEL),
        ),
    ): AgentSession {
        val planned = TemplatePlanner().plan(goal, FakeToolRegistry.withA0Tools())
        check(planned is PlanningResult.Planned)
        return AgentSession(
            id = AgentSessionId(id),
            goal = goal,
            plan = planned.plan,
            cursor = cursor,
            state = state,
            observations = observations,
            consents = consents,
            trace = ExecutionTrace(trace),
        )
    }

    private suspend fun restored(): AgentSession? {
        val active = store.active()
        assertTrue("expected a readable session, got $active", active is OperationResult.Success)
        return (active as OperationResult.Success).value
    }

    @Test
    fun `a saved session is re-read exactly`() = runTest {
        val original = session(consents = mapOf(1 to true))

        assertTrue(store.save(original) is OperationResult.Success)

        assertEquals(original, restored())
    }

    @Test
    fun `active is null on an empty database`() = runTest {
        assertNull(restored())
    }

    @Test
    fun `delete empties all three tables`() = runTest {
        val original = session()
        store.save(original)

        assertTrue(store.delete(original.id) is OperationResult.Success)

        assertNull(dao.activeSession())
        assertEquals(0, dao.stepsFor("s1").size)
        assertEquals(0, dao.traceFor("s1").size)
    }

    @Test
    fun `consent applies once and not a second time`() = runTest {
        store.save(session())

        val first = store.recordConsentIfPending(AgentSessionId("s1"), 1, granted = true)
        val second = store.recordConsentIfPending(AgentSessionId("s1"), 1, granted = true)

        assertEquals(OperationResult.Success(true), first)
        assertEquals(OperationResult.Success(false), second)
    }

    @Test
    fun `a consent written by the conditional update is visible on the next read`() = runTest {
        store.save(session())

        store.recordConsentIfPending(AgentSessionId("s1"), 1, granted = true)

        assertEquals(mapOf(1 to true), restored()?.consents)
    }

    /**
     * The F6 half this migration existed to get right: the plan on disk holds the **reference**, so a
     * resumed plan re-binds against the observation that survived instead of replaying a value frozen
     * when the plan was written. Asserted on the raw column, because a mapper that stored the resolved
     * value would still round-trip through itself perfectly well.
     */
    @Test
    fun `a binding is stored as a reference and not as the value it resolved to`() = runTest {
        store.save(session())

        val bound = dao.stepsFor("s1").first { it.stepIndex == 1 }.argsJson

        assertEquals("""{"query":{"kind":"from_step","stepIndex":0,"key":"resolved_query"}}""", bound)
        assertFalse("the resolved value must not be frozen into the plan", bound.contains("убер"))
    }

    @Test
    fun `an unrecognised state yields a Failure and not a guessed state`() = runTest {
        store.save(session())
        dao.updateState("s1", "Sleeping")

        assertEquals(
            OperationResult.Failure(OperationError.UnknownError("db_agent_session_corrupt")),
            store.active(),
        )
    }

    @Test
    fun `a ToolObserved event with no observation on its step yields a Failure`() = runTest {
        store.save(session())
        dao.upsertTrace(
            listOf(AgentTraceEventEntity("s1", seq = 5, type = "ToolObserved", stepIndex = 1, detail = null, at = 1L)),
        )

        assertEquals(
            OperationResult.Failure(OperationError.UnknownError("db_agent_session_corrupt")),
            store.active(),
        )
    }

    /**
     * The named fidelity gap (A0 spec §7), pinned so that widening `observation_type` is a deliberate
     * edit rather than something a future reader assumes already happened.
     */
    @Test
    fun `a Failed observation stores no output and restores as Generic`() = runTest {
        store.save(
            session(observations = mapOf(0 to ToolResult.Failed(CommandFailure.NoStoreApp)), trace = emptyList()),
        )

        assertNull(dao.stepsFor("s1").first { it.stepIndex == 0 }.observationOutputJson)
        assertEquals(ToolResult.Failed(CommandFailure.Generic), restored()?.observations?.get(0))
    }

    @Test
    fun `an empty output is not the same as no output`() = runTest {
        store.save(
            session(observations = mapOf(0 to ToolResult.Effected(ToolOutput(emptyMap()))), trace = emptyList()),
        )

        assertEquals("{}", dao.stepsFor("s1").first { it.stepIndex == 0 }.observationOutputJson)
        assertEquals(ToolResult.Effected(ToolOutput(emptyMap())), restored()?.observations?.get(0))
    }

    @Test
    fun `created_at survives a re-save`() = runTest {
        store.save(session())
        clock = 999L
        store.save(session(state = ExecutionState.Running))

        assertEquals(100L, dao.activeSession()?.createdAt)
    }

    @Test
    fun `a trace event keeps the timestamp it was first written with`() = runTest {
        val first = session(trace = listOf(TraceEvent.PlanCreated(stepCount = 2)))
        store.save(first)
        clock = 999L
        store.save(first.copy(trace = ExecutionTrace(first.trace.events + TraceEvent.StepStarted(0))))

        val trace = dao.traceFor("s1")
        assertEquals(listOf(100L, 999L), trace.map { it.at })
    }

    /**
     * A session row whose steps are gone is the two tables disagreeing, and it must fail closed.
     *
     * This is what a `delete` landing between two of the reads used to produce, back when `active()`
     * issued three separate queries: an empty `ExecutionPlan` is constructible, so it assembled into a
     * well-formed `Success` whose plan was empty — and `AgentExecutor.prepare` then finds no step at
     * the cursor and ends the session `Completed`. The run would report success for a goal on which
     * nothing executed and nothing was traced. `loadActive`'s transaction closes the race; this test
     * covers the refusal, which is the half that can actually be checked by breaking it.
     */
    @Test
    fun `a session row without its steps is corrupt, not an empty plan`() = runTest {
        dao.upsertSession(
            AgentSessionEntity(
                id = "s1", goalText = "открой убер", goalShape = "AppNotInstalled",
                goalShapeArg = "убер", state = "Running", cursor = 1, createdAt = 1L,
            ),
        )

        assertEquals(
            OperationResult.Failure(OperationError.UnknownError("db_agent_session_corrupt")),
            store.active(),
        )
    }

    /**
     * A row that cannot be read back is **removed**, not left behind (review finding F10, 2026-08-23).
     *
     * `LauncherAgentSession.restoreOnStart` is the one caller that cleans up after a dead process, and
     * it returns early on a `Failure` — it has no id to delete. So an unreadable row used to sit on
     * disk holding `goal_text`, the user's raw command, waiting for a reader that can never come. The
     * failure is still reported: deleting it is cleanup, not recovery, and a corrupt row never becomes
     * a guessed session.
     */
    @Test
    fun `an unreadable session is deleted, not left on disk with the command text`() = runTest {
        store.save(session())
        // Break exactly one column: a shape name this build has no branch for. Everything else about
        // the row — its steps, its trace, its goal_text — is intact and readable.
        dao.upsertSession(
            AgentSessionEntity(
                id = "s1", goalText = "открой убер", goalShape = "SomethingFromALaterBuild",
                goalShapeArg = "убер", state = "Running", cursor = 1, createdAt = 1L,
            ),
        )

        assertEquals(
            OperationResult.Failure(OperationError.UnknownError("db_agent_session_corrupt")),
            store.active(),
        )

        assertNull("the unreadable row must be gone", dao.activeSession())
        assertEquals("its steps must go with it", 0, dao.stepsFor("s1").size)
        assertEquals("and its trace", 0, dao.traceFor("s1").size)
    }

    /**
     * Non-vacuity for the delete above: a readable session is **not** deleted by being read. Without
     * this, "the tables are empty afterwards" would pass for a store that dropped everything it read.
     */
    @Test
    fun `reading a healthy session does not delete it`() = runTest {
        store.save(session())

        assertNotNull(restored())

        assertNotNull(dao.activeSession())
        assertEquals(2, dao.stepsFor("s1").size)
    }

    /** The store holds one session. Enforced on the way in, not assumed by `activeSession()`'s LIMIT 1. */
    @Test
    fun `saving a second session replaces the first`() = runTest {
        store.save(session(id = "s1"))

        store.save(session(id = "s2", state = ExecutionState.Running))

        assertEquals(AgentSessionId("s2"), restored()?.id)
        assertEquals(0, dao.stepsFor("s1").size)
        assertEquals(0, dao.traceFor("s1").size)
    }

    /**
     * The mapper's `Free` arm, and the three separate things this one assertion holds (fix round 2,
     * 2026-08-23 — the arm shipped held by nothing and survived a mutation that persisted a `Free`
     * goal under the `AppNotInstalled` string, so `readShape` decoded it back as the **wrong shape**).
     *
     * It pins, at once: that the arm refuses at all; that `IllegalArgumentException` was the right
     * choice over `CorruptAgentRowException`, since the latter surfaces as `db_agent_session_corrupt`
     * and would report a healthy database as corrupt; and that `guarded` contains the throw, so the
     * hard rule "never throw to UI" rests on a test rather than on a KDoc sentence. A second caller of
     * `toSessionEntity` outside `guarded`, or a narrowed catch list, breaks this test rather than
     * reaching a user as a crash.
     */
    @Test
    fun `saving a free-text goal is a contained Failure that writes no row`() = runTest {
        val free = session().copy(
            goal = AgentGoal(text = "сделай конспект", shape = GoalShape.Free("сделай конспект")),
        )

        assertEquals(
            OperationResult.Failure(OperationError.UnknownError("db_agent_session_write_failed")),
            store.save(free),
        )
        assertNull("a refused goal must leave no row behind", dao.activeSession())
    }
}
