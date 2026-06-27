package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.device.DeviceCapability
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.device.DeviceProfileProvider

/**
 * Configurable fake [DeviceProfileProvider]. Settable profile and capability let tests
 * drive all gate branches without Android APIs. Not wired into any Hilt graph.
 */
class FakeDeviceProfileProvider(
    initialProfile: DeviceProfile = DeviceProfile.MID_RANGE,
    initialCapability: DeviceCapability = DeviceCapability(
        ramBytes = 4_000_000_000L,
        cpuCores = 4,
        nnapiAvailable = false,
        thermalOk = true,
        batteryOk = true,
    ),
) : DeviceProfileProvider {

    var profileToReturn: DeviceProfile = initialProfile
    var capabilityToReturn: DeviceCapability = initialCapability

    override fun profile(): DeviceProfile = profileToReturn
    override fun capability(): DeviceCapability = capabilityToReturn
}
