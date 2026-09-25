package com.sidr.launcher.core.android.device

import android.content.Context
import com.sidr.launcher.domain.device.AppBuildInfo

/**
 * Reads what is **installed**, not what was compiled into this module — which is the question an
 * acceptance asks. `PackageManager` access lives in `:core:android` by the contract table.
 *
 * **A plain class with no annotations**, exactly like [AndroidDeviceProfiler] beside it: this module
 * has no Hilt plugin, no kapt and no `javax.inject` on its classpath, and it is assembled by hand in
 * `:app`'s `DeviceProfileProvidesModule`.
 */
class AndroidAppBuildInfo(private val context: Context) : AppBuildInfo {
    // Fix round 1 (review finding 3): the value cannot change while the process lives — the
    // installed package's versionName is fixed at process start — so it is read once, not
    // re-queried from PackageManager on every access (e.g. every `combine` emission upstream).
    override val versionName: String by lazy {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
    }
}
