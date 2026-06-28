package com.sidr.launcher.data.ailocal.provision

import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.device.DeviceProfileProvider
import kotlinx.coroutines.flow.firstOrNull

/**
 * Gate-before-enqueue (Block Q, §6.A). [ensureModel] decides — using the **static** gate inputs
 * only — whether to schedule the one-shot download, then delegates the actual enqueue to the
 * [ModelDownloadScheduler] seam.
 *
 * **Enqueue-gate-static-only (§6.A, conscious choice):** the enqueue decision uses
 * `profile != LOW_END && availability != Available` (plus the OQ#2 [ModelDownloadConfig.isPinned]
 * release-config fact). It deliberately does **NOT** consult transient `thermalOk`/`batteryOk`:
 *  - A momentary battery-saver at app start must never *permanently* prevent the one-shot download
 *    from being scheduled. Runtime battery/charging is handled by the WorkManager **constraints**
 *    (which merely defer execution).
 *  - Thermal/battery for *inference* is the per-inference re-check already inside
 *    `OnnxIntentClassifier` (Block P) via the full `LocalInferenceGate.allowsLocalNlu(...)`.
 *
 * This is the same one policy as `LocalInferenceGate` evaluated for the right inputs at the right
 * moment — not a second policy. `LOW_END` never schedules and never loads ONNX.
 */
class ModelManager(
    private val deviceProfileProvider: DeviceProfileProvider,
    private val availability: ModelAvailabilityRepository,
    private val scheduler: ModelDownloadScheduler,
    private val config: ModelDownloadConfig,
) {
    suspend fun ensureModel() {
        // LOW_END is excluded by the gate at every moment — never schedule, never load.
        if (deviceProfileProvider.profile() == DeviceProfile.LOW_END) return

        // OQ#2-pending: with no pinned artifact the download is inert — do not enqueue futile work.
        if (!config.isPinned) return

        // Idempotent: a verified model already present needs no download.
        val current = availability.availability(config.modelId).firstOrNull()
        if (current == ModelAvailability.Available) return

        scheduler.ensureScheduled(config.modelId)
    }
}
