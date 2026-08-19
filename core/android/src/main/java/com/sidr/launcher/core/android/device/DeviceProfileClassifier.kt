package com.sidr.launcher.core.android.device

import com.sidr.launcher.domain.device.DeviceCapability
import com.sidr.launcher.domain.device.DeviceProfile

/**
 * Pure, JVM-testable classification logic for [AndroidDeviceProfiler] (Block Q, §5.A/C). All the
 * Android-API reads (RAM, cores, thermal, battery) stay behind the profiler; this object turns those
 * already-read primitives into a [DeviceProfile] and a [DeviceCapability] with no platform deps.
 *
 * Thresholds (RAM measured against `ActivityManager.MemoryInfo.totalMem`, which reports a few hundred
 * MB below the nominal spec, so the cut-offs sit below the round numbers):
 *  - **LOW_END**  — `totalMem < 2.5 GB` **or** `< 4` logical cores. A nominal-2 GB phone reports
 *    ~1.9 GB (< 2.5 → LOW_END); a nominal-3 GB phone reports ~2.8 GB (≥ 2.5 → not LOW_END).
 *  - **HIGH_END** — `totalMem ≥ 5.5 GB` **and** `≥ 8` cores (a nominal-6 GB octa-core reports ~5.6 GB).
 *  - **MID_RANGE** — everything else (e.g. the SM-A325F dev device: 4 GB / 8 cores → MID_RANGE).
 *
 * Phase 6's effective gate is LOW_END-vs-rest, so the MID/HIGH split is forward-looking; the values
 * are deliberately conservative (only generously-provisioned devices reach HIGH_END).
 */
object DeviceProfileClassifier {

    const val LOW_END_MAX_RAM_BYTES = 2_500_000_000L
    const val LOW_END_MIN_CORES = 4
    const val HIGH_END_MIN_RAM_BYTES = 5_500_000_000L
    const val HIGH_END_MIN_CORES = 8

    /** PowerManager.THERMAL_STATUS_SEVERE — at/above this we treat the device as throttled. */
    const val THERMAL_STATUS_SEVERE = 3

    /** Sentinel for "no thermal signal" (API < 29). Reads as thermally OK. */
    const val NO_THERMAL_SIGNAL = -1

    fun classify(ramBytes: Long, cpuCores: Int): DeviceProfile = when {
        ramBytes < LOW_END_MAX_RAM_BYTES || cpuCores < LOW_END_MIN_CORES -> DeviceProfile.LOW_END
        ramBytes >= HIGH_END_MIN_RAM_BYTES && cpuCores >= HIGH_END_MIN_CORES -> DeviceProfile.HIGH_END
        else -> DeviceProfile.MID_RANGE
    }

    /**
     * Assembles a [DeviceCapability] from raw signals.
     *
     * @param nnapiAvailable best-effort hint: `Build.VERSION.SDK_INT >= 29`. Historically fed local-NLU
     *   execution-provider selection (removed Этап 0.3); kept as a device-capability signal for a
     *   future local-inference accelerator.
     * @param thermalStatus `PowerManager.getCurrentThermalStatus()` on API 29+, else [NO_THERMAL_SIGNAL].
     *   `thermalOk` is `status < THERMAL_STATUS_SEVERE` (the sentinel reads as OK).
     * @param isPowerSaveMode `PowerManager.isPowerSaveMode`; `batteryOk` is its negation.
     */
    fun capability(
        ramBytes: Long,
        cpuCores: Int,
        nnapiAvailable: Boolean,
        thermalStatus: Int,
        isPowerSaveMode: Boolean,
    ): DeviceCapability = DeviceCapability(
        ramBytes = ramBytes,
        cpuCores = cpuCores,
        nnapiAvailable = nnapiAvailable,
        thermalOk = thermalStatus < THERMAL_STATUS_SEVERE,
        batteryOk = !isPowerSaveMode,
    )
}
