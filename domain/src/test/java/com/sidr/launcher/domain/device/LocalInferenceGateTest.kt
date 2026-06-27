package com.sidr.launcher.domain.device

import com.sidr.launcher.domain.ai.local.ModelAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Truth table for [LocalInferenceGate.allowsLocalNlu].
 *
 * 3 profiles × 3 availabilities × 2 thermalOk × 2 batteryOk = 36 cases, all covered.
 *
 * Summary of the table (Phase 6, LOW_END-vs-rest):
 *   LOW_END              → false always (12 cases)
 *   MID_RANGE Available  → true only when thermalOk && batteryOk (4 cases: 1 true, 3 false)
 *   MID_RANGE Missing    → false always (4 cases)
 *   MID_RANGE Unverified → false always (4 cases)
 *   HIGH_END  Available  → true only when thermalOk && batteryOk (same gate as MID_RANGE)
 *   HIGH_END  Missing    → false always (4 cases)
 *   HIGH_END  Unverified → false always (4 cases)
 */
class LocalInferenceGateTest {

    private fun cap(thermalOk: Boolean, batteryOk: Boolean) = DeviceCapability(
        ramBytes = 4_000_000_000L,
        cpuCores = 4,
        nnapiAvailable = false,
        thermalOk = thermalOk,
        batteryOk = batteryOk,
    )

    // ─── LOW_END — always false (12 cases: 3 availability × 2 thermal × 2 battery) ──────────────

    @Test fun `LOW_END is false for all availability and thermal-battery combinations`() {
        for (avail in ModelAvailability.values()) {
            for (thermal in listOf(true, false)) {
                for (battery in listOf(true, false)) {
                    assertFalse(
                        "Expected false: LOW_END/$avail/thermal=$thermal/battery=$battery",
                        LocalInferenceGate.allowsLocalNlu(DeviceProfile.LOW_END, cap(thermal, battery), avail),
                    )
                }
            }
        }
    }

    // ─── MID_RANGE / Available ────────────────────────────────────────────────────────────────────

    @Test fun `MID_RANGE Available thermalOk batteryOk is true`() =
        assertTrue(LocalInferenceGate.allowsLocalNlu(DeviceProfile.MID_RANGE, cap(true, true), ModelAvailability.Available))

    @Test fun `MID_RANGE Available thermalOk batteryNotOk is false`() =
        assertFalse(LocalInferenceGate.allowsLocalNlu(DeviceProfile.MID_RANGE, cap(true, false), ModelAvailability.Available))

    @Test fun `MID_RANGE Available thermalNotOk batteryOk is false`() =
        assertFalse(LocalInferenceGate.allowsLocalNlu(DeviceProfile.MID_RANGE, cap(false, true), ModelAvailability.Available))

    @Test fun `MID_RANGE Available thermalNotOk batteryNotOk is false`() =
        assertFalse(LocalInferenceGate.allowsLocalNlu(DeviceProfile.MID_RANGE, cap(false, false), ModelAvailability.Available))

    // ─── MID_RANGE / Missing (4 cases) ───────────────────────────────────────────────────────────

    @Test fun `MID_RANGE Missing is false for all thermal-battery combinations`() {
        for (thermal in listOf(true, false)) {
            for (battery in listOf(true, false)) {
                assertFalse(
                    "Expected false: MID_RANGE/Missing/thermal=$thermal/battery=$battery",
                    LocalInferenceGate.allowsLocalNlu(DeviceProfile.MID_RANGE, cap(thermal, battery), ModelAvailability.Missing),
                )
            }
        }
    }

    // ─── MID_RANGE / Unverified (4 cases) ────────────────────────────────────────────────────────

    @Test fun `MID_RANGE Unverified is false for all thermal-battery combinations`() {
        for (thermal in listOf(true, false)) {
            for (battery in listOf(true, false)) {
                assertFalse(
                    "Expected false: MID_RANGE/Unverified/thermal=$thermal/battery=$battery",
                    LocalInferenceGate.allowsLocalNlu(DeviceProfile.MID_RANGE, cap(thermal, battery), ModelAvailability.Unverified),
                )
            }
        }
    }

    // ─── HIGH_END / Available ─────────────────────────────────────────────────────────────────────

    @Test fun `HIGH_END Available thermalOk batteryOk is true`() =
        assertTrue(LocalInferenceGate.allowsLocalNlu(DeviceProfile.HIGH_END, cap(true, true), ModelAvailability.Available))

    @Test fun `HIGH_END Available thermalOk batteryNotOk is false`() =
        assertFalse(LocalInferenceGate.allowsLocalNlu(DeviceProfile.HIGH_END, cap(true, false), ModelAvailability.Available))

    @Test fun `HIGH_END Available thermalNotOk batteryOk is false`() =
        assertFalse(LocalInferenceGate.allowsLocalNlu(DeviceProfile.HIGH_END, cap(false, true), ModelAvailability.Available))

    @Test fun `HIGH_END Available thermalNotOk batteryNotOk is false`() =
        assertFalse(LocalInferenceGate.allowsLocalNlu(DeviceProfile.HIGH_END, cap(false, false), ModelAvailability.Available))

    // ─── HIGH_END / Missing (4 cases) ────────────────────────────────────────────────────────────

    @Test fun `HIGH_END Missing is false for all thermal-battery combinations`() {
        for (thermal in listOf(true, false)) {
            for (battery in listOf(true, false)) {
                assertFalse(
                    "Expected false: HIGH_END/Missing/thermal=$thermal/battery=$battery",
                    LocalInferenceGate.allowsLocalNlu(DeviceProfile.HIGH_END, cap(thermal, battery), ModelAvailability.Missing),
                )
            }
        }
    }

    // ─── HIGH_END / Unverified (4 cases) ─────────────────────────────────────────────────────────

    @Test fun `HIGH_END Unverified is false for all thermal-battery combinations`() {
        for (thermal in listOf(true, false)) {
            for (battery in listOf(true, false)) {
                assertFalse(
                    "Expected false: HIGH_END/Unverified/thermal=$thermal/battery=$battery",
                    LocalInferenceGate.allowsLocalNlu(DeviceProfile.HIGH_END, cap(thermal, battery), ModelAvailability.Unverified),
                )
            }
        }
    }

    // ─── nnapiAvailable does NOT affect the gate ──────────────────────────────────────────────────

    @Test fun `nnapiAvailable true does not change the MID_RANGE Available gate outcome`() {
        val capWithNnapi = DeviceCapability(
            ramBytes = 4_000_000_000L, cpuCores = 4, nnapiAvailable = true,
            thermalOk = true, batteryOk = true,
        )
        assertTrue(LocalInferenceGate.allowsLocalNlu(DeviceProfile.MID_RANGE, capWithNnapi, ModelAvailability.Available))
    }
}
