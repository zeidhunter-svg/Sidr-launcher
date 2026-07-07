package com.sidr.launcher.data.repository.db

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.db.dao.ResolutionPreferenceDao
import com.sidr.launcher.data.repository.db.mapper.ResolutionPreferenceMapper
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.MAX_RESOLUTION_PREFERENCES
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Room-backed [ResolutionPreferenceStore] (Stage-2 S2-1, Phase B / Task 8). Nothing consumes this
 * yet — no DI binding is wired in this task (Task 9 / Phase C).
 *
 * Mirrors [IntentMatchHistoryRepositoryImpl]'s idioms: suspend ops on [ioDispatcher], never throw
 * (rethrow [CancellationException], map any other failure to a category-only [OperationError]), the
 * LRU cap applied AFTER a successful write, and the observe [Flow] tolerant of both a stream-level
 * failure and a single malformed row.
 */
class ResolutionPreferenceStoreImpl @Inject constructor(
    private val dao: ResolutionPreferenceDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ResolutionPreferenceStore {

    override suspend fun find(
        key: CapabilityKey,
        context: ResolutionContext,
    ): OperationResult<ResolutionPreference?> = withContext(ioDispatcher) {
        try {
            val entity = dao.findByKey(
                actionId = key.actionId.value,
                query = key.query,
                contextKey = contextKeyOf(context),
            )
            OperationResult.Success(entity?.let(ResolutionPreferenceMapper::toDomain))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_resolution_pref_read_failed"))
        }
    }

    override suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dao.upsert(ResolutionPreferenceMapper.toEntity(preference))
                val excess = dao.count() - MAX_RESOLUTION_PREFERENCES
                if (excess > 0) dao.deleteOldest(excess)
                OperationResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Failure(OperationError.UnknownError(reason = "db_resolution_pref_write_failed"))
            }
        }

    override suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dao.deleteByKey(
                    actionId = key.actionId.value,
                    query = key.query,
                    contextKey = contextKeyOf(context),
                )
                OperationResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Failure(OperationError.UnknownError(reason = "db_resolution_pref_delete_failed"))
            }
        }

    override fun observeAll(): Flow<List<ResolutionPreference>> =
        dao.observeAll()
            .catch { emit(emptyList()) }
            .map { rows -> rows.mapNotNull { row -> runCatching { ResolutionPreferenceMapper.toDomain(row) }.getOrNull() } }
}

/**
 * v1 [ResolutionContext] -> `context_key` string encoding for read-path key lookups (`find`/
 * `delete`). Kept in lockstep with [ResolutionPreferenceMapper]'s write-side encoding (both
 * currently `"none"` for [ResolutionContext.None] — the only case v1 has).
 */
private fun contextKeyOf(context: ResolutionContext): String = when (context) {
    is ResolutionContext.None -> "none"
}
