package com.sidr.launcher.data.repository.db

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.db.dao.AliasDao
import com.sidr.launcher.data.repository.db.mapper.AliasMapper
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AliasStoreImpl @Inject constructor(
    private val dao: AliasDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AliasStore {

    override suspend fun find(phrase: String): OperationResult<Alias?> = withContext(ioDispatcher) {
        try {
            OperationResult.Success(dao.findByPhrase(phrase)?.let(AliasMapper::toDomain))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_alias_read_failed"))
        }
    }

    override suspend fun upsert(alias: Alias): OperationResult<Unit> = withContext(ioDispatcher) {
        try {
            dao.upsert(AliasMapper.toEntity(alias))
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_alias_write_failed"))
        }
    }

    override suspend fun delete(phrase: String): OperationResult<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteByPhrase(phrase)
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_alias_delete_failed"))
        }
    }

    override fun observeAll(): Flow<List<Alias>> =
        dao.observeAll()
            .catch { emit(emptyList()) }
            .map { rows -> rows.mapNotNull { row -> runCatching { AliasMapper.toDomain(row) }.getOrNull() } }
}
