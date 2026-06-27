package com.sidr.launcher.domain.device

import com.sidr.launcher.domain.ai.local.ModelAvailability

/**
 * Pure stateless policy: determines whether local NLU inference is allowed.
 *
 * Truth table (Phase 6 — LOW_END-vs-rest; MID_RANGE and HIGH_END share the same effective gate
 * because Phase 6 does not yet differentiate them beyond the LOW_END exclusion):
 *
 *   LOW_END   → false always, regardless of availability, thermal, or battery.
 *   MID_RANGE → true iff availability == Available && thermalOk && batteryOk.
 *   HIGH_END  → true iff availability == Available && thermalOk && batteryOk.
 *
 * Fields NOT consulted by this gate (they feed other decisions):
 *   [DeviceCapability.nnapiAvailable] — EP selection inside OnnxIntentClassifier (Block P).
 *   [DeviceCapability.ramBytes]       — profile classification in AndroidDeviceProfiler (Block Q).
 *   [DeviceCapability.cpuCores]       — profile classification in AndroidDeviceProfiler (Block Q).
 *   `online`                          — owned by ConnectivityChecker; local NLU needs no network.
 *
 * The same function is called at two moments (Fork P6-4):
 *   1. DI/graph time — static inputs (profile, model availability) decide which IntentMatcher impl
 *      to bind (real ONNX or NoOp).
 *   2. Per-inference time — dynamic inputs (thermalOk, batteryOk) re-checked inside
 *      OnnxIntentClassifier before each session run.
 * One policy, one rule, two evaluation moments — no second policy, no duplicated logic.
 */
object LocalInferenceGate {

    fun allowsLocalNlu(
        profile: DeviceProfile,
        capability: DeviceCapability,
        availability: ModelAvailability,
    ): Boolean {
        if (profile == DeviceProfile.LOW_END) return false
        if (availability != ModelAvailability.Available) return false
        return capability.thermalOk && capability.batteryOk
    }
}
