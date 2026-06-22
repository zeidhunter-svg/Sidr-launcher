package com.sidr.launcher.data.repository.db

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.db.dao.SuggestionRankingDao
import com.sidr.launcher.data.repository.db.mapper.SuggestionRankingMapper
import com.sidr.launcher.domain.history.SuggestionRankingRecord
import com.sidr.launcher.domain.history.SuggestionRankingRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SuggestionRankingRepositoryImpl @Inject constructor(
    private val dao: SuggestionRankingDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SuggestionRankingRepository {

    override fun getRankingRecords(): Flow<List<SuggestionRankingRecord>> =
        dao.getAllOrderedByScore()
            .catch { emit(emptyList()) }
            .map { rows -> rows.map(SuggestionRankingMapper::toDomain) }

    override suspend fun upsertRanking(
        record: SuggestionRankingRecord,
    ): OperationResult<Unit> = withContext(ioDispatcher) {
        try {
            dao.upsert(SuggestionRankingMapper.toEntity(record))
            val excess = dao.count() - MAX_RANKING_ROWS
            if (excess > 0) dao.deleteLowestScored(excess)
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_ranking_write_failed"))
        }
    }

    private companion object {
        const val MAX_RANKING_ROWS = 100
    }
}
