package com.sidr.launcher.domain.device

/**
 * What build this is. A port because `:domain` is `commonMain` and a version string is the
 * platform's to answer — the `DeviceProfileProvider` shape.
 *
 * It exists for one procedural reason: an acceptance that cannot name the build it ran on is an
 * acceptance of an unnamed build. The 2026-09-20 round was conducted partly in the belief that a
 * provider was configured when it was not; naming the artefact is the cheapest half of not
 * repeating that.
 */
interface AppBuildInfo {
    val versionName: String
}
