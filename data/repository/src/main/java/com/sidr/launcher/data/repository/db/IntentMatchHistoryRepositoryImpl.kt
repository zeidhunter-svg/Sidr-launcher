package com.sidr.launcher.data.repository.db

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.db.dao.IntentMatchDao
import com.sidr.launcher.data.repository.db.mapper.IntentMatchMapper
import com.sidr.launcher.domain.history.IntentMatchRecord
import com.sidr.launcher.domain.history.IntentMatchHistoryRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class IntentMatchHistoryRepositoryImpl @Inject constructor(
    private val dao: IntentMatchDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : IntentMatchHistoryRepository {

    override fun getMatchRecords(): Flow<List<IntentMatchRecord>> =
        dao.getAllOrderedByRecency()
            .catch { emit(emptyList()) }
            .map { rows -> rows.map(IntentMatchMapper::toDomain) }

    override suspend fun recordMatch(
        record: IntentMatchRecord,
    ): OperationResult<Unit> = withContext(ioDispatcher) {
        try {
            // SEARCH query content is redacted inside IntentMatchMapper.toEntity (Fork 3).
            dao.insert(IntentMatchMapper.toEntity(record))
            val excess = dao.count() - MAX_INTENT_MATCH_ROWS
            if (excess > 0) dao.deleteOldest(excess)
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_intent_match_write_failed"))
        }
    }

    private companion object {
        const val MAX_INTENT_MATCH_ROWS = 200
    }
}
