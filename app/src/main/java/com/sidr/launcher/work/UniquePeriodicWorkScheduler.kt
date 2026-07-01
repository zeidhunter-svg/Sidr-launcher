package com.sidr.launcher.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Tiny indirection over [WorkManager] so periodic scheduling policy stays unit-testable without a
 * real WorkManager instance. The production implementation is still just WorkManager.
 */
interface UniquePeriodicWorkScheduler {
    fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    )

    fun cancelUniqueWork(uniqueWorkName: String)
}

class WorkManagerUniquePeriodicWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : UniquePeriodicWorkScheduler {

    override fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    ) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(uniqueWorkName, policy, request)
    }

    override fun cancelUniqueWork(uniqueWorkName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
    }
}
