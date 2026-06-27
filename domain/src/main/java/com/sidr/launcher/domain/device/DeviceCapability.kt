package com.sidr.launcher.domain.device

/**
 * Observed hardware capability snapshot. Produced by [DeviceProfileProvider].
 *
 * Field rationale:
 *   [ramBytes]        — physical RAM; drives profile classification in Block Q.
 *   [cpuCores]        — logical core count; drives profile classification in Block Q.
 *   [nnapiAvailable]  — whether the NNAPI EP can be attempted (API 29+, non-deprecated SoC);
 *                       drives EP selection inside OnnxIntentClassifier (Block P), NOT the
 *                       on/off gate. NNAPI deprecated in Android 15 (see Fork P6-5).
 *   [thermalOk]       — device is not thermally throttled; re-checked at inference time.
 *   [batteryOk]       — device is not in battery-saver / critically low; re-checked at inference.
 *
 * `online` is intentionally absent — network reachability is owned by [ConnectivityChecker]
 * (Phase 5). Adding it here would create a second source of truth with no Phase-6 reader.
 */
data class DeviceCapability(
    val ramBytes: Long,
    val cpuCores: Int,
    val nnapiAvailable: Boolean,
    val thermalOk: Boolean,
    val batteryOk: Boolean,
)
