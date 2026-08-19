package com.sidr.launcher.domain.device

/**
 * Observed hardware capability snapshot. Produced by [DeviceProfileProvider].
 *
 * Field rationale:
 *   [ramBytes]        — physical RAM; drives profile classification (Block Q).
 *   [cpuCores]        — logical core count; drives profile classification (Block Q).
 *   [nnapiAvailable]  — whether the NNAPI EP can be attempted (API 29+, non-deprecated SoC).
 *                       Historically fed local-NLU execution-provider selection, removed in the
 *                       agentic-restart Этап 0.3; kept as a capability signal for a future
 *                       local-inference accelerator. NNAPI deprecated in Android 15 (see Fork P6-5).
 *   [thermalOk]       — device is not thermally throttled; re-checked before capability-sensitive work.
 *   [batteryOk]       — device is not in battery-saver / critically low; re-checked likewise.
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
