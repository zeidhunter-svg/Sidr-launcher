package com.sidr.launcher.data.repository.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sidr.launcher.data.repository.db.entity.AgentPlanStepEntity
import com.sidr.launcher.data.repository.db.entity.AgentSessionEntity
import com.sidr.launcher.data.repository.db.entity.AgentTraceEventEntity

/**
 * The three tables of one session, read together. A holder rather than three call sites, because the
 * point of it is that the three reads happened inside one transaction — see [AgentSessionDao.loadActive].
 */
data class AgentSessionRows(
    val session: AgentSessionEntity,
    val steps: List<AgentPlanStepEntity>,
    val trace: List<AgentTraceEventEntity>,
)

/**
 * SQL for the agent session (A0 Task 10). An abstract class rather than an interface because
 * [replaceSession] and [loadActive] need a body inside `@Transaction`.
 */
@Dao
abstract class AgentSessionDao {

    @Query("SELECT * FROM agent_session LIMIT 1")
    abstract suspend fun activeSession(): AgentSessionEntity?

    @Query("SELECT * FROM agent_session WHERE id = :id")
    abstract suspend fun sessionById(id: String): AgentSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSession(entity: AgentSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSteps(entities: List<AgentPlanStepEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTrace(entities: List<AgentTraceEventEntity>)

    @Query("SELECT * FROM agent_plan_step WHERE session_id = :id ORDER BY step_index")
    abstract suspend fun stepsFor(id: String): List<AgentPlanStepEntity>

    @Query("SELECT * FROM agent_trace_event WHERE session_id = :id ORDER BY seq")
    abstract suspend fun traceFor(id: String): List<AgentTraceEventEntity>

    @Query("UPDATE agent_session SET state = :state WHERE id = :id")
    abstract suspend fun updateState(id: String, state: String)

    @Query("DELETE FROM agent_session WHERE id = :id")
    abstract suspend fun deleteSession(id: String)

    @Query("DELETE FROM agent_session WHERE id <> :id")
    protected abstract suspend fun deleteSessionsOtherThan(id: String)

    @Query("DELETE FROM agent_plan_step WHERE session_id = :id")
    protected abstract suspend fun deleteStepsFor(id: String)

    @Query("DELETE FROM agent_trace_event WHERE session_id = :id")
    protected abstract suspend fun deleteTraceFor(id: String)

    /**
     * The conditional write that makes a double confirmation harmless. Two taps that race both run
     * this statement; the second matches no row because `consent IS NULL` no longer holds, and the
     * caller sees 0. No flag, no debounce, no window in which both can win.
     *
     * Every clause carries its own weight, and each was checked by breaking it: `session_id` and
     * `step_index` say *which* decision this is, `consent IS NULL` says it has not been made, and the
     * `EXISTS` says the session is still the one asking. Removing any one of the four turns a test in
     * `AgentSessionDaoTest` red — `session_id` is held only by
     * `a consent write does not reach an identically-numbered step of another session`, which is why
     * that test seeds two sessions through the DAO instead of through the store.
     */
    @Query(
        """
        UPDATE agent_plan_step SET consent = :granted
        WHERE session_id = :sessionId
          AND step_index = :stepIndex
          AND consent IS NULL
          AND EXISTS (SELECT 1 FROM agent_session WHERE id = :sessionId AND state = 'AwaitingConsent')
        """,
    )
    abstract suspend fun recordConsentIfPending(sessionId: String, stepIndex: Int, granted: Boolean): Int

    /**
     * The whole session — row, steps and trace — in **one** transaction, and that is the whole reason
     * it exists rather than three calls at the call site.
     *
     * [replaceSession] is transactional, so a writer is atomic; three separate reads are not, and each
     * one sees whatever is committed at the moment it runs. A `delete` landing between them (the cancel
     * path is a single `DELETE` plus cascade) returns a session row with **no** steps — and an empty
     * `ExecutionPlan` is constructible, so that assembles into a perfectly well-formed session whose
     * plan is empty. `AgentExecutor.prepare` then finds no step at the cursor and ends it `Completed`:
     * the run reports success for a goal on which nothing executed and nothing was traced.
     *
     * The transaction is the belt. The braces are in the mapper, which refuses a session with no steps
     * outright — a persisted plan always has at least one, so zero means the tables disagree.
     */
    @Transaction
    open suspend fun loadActive(): AgentSessionRows? {
        val session = activeSession() ?: return null
        return AgentSessionRows(session, stepsFor(session.id), traceFor(session.id))
    }

    /**
     * The whole session — row, steps and trace — in **one** transaction, so a crash cannot leave a
     * cursor ahead of the trace that explains it.
     *
     * Three things happen here that the caller cannot do for itself:
     *  - **any other session id is removed.** The store holds one session; that is enforced on the way
     *    in rather than assumed by `activeSession()`'s `LIMIT 1`.
     *  - **children are deleted explicitly before the parent is replaced.** `INSERT OR REPLACE` on the
     *    parent would delete-and-reinsert it, and whether that fires `ON DELETE CASCADE` depends on
     *    SQLite's recursive-trigger setting. Deleting first makes the outcome the same either way.
     *  - **`created_at` and each event's `at` are preserved** when the row already exists. They are
     *    stamped by the caller as "now"; a value that was already on disk wins, because this method
     *    runs after every transition and a restamped `created_at` would mean "last written".
     */
    @Transaction
    open suspend fun replaceSession(
        session: AgentSessionEntity,
        steps: List<AgentPlanStepEntity>,
        trace: List<AgentTraceEventEntity>,
    ) {
        val createdAt = sessionById(session.id)?.createdAt
        val stamped = traceFor(session.id).associate { it.seq to it.at }

        deleteSessionsOtherThan(session.id)
        deleteStepsFor(session.id)
        deleteTraceFor(session.id)

        upsertSession(if (createdAt == null) session else session.copy(createdAt = createdAt))
        upsertSteps(steps)
        upsertTrace(trace.map { row -> stamped[row.seq]?.let { row.copy(at = it) } ?: row })
    }
}
