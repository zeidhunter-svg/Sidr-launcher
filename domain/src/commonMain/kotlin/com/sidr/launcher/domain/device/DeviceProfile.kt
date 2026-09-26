package com.sidr.launcher.domain.device

/**
 * Coarse capability tier for the current device.
 *
 * Decided by [DeviceProfileProvider] (detected in Block Q, :core:android) and cached in
 * [DeviceProfileCacheEntry]. The three-way split is forward-looking: Phase 6 uses
 * LOW_END-vs-rest (MID_RANGE and HIGH_END share the same effective gate). Additional
 * differentiation (e.g. heap ceilings, EP selection) is reserved for a later phase.
 */
enum class DeviceProfile { LOW_END, MID_RANGE, HIGH_END }
