package com.sidr.launcher.domain.preferences

/**
 * Serialisable primitive projection of the computed device capability profile.
 *
 * The detector lives in :core:android and produces the full DeviceProfile/DeviceCapability
 * model (to be formalised in :domain during the Phase 4/5 capability-split). This cache entry
 * stores only the primitives needed for cold-start decisions, with no Android types.
 *
 * When DeviceProfile is formalised, PreferencesMapper in :data:repository will translate
 * DeviceProfile ↔ DeviceProfileCacheEntry without requiring any change to the DataStore keys.
 *
 * Voice-input capability is intentionally absent (Phase 7). Add it when the voice pipeline
 * unfreezes and the guard-test denylist has been updated to distinguish capability flags from
 * raw voice storage.
 */
data class DeviceProfileCacheEntry(
    val isLowEndDevice: Boolean = false,
    val cachedAtEpochMs: Long = 0L,
)
