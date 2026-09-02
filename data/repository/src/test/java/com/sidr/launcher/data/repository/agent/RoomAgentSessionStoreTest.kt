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
     * The mapper's `Free` arm. It **refused** until A1' Task 9 composed a planner that produces
     * `GoalShape.Free` on Android, at which point the refusal stopped protecting an absent consumer
     * and started killing the block's headline capability in silence; this test replaces the one that
     * pinned the refusal (`saving a free-text goal is a contained Failure that writes no row`).
     *
     * It is deliberately at least as strong as the test it replaces, which was itself written after a
     * mutation survived (fix round 2, 2026-08-23 — the arm shipped held by nothing and a mutation
     * persisting a `Free` goal under the `AppNotInstalled` string went unnoticed, so `readShape`
     * decoded it back as the **wrong shape**). That exact mutation is still red here: the assertion is
     * on the restored `GoalShape`, so encoding `Free` under the other discriminator fails, and so does
     * a decode arm that reads `Free` back as `AppNotInstalled`.
     *
     * The goal's `text` and the shape's `text` are **different** on purpose. They are independent
     * fields — today's one construction site (`RouteCommandUseCase` step 2b) sets them equal, but the
     * type does not — so this pins that `goal_shape_arg` carries the shape's own argument rather than
     * being reconstructed from `goal_text` on the way back. A mapper that stored the shape empty and
     * rebuilt it from the goal text passes a same-text round-trip and fails this one.
     */
    @Test
    fun `a free-text goal round-trips with its own shape and its own text`() = runTest {
        val free = session().copy(
            goal = AgentGoal(text = "сделай конспект встречи", shape = GoalShape.Free("сделай конспект")),
        )

        assertTrue(store.save(free) is OperationResult.Success)

        val restored = restored()
        assertEquals(free, restored)
        assertEquals(GoalShape.Free("сделай конспект"), restored?.goal?.shape)
        assertEquals("сделай конспект встречи", restored?.goal?.text)
    }

    /**
     * The half of the replaced test that had nothing to do with `Free`, kept alive after its throwing
     * arm was removed: **`guarded` contains a write failure**, so the hard rule "never throw to UI"
     * rests on a test and not on a KDoc sentence. A narrowed catch list in `guarded`, or a `save` that
     * stopped going through it, breaks this test instead of reaching a user as a crash.
     *
     * The failure is planted in the clock rather than in the database, and the honest reason is that
     * an in-memory Room database cannot be taken away: `db.close()` followed by a write simply
     * recreates it and the save succeeds (measured, not assumed — that was this test's first shape).
     * A throwing `now()` is a real throw from inside the block `guarded` wraps, and it pins one more
     * thing besides containment: that `save` reads the clock **inside** `guarded`. A `now()` hoisted
     * out of the try — the natural refactor when someone wants one stamp for two calls — would throw
     * straight to the caller, and this test is what says so.
     */
    @Test
    fun `a write that throws is a contained Failure, not a throw to the caller`() = runTest {
        val toSave = session()
        val failing = RoomAgentSessionStore(dao, Dispatchers.Unconfined) { error("no clock") }

        assertEquals(
            OperationResult.Failure(OperationError.UnknownError("db_agent_session_write_failed")),
            failing.save(toSave),
        )
        assertNull("a failed write must leave no row behind", dao.activeSession())
    }
}
