package com.sidr.launcher.data.repository.db

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.data.repository.db.dao.AppUsageDao
import com.sidr.launcher.data.repository.db.mapper.AppUsageMapper
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UsageHistoryRepositoryImpl @Inject constructor(
    private val dao: AppUsageDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UsageHistoryRepository {

    override fun getUsageRecords(): Flow<List<AppUsageRecord>> =
        dao.getAllOrderedByUsage()
            .catch { emit(emptyList()) }
            .map { rows -> rows.map(AppUsageMapper::toDomain) }

    override suspend fun recordLaunch(
        packageName: String,
        timestampEpochMs: Long,
    ): OperationResult<Unit> = withContext(ioDispatcher) {
        try {
            dao.upsertLaunch(packageName, timestampEpochMs)
            val excess = dao.count() - MAX_USAGE_ROWS
            if (excess > 0) dao.deleteOldest(excess)
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(reason = "db_usage_write_failed"))
        }
    }

    private companion object {
        const val MAX_USAGE_ROWS = 200
    }
}
