package com.sidr.launcher.core.android.device

import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.preferences.DeviceProfileCacheEntry

/**
 * Pure mapping between the rich [DeviceProfile] and the **intentionally lossy** persisted
 * [DeviceProfileCacheEntry] (Block Q, §5.B).
 *
 * The cache entry stores a single boolean (`isLowEndDevice`), so the round-trip collapses
 * `MID_RANGE` and `HIGH_END` into one "not low-end" bucket and cannot distinguish them on read.
 * This is **acceptable**: suggestion-precompute gating is LOW_END-vs-rest, so the only distinction
 * the cache needs to preserve is LOW_END. A future block that needs a true three-way split should
 * re-detect (detection is cheap) rather than widen the persisted schema.
 *
 * [profileFromCache] therefore returns [DeviceProfile.MID_RANGE] as the representative of the
 * "not low-end" bucket.
 */
object DeviceProfileCacheMapping {

    fun toCacheEntry(profile: DeviceProfile, nowEpochMs: Long): DeviceProfileCacheEntry =
        DeviceProfileCacheEntry(
            isLowEndDevice = profile == DeviceProfile.LOW_END,
            cachedAtEpochMs = nowEpochMs,
        )

    fun profileFromCache(entry: DeviceProfileCacheEntry): DeviceProfile =
        if (entry.isLowEndDevice) DeviceProfile.LOW_END else DeviceProfile.MID_RANGE
}
