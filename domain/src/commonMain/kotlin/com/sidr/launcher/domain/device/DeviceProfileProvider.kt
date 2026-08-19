package com.sidr.launcher.domain.device

/**
 * Port for reading the current device capability. Implementation lives in :core:android
 * as AndroidDeviceProfiler (Block Q).
 *
 * Reads are synchronous: the detector caches its result on first call (expensive I/O) and
 * returns cheaply thereafter. Callers that need to react to runtime changes (thermal, battery)
 * should re-call [capability] before each inference rather than holding the result.
 */
interface DeviceProfileProvider {
    fun profile(): DeviceProfile
    fun capability(): DeviceCapability
}
