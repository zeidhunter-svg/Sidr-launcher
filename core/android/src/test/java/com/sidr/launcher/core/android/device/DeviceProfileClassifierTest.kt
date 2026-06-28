package com.sidr.launcher.core.android.device

import com.sidr.launcher.domain.device.DeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileClassifierTest {

    @Test
    fun `low RAM or few cores classify as LOW_END`() {
        assertEquals(DeviceProfile.LOW_END, DeviceProfileClassifier.classify(2_000_000_000L, 8)) // <2.5 GB
        assertEquals(DeviceProfile.LOW_END, DeviceProfileClassifier.classify(8_000_000_000L, 2)) // <4 cores
    }

    @Test
    fun `mid-tier RAM and cores classify as MID_RANGE`() {
        assertEquals(DeviceProfile.MID_RANGE, DeviceProfileClassifier.classify(2_800_000_000L, 8))
        assertEquals(DeviceProfile.MID_RANGE, DeviceProfileClassifier.classify(4_000_000_000L, 8)) // SM-A325F
        // High RAM but only 4 cores → not HIGH_END.
        assertEquals(DeviceProfile.MID_RANGE, DeviceProfileClassifier.classify(8_000_000_000L, 4))
    }

    @Test
    fun `high RAM and many cores classify as HIGH_END`() {
        assertEquals(DeviceProfile.HIGH_END, DeviceProfileClassifier.classify(6_000_000_000L, 8))
        assertEquals(DeviceProfile.HIGH_END, DeviceProfileClassifier.classify(12_000_000_000L, 8))
    }

    @Test
    fun `thermalOk is true below SEVERE and on the no-signal sentinel`() {
        assertTrue(capability(thermalStatus = DeviceProfileClassifier.NO_THERMAL_SIGNAL).thermalOk)
        assertTrue(capability(thermalStatus = 2).thermalOk) // MODERATE
        assertFalse(capability(thermalStatus = DeviceProfileClassifier.THERMAL_STATUS_SEVERE).thermalOk)
        assertFalse(capability(thermalStatus = 6).thermalOk) // SHUTDOWN
    }

    @Test
    fun `batteryOk is the negation of power-save mode, and signals pass through`() {
        assertFalse(capability(isPowerSaveMode = true).batteryOk)
        assertTrue(capability(isPowerSaveMode = false).batteryOk)
        val cap = capability(nnapiAvailable = true)
        assertTrue(cap.nnapiAvailable)
        assertEquals(4_000_000_000L, cap.ramBytes)
        assertEquals(8, cap.cpuCores)
    }

    private fun capability(
        nnapiAvailable: Boolean = false,
        thermalStatus: Int = DeviceProfileClassifier.NO_THERMAL_SIGNAL,
        isPowerSaveMode: Boolean = false,
    ) = DeviceProfileClassifier.capability(
        ramBytes = 4_000_000_000L,
        cpuCores = 8,
        nnapiAvailable = nnapiAvailable,
        thermalStatus = thermalStatus,
        isPowerSaveMode = isPowerSaveMode,
    )
}
