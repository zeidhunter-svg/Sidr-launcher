package com.sidr.launcher.work

import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Centralises the Phase-7 background-suggestions gate so scheduling and worker execution share the
 * same fail-closed policy.
 */
class SuggestionPrecomputeGate @Inject constructor(
    private val featureFlagRepository: FeatureFlagRepository,
    private val deviceProfileProvider: DeviceProfileProvider,
) {

    suspend fun allowScheduling(): Boolean =
        try {
            featureFlagRepository.getFlags().first().aiSuggestionsEnabled &&
                deviceProfileProvider.profile() != DeviceProfile.LOW_END
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }

    suspend fun allowExecution(): Boolean =
        try {
            featureFlagRepository.getFlags().first().aiSuggestionsEnabled &&
                deviceProfileProvider.profile() != DeviceProfile.LOW_END &&
                deviceProfileProvider.capability().batteryOk
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
}
