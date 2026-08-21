package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.AgentSessionDao
import com.sidr.launcher.data.repository.db.entity.AgentPlanStepEntity
import com.sidr.launcher.data.repository.db.entity.AgentSessionEntity
import com.sidr.launcher.data.repository.db.entity.AgentTraceEventEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The SQL-level contract of the agent session tables (A0 Task 10).
 *
 * Two properties are load-bearing and are tested here rather than in the store, because they are
 * decided by the schema and not by Kotlin:
 *  - **the conditional consent write.** Every clause of `recordConsentIfPending`'s `WHERE` has its own
 *    test, so dropping any one of them turns exactly one test red rather than none.
 *  - **the cascade.** `delete` deletes the session row only; steps and trace must follow. The trace is
 *    seeded here on purpose — asserting an empty child table that was never populated proves nothing.
 *
 * `args_json` is seeded in its `ArgSource` form (F6), not with resolved values: the shape this test
 * pins is the shape a resumed plan re-binds from. The exact encoding is pinned against the mapper in
 * `AgentSessionMappersTest`, so the literals below and the mapper cannot drift apart silently.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AgentSessionDaoTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: AgentSessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.agentSessionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedAwaitingConsent(id: String = "s1") {
        dao.upsertSession(
            AgentSessionEntity(
                id = id, goalText = "открой убер", goalShape = "AppNotInstalled",
                goalShapeArg = "убер", state = "AwaitingConsent", cursor = 1, createdAt = 1L,
            ),
        )
        dao.upsertSteps(
            listOf(
                AgentPlanStepEntity(
                    sessionId = id, stepIndex = 0, toolId = "launch_app",
                    argsJson = """{"query":{"kind":"literal","value":"убер"}}""", risk = "SAFE",
                    preconditionFact = null, rationale = "GOAL_DIRECT",
                    observationType = "Observed", observationFact = "APP_NOT_INSTALLED",
                    observationOutputJson = """{"resolved_query":"убер"}""", consent = null,
                ),
                AgentPlanStepEntity(
                    sessionId = id, stepIndex = 1, toolId = "play_store_search",
                    argsJson = """{"query":{"kind":"from_step","stepIndex":0,"key":"resolved_query"}}""",
                    risk = "CONFIRM", preconditionFact = "APP_NOT_INSTALLED",
                    rationale = "APP_NOT_INSTALLED_FALLBACK",
                    observationType = null, observationFact = null,
                    observationOutputJson = null, consent = null,
                ),
            ),
        )
        dao.upsertTrace(
            listOf(
                AgentTraceEventEntity(id, seq = 0, type = "PlanCreated", stepIndex = null, detail = "2", at = 10L),
                AgentTraceEventEntity(id, seq = 1, type = "ConsentRequested", stepIndex = 1, detail = "RISK_LEVEL", at = 11L),
            ),
        )
    }

    @Test
    fun `the first consent write applies`() = runTest {
        seedAwaitingConsent()

        assertEquals(1, dao.recordConsentIfPending("s1", 1, granted = true))
        assertEquals(true, dao.stepsFor("s1").first { it.stepIndex == 1 }.consent)
    }

    @Test
    fun `a second consent write for the same step applies to nothing`() = runTest {
        seedAwaitingConsent()
        dao.recordConsentIfPending("s1", 1, granted = true)

        assertEquals(0, dao.recordConsentIfPending("s1", 1, granted = true))
    }

    @Test
    fun `consent cannot be written while the session is not awaiting it`() = runTest {
        seedAwaitingConsent()
        dao.updateState("s1", "Running")

        assertEquals(0, dao.recordConsentIfPending("s1", 1, granted = true))
        assertNull(dao.stepsFor("s1").first { it.stepIndex == 1 }.consent)
    }

    @Test
    fun `a consent write touches only the step it names`() = runTest {
        seedAwaitingConsent()

        assertEquals(1, dao.recordConsentIfPending("s1", 1, granted = true))
        assertNull(
            "step 0 was not the step awaiting consent and must be untouched",
            dao.stepsFor("s1").first { it.stepIndex == 0 }.consent,
        )
    }

    @Test
    fun `a consent write for a session that does not exist applies to nothing`() = runTest {
        seedAwaitingConsent(id = "s1")

        assertEquals(0, dao.recordConsentIfPending("other", 1, granted = true))
        assertNull(dao.stepsFor("s1").first { it.stepIndex == 1 }.consent)
    }

    /**
     * The `session_id` clause carries its own weight, and this is the only test that shows it. The
     * store keeps the table to one session, but that is enforced by `replaceSession` and not by the
     * schema — so at the DAO level two sessions can coexist, and a consent granted to one of them must
     * not be written onto the other's step. Seeded through the DAO deliberately, bypassing the store,
     * because the store is exactly the layer whose invariant is not being trusted here.
     */
    @Test
    fun `a consent write does not reach an identically-numbered step of another session`() = runTest {
        seedAwaitingConsent(id = "s1")
        seedAwaitingConsent(id = "s2")
        dao.updateState("s2", "Running")

        assertEquals(1, dao.recordConsentIfPending("s1", 1, granted = true))

        assertEquals(true, dao.stepsFor("s1").first { it.stepIndex == 1 }.consent)
        assertNull(
            "step 1 of the other session must be untouched",
            dao.stepsFor("s2").first { it.stepIndex == 1 }.consent,
        )
    }

    @Test
    fun `deleting the session cascades to steps and trace`() = runTest {
        seedAwaitingConsent()
        // Without these the cascade assertions below would hold over tables that were never filled.
        assertEquals(2, dao.stepsFor("s1").size)
        assertEquals(2, dao.traceFor("s1").size)

        dao.deleteSession("s1")

        assertNull(dao.activeSession())
        assertEquals(0, dao.stepsFor("s1").size)
        assertEquals(0, dao.traceFor("s1").size)
    }

    /**
     * "Produced nothing" and "produced an empty map" are different facts, and the column is what keeps
     * them different — asserted here, at the SQL level, rather than through the mapper's default.
     */
    @Test
    fun `a step with no output stores NULL and an empty output stores an empty object`() = runTest {
        seedAwaitingConsent()
        dao.upsertSteps(
            listOf(
                AgentPlanStepEntity(
                    sessionId = "s1", stepIndex = 1, toolId = "play_store_search",
                    argsJson = """{"query":{"kind":"from_step","stepIndex":0,"key":"resolved_query"}}""",
                    risk = "CONFIRM", preconditionFact = "APP_NOT_INSTALLED",
                    rationale = "APP_NOT_INSTALLED_FALLBACK",
                    observationType = "Effected", observationFact = null,
                    observationOutputJson = "{}", consent = true,
                ),
            ),
        )

        val steps = dao.stepsFor("s1")
        assertNull(
            "a step that has not run yet produced nothing",
            steps.first { it.stepIndex == 0 }.consent,
        )
        assertEquals("{}", steps.first { it.stepIndex == 1 }.observationOutputJson)
        assertNotNull(steps.first { it.stepIndex == 0 }.observationOutputJson)
    }

    @Test
    fun `activeSession is null on an empty database`() = runTest {
        assertNull(dao.activeSession())
    }
}
