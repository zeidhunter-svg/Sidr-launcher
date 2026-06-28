package com.sidr.launcher.core.android.device

import com.sidr.launcher.domain.device.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileCacheMappingTest {

    @Test
    fun `only LOW_END maps to isLowEndDevice true`() {
        assertTrue(DeviceProfileCacheMapping.toCacheEntry(DeviceProfile.LOW_END, 123L).isLowEndDevice)
        assertFalse(DeviceProfileCacheMapping.toCacheEntry(DeviceProfile.MID_RANGE, 123L).isLowEndDevice)
        assertFalse(DeviceProfileCacheMapping.toCacheEntry(DeviceProfile.HIGH_END, 123L).isLowEndDevice)
        assertEquals(123L, DeviceProfileCacheMapping.toCacheEntry(DeviceProfile.HIGH_END, 123L).cachedAtEpochMs)
    }

    @Test
    fun `LOW_END round-trips exactly`() {
        val entry = DeviceProfileCacheMapping.toCacheEntry(DeviceProfile.LOW_END, 1L)
        assertEquals(DeviceProfile.LOW_END, DeviceProfileCacheMapping.profileFromCache(entry))
    }

    @Test
    fun `MID and HIGH collapse to the not-low-end bucket (intentionally lossy)`() {
        val midEntry = DeviceProfileCacheMapping.toCacheEntry(DeviceProfile.MID_RANGE, 1L)
        val highEntry = DeviceProfileCacheMapping.toCacheEntry(DeviceProfile.HIGH_END, 1L)
        // Both recover as MID_RANGE — the cache only preserves LOW_END-vs-rest (§5.B).
        assertEquals(DeviceProfile.MID_RANGE, DeviceProfileCacheMapping.profileFromCache(midEntry))
        assertEquals(DeviceProfile.MID_RANGE, DeviceProfileCacheMapping.profileFromCache(highEntry))
    }
}
