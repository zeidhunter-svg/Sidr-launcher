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
    override val versionName: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
}
