package com.sidr.launcher.work

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Owns the Phase-7 periodic background work. `ensureScheduled()` is safe to call repeatedly from
 * startup and boot warmup because both jobs use stable unique names + UPDATE policy.
 */
class SuggestionsWorkScheduler @Inject constructor(
    private val workScheduler: UniquePeriodicWorkScheduler,
    private val precomputeGate: SuggestionPrecomputeGate,
) {

    suspend fun ensureScheduled() {
        scheduleUsageCleanup()
        if (precomputeGate.allowScheduling()) {
            workScheduler.enqueueUniquePeriodicWork(
                SuggestionPrecomputeWorker.UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                suggestionPrecomputeRequest(),
            )
        } else {
            workScheduler.cancelUniqueWork(SuggestionPrecomputeWorker.UNIQUE_NAME)
        }
    }

    private fun scheduleUsageCleanup() {
        workScheduler.enqueueUniquePeriodicWork(
            UsageCleanupWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            usageCleanupRequest(),
        )
    }

    private fun suggestionPrecomputeRequest(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<SuggestionPrecomputeWorker>(
            PRECOMPUTE_REPEAT_HOURS,
            TimeUnit.HOURS,
            PRECOMPUTE_FLEX_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(suggestionPrecomputeConstraints())
            .build()

    private fun usageCleanupRequest(): PeriodicWorkRequest =
        PeriodicWorkRequestBuilder<UsageCleanupWorker>(
            CLEANUP_REPEAT_DAYS,
            TimeUnit.DAYS,
            CLEANUP_FLEX_DAYS,
            TimeUnit.DAYS,
        )
            .setConstraints(usageCleanupConstraints())
            .build()

    internal fun suggestionPrecomputeConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()

    internal fun usageCleanupConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

    private companion object {
        const val PRECOMPUTE_REPEAT_HOURS = 24L
        const val PRECOMPUTE_FLEX_HOURS = 6L
        const val CLEANUP_REPEAT_DAYS = 7L
        const val CLEANUP_FLEX_DAYS = 1L
    }
}
