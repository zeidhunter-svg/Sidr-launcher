package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.db.dao.AgentSessionDao
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The Room implementation of [AgentSessionStore] (A0 spec §7).
 *
 * Three properties are worth naming, because they are what the port promises and not incidental:
 *  - **one session, or none.** [save] writes the row, its steps and its trace in a single
 *    transaction and removes any other session id in the same one, so "0 or 1 row" is enforced on the
 *    way in. [delete] removes the session row only; the two child tables follow by cascade, which is
 *    what makes "at rest the three tables are empty" true rather than aspirational. [active] reads all
 *    three in one transaction too — a writer that is atomic and a reader that is not still tears.
 *  - **consent is a conditional `UPDATE`.** [recordConsentIfPending] reports whether it applied, and
 *    two racing taps cannot both be told yes. Whole-object writes were avoided deliberately: that
 *    pattern already cost this project the `autoHideNavBar` bug (DS-11), which only surfaced on device.
 *  - **nothing throws to the caller.** Every method returns [OperationResult], and a row that cannot
 *    be read back is a [OperationError.UnknownError] with reason `db_agent_session_corrupt` — never a
 *    session assembled from guessed defaults.
 *
 * The clock lives here because the domain has no clock: `:domain` is `commonMain` across two KMP
 * targets, and `TraceEvent` carries no timestamp on purpose. It is a constructor parameter so the
 * "`created_at` survives a re-save" and "`at` survives a re-save" tests can tell a preserved stamp
 * from a coincidentally identical one.
 */
class RoomAgentSessionStore(
    private val dao: AgentSessionDao,
    private val ioDispatcher: CoroutineDispatcher,
    private val now: () -> Long,
) : AgentSessionStore {

    @Inject
    constructor(
        dao: AgentSessionDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(dao, ioDispatcher, System::currentTimeMillis)

    override suspend fun active(): OperationResult<AgentSession?> = guarded("db_agent_session_read_failed") {
        val rows = dao.loadActive() ?: return@guarded null
        AgentSessionMappers.toDomain(rows.session, rows.steps, rows.trace)
    }

    override suspend fun save(session: AgentSession): OperationResult<Unit> =
        guarded("db_agent_session_write_failed") {
            val stamp = now()
            dao.replaceSession(
                session = AgentSessionMappers.toSessionEntity(session, stamp),
                steps = AgentSessionMappers.toStepEntities(session),
                trace = AgentSessionMappers.toTraceEntities(session, stamp),
            )
        }

    override suspend fun delete(id: AgentSessionId): OperationResult<Unit> =
        guarded("db_agent_session_delete_failed") { dao.deleteSession(id.value) }

    override suspend fun recordConsentIfPending(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<Boolean> = guarded("db_agent_consent_write_failed") {
        dao.recordConsentIfPending(id.value, stepIndex, granted) > 0
    }

    private suspend fun <T> guarded(reason: String, block: suspend () -> T): OperationResult<T> =
        withContext(ioDispatcher) {
            try {
                OperationResult.Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (_: CorruptAgentRowException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "db_agent_session_corrupt"))
            } catch (_: Exception) {
                OperationResult.Failure(OperationError.UnknownError(reason = reason))
            }
        }
}
