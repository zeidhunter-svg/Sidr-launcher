package com.sidr.launcher.data.repository.suggestions

import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.history.UsageHistoryRepository
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionProvider
import com.sidr.launcher.domain.suggestions.SuggestionSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.exp

/**
 * Zero-permission provider: recency + frequency over [UsageHistoryRepository] (Phase 7, Block U1).
 *
 * `packageName` is used as both [Suggestion.label] and [Suggestion.actionId] — there is no app-label
 * lookup at this layer (the real display label is resolved by Block W's UI, same as the rest of the
 * domain suggestion model). Recency decays exponentially over [RECENT_HORIZON_MS]; frequency is the
 * launch count normalised against the most-used app in the set. Always contributes once any usage
 * history exists, independent of every optional permission (the U6 degrade contract).
 */
class UsageSuggestionProvider @Inject constructor(
    private val usageHistoryRepository: UsageHistoryRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SuggestionProvider {

    override suspend fun provide(context: SuggestionContext): List<Suggestion> =
        withContext(ioDispatcher) {
            val records = usageHistoryRepository.getUsageRecords().first()
            if (records.isEmpty()) return@withContext emptyList()

            val maxLaunchCount = records.maxOf { it.launchCount }.coerceAtLeast(1)

            val recent = records
                .filter { context.nowEpochMs - it.lastUsedEpochMs <= RECENT_HORIZON_MS }
                .map { record -> recentCandidate(record, context.nowEpochMs) }

            val frequent = records
                .filter { it.launchCount > 1 }
                .sortedByDescending { it.launchCount }
                .take(MAX_FREQUENT_CANDIDATES)
                .map { record -> frequentCandidate(record, maxLaunchCount) }

            recent + frequent
        }

    private fun recentCandidate(record: AppUsageRecord, nowEpochMs: Long): Suggestion {
        val elapsedMs = (nowEpochMs - record.lastUsedEpochMs).coerceAtLeast(0)
        val decay = exp(-elapsedMs.toDouble() / RECENT_HORIZON_MS)
        return Suggestion(
            label = record.packageName,
            actionId = record.packageName,
            source = SuggestionSource.RECENT_USAGE,
            score = decay,
        )
    }

    private fun frequentCandidate(record: AppUsageRecord, maxLaunchCount: Int): Suggestion {
        val ratio = record.launchCount.toDouble() / maxLaunchCount
        return Suggestion(
            label = record.packageName,
            actionId = record.packageName,
            source = SuggestionSource.FREQUENT_USAGE,
            score = ratio,
        )
    }

    private companion object {
        val RECENT_HORIZON_MS = TimeUnit.DAYS.toMillis(7)
        const val MAX_FREQUENT_CANDIDATES = 5
    }
}
