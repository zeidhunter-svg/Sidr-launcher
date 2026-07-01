package com.sidr.launcher.work

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import com.sidr.launcher.core.testing.FakeDeviceProfileProvider
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.domain.device.DeviceCapability
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.preferences.FeatureFlags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsWorkSchedulerTest {

    @Test
    fun `ensureScheduled enqueues precompute and cleanup with stable unique names and constraints`() = runTest {
        val workScheduler = FakeUniquePeriodicWorkScheduler()
        val gate = SuggestionPrecomputeGate(
            featureFlagRepository = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
            ),
            deviceProfileProvider = FakeDeviceProfileProvider(initialProfile = DeviceProfile.MID_RANGE),
        )
        val scheduler = SuggestionsWorkScheduler(workScheduler = workScheduler, precomputeGate = gate)

        scheduler.ensureScheduled()

        assertEquals(2, workScheduler.enqueues.size)

        val precompute = workScheduler.enqueues.single { it.uniqueWorkName == SuggestionPrecomputeWorker.UNIQUE_NAME }
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, precompute.policy)
        assertEquals(
            SuggestionPrecomputeWorker::class.java.name,
            precompute.request.workSpec.workerClassName,
        )
        val precomputeConstraints = scheduler.suggestionPrecomputeConstraints()
        assertEquals(NetworkType.NOT_REQUIRED, precomputeConstraints.requiredNetworkType)
        assertTrue(precomputeConstraints.requiresBatteryNotLow())
        assertTrue(precomputeConstraints.requiresStorageNotLow())

        val cleanup = workScheduler.enqueues.single { it.uniqueWorkName == UsageCleanupWorker.UNIQUE_NAME }
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, cleanup.policy)
        assertEquals(
            UsageCleanupWorker::class.java.name,
            cleanup.request.workSpec.workerClassName,
        )
        val cleanupConstraints = scheduler.usageCleanupConstraints()
        assertEquals(NetworkType.NOT_REQUIRED, cleanupConstraints.requiredNetworkType)
        assertTrue(cleanupConstraints.requiresBatteryNotLow())
        assertTrue(cleanupConstraints.requiresStorageNotLow())
        assertFalse(cleanupConstraints.requiresDeviceIdle())
    }

    @Test
    fun `flag off cancels precompute and still schedules cleanup`() = runTest {
        val workScheduler = FakeUniquePeriodicWorkScheduler()
        val gate = SuggestionPrecomputeGate(
            featureFlagRepository = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = false, usageHistoryEnabled = true),
            ),
            deviceProfileProvider = FakeDeviceProfileProvider(initialProfile = DeviceProfile.MID_RANGE),
        )
        val scheduler = SuggestionsWorkScheduler(workScheduler = workScheduler, precomputeGate = gate)

        scheduler.ensureScheduled()

        assertEquals(listOf(SuggestionPrecomputeWorker.UNIQUE_NAME), workScheduler.cancelledUniqueNames)
        assertEquals(1, workScheduler.enqueues.size)
        assertEquals(UsageCleanupWorker.UNIQUE_NAME, workScheduler.enqueues.single().uniqueWorkName)
    }

    @Test
    fun `LOW_END cancels precompute and still schedules cleanup`() = runTest {
        val workScheduler = FakeUniquePeriodicWorkScheduler()
        val gate = SuggestionPrecomputeGate(
            featureFlagRepository = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
            ),
            deviceProfileProvider = FakeDeviceProfileProvider(initialProfile = DeviceProfile.LOW_END),
        )
        val scheduler = SuggestionsWorkScheduler(workScheduler = workScheduler, precomputeGate = gate)

        scheduler.ensureScheduled()

        assertEquals(listOf(SuggestionPrecomputeWorker.UNIQUE_NAME), workScheduler.cancelledUniqueNames)
        assertEquals(1, workScheduler.enqueues.size)
        assertEquals(UsageCleanupWorker.UNIQUE_NAME, workScheduler.enqueues.single().uniqueWorkName)
    }

    @Test
    fun `re-running ensureScheduled keeps idempotent unique names and update policy`() = runTest {
        val workScheduler = FakeUniquePeriodicWorkScheduler()
        val gate = SuggestionPrecomputeGate(
            featureFlagRepository = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
            ),
            deviceProfileProvider = FakeDeviceProfileProvider(initialProfile = DeviceProfile.HIGH_END),
        )
        val scheduler = SuggestionsWorkScheduler(workScheduler = workScheduler, precomputeGate = gate)

        scheduler.ensureScheduled()
        scheduler.ensureScheduled()

        val precomputeEnqueues = workScheduler.enqueues.filter {
            it.uniqueWorkName == SuggestionPrecomputeWorker.UNIQUE_NAME
        }
        assertEquals(2, precomputeEnqueues.size)
        assertTrue(precomputeEnqueues.all { it.policy == ExistingPeriodicWorkPolicy.UPDATE })

        val cleanupEnqueues = workScheduler.enqueues.filter {
            it.uniqueWorkName == UsageCleanupWorker.UNIQUE_NAME
        }
        assertEquals(2, cleanupEnqueues.size)
        assertTrue(cleanupEnqueues.all { it.policy == ExistingPeriodicWorkPolicy.UPDATE })
    }

    @Test
    fun `battery saver blocks execution but not scheduling`() = runTest {
        val gate = SuggestionPrecomputeGate(
            featureFlagRepository = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
            ),
            deviceProfileProvider = FakeDeviceProfileProvider(
                initialProfile = DeviceProfile.MID_RANGE,
                initialCapability = DeviceCapability(
                    ramBytes = 4_000_000_000L,
                    cpuCores = 4,
                    nnapiAvailable = false,
                    thermalOk = true,
                    batteryOk = false,
                ),
            ),
        )

        assertTrue(gate.allowScheduling())
        assertFalse(gate.allowExecution())
    }

    private class FakeUniquePeriodicWorkScheduler : UniquePeriodicWorkScheduler {
        val enqueues = mutableListOf<EnqueueCall>()
        val cancelledUniqueNames = mutableListOf<String>()

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ) {
            enqueues += EnqueueCall(uniqueWorkName, policy, request)
        }

        override fun cancelUniqueWork(uniqueWorkName: String) {
            cancelledUniqueNames += uniqueWorkName
        }
    }

    private data class EnqueueCall(
        val uniqueWorkName: String,
        val policy: ExistingPeriodicWorkPolicy,
        val request: PeriodicWorkRequest,
    )
}
