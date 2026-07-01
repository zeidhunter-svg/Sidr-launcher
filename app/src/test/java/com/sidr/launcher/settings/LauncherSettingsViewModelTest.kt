package com.sidr.launcher.settings

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import com.sidr.launcher.core.testing.FakeDeviceProfileProvider
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.work.SuggestionPrecomputeGate
import com.sidr.launcher.work.SuggestionPrecomputeWorker
import com.sidr.launcher.work.SuggestionsWorkScheduler
import com.sidr.launcher.work.UniquePeriodicWorkScheduler
import com.sidr.launcher.work.UsageCleanupWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enabling ai suggestions updates flags and schedules precompute`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(
            FeatureFlags(aiSuggestionsEnabled = false, usageHistoryEnabled = true),
        )
        val workScheduler = FakeUniquePeriodicWorkScheduler()
        val vm = buildViewModel(flagRepo, workScheduler)

        vm.setAiSuggestionsEnabled(true)
        advanceUntilIdle()

        assertTrue(flagRepo.getFlags().first().aiSuggestionsEnabled)
        assertTrue(
            workScheduler.enqueuedNames.contains(SuggestionPrecomputeWorker.UNIQUE_NAME),
        )
        assertTrue(workScheduler.enqueuedNames.contains(UsageCleanupWorker.UNIQUE_NAME))
        assertEquals(null, vm.uiState.value.errorMessage)
    }

    @Test
    fun `disabling ai suggestions cancels precompute and keeps cleanup scheduling`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(
            FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
        )
        val workScheduler = FakeUniquePeriodicWorkScheduler()
        val vm = buildViewModel(flagRepo, workScheduler)

        vm.setAiSuggestionsEnabled(false)
        advanceUntilIdle()

        assertFalse(flagRepo.getFlags().first().aiSuggestionsEnabled)
        assertEquals(listOf(SuggestionPrecomputeWorker.UNIQUE_NAME), workScheduler.cancelledUniqueNames)
        assertTrue(workScheduler.enqueuedNames.contains(UsageCleanupWorker.UNIQUE_NAME))
    }

    @Test
    fun `flag write failure surfaces a safe error and does not schedule`() = runTest(testDispatcher) {
        val flagRepo = FakeFeatureFlagRepository(
            FeatureFlags(aiSuggestionsEnabled = false, usageHistoryEnabled = true),
        ).apply {
            errorToReturn = OperationError.UnknownError("datastore down")
        }
        val workScheduler = FakeUniquePeriodicWorkScheduler()
        val vm = buildViewModel(flagRepo, workScheduler)

        vm.setAiSuggestionsEnabled(true)
        advanceUntilIdle()

        assertFalse(flagRepo.getFlags().first().aiSuggestionsEnabled)
        assertEquals("Couldn't update launcher settings. Please try again.", vm.uiState.value.errorMessage)
        assertTrue(workScheduler.enqueuedNames.isEmpty())
        assertTrue(workScheduler.cancelledUniqueNames.isEmpty())
    }

    private fun buildViewModel(
        flagRepo: FakeFeatureFlagRepository,
        workScheduler: FakeUniquePeriodicWorkScheduler,
    ): LauncherSettingsViewModel {
        val gate = SuggestionPrecomputeGate(
            featureFlagRepository = flagRepo,
            deviceProfileProvider = FakeDeviceProfileProvider(initialProfile = DeviceProfile.MID_RANGE),
        )
        return LauncherSettingsViewModel(
            featureFlagRepository = flagRepo,
            suggestionsWorkScheduler = SuggestionsWorkScheduler(workScheduler, gate),
            ioDispatcher = testDispatcher,
        )
    }

    private class FakeUniquePeriodicWorkScheduler : UniquePeriodicWorkScheduler {
        val enqueuedNames = mutableListOf<String>()
        val cancelledUniqueNames = mutableListOf<String>()

        override fun enqueueUniquePeriodicWork(
            uniqueWorkName: String,
            policy: ExistingPeriodicWorkPolicy,
            request: PeriodicWorkRequest,
        ) {
            enqueuedNames += uniqueWorkName
        }

        override fun cancelUniqueWork(uniqueWorkName: String) {
            cancelledUniqueNames += uniqueWorkName
        }
    }
}
