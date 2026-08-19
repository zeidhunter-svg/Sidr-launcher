package com.sidr.launcher.core.android.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.sidr.launcher.domain.device.DeviceCapability
import com.sidr.launcher.domain.device.DeviceProfile
import com.sidr.launcher.domain.device.DeviceProfileProvider
import com.sidr.launcher.domain.preferences.DeviceProfileCacheRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Android implementation of the [DeviceProfileProvider] port (Block Q, §5.A/C).
 *
 * Reads RAM ([ActivityManager.MemoryInfo.totalMem]) + logical cores ([Runtime.availableProcessors])
 * + the NNAPI/thermal/battery signals, and delegates the actual decisions to the pure
 * [DeviceProfileClassifier] (which is JVM-tested). The Android-API reads here are device/instrumented-
 * pending where they cannot be unit-tested.
 *
 * [profile] is computed once and cached in memory (the port contract: cache on first call, return
 * cheaply thereafter) and **written through** to [DeviceProfileCacheRepository] for cold-start use.
 * [capability] is re-read on **every** call — thermal/battery move at runtime and callers
 * (e.g. suggestion-precompute gating) should re-check before each capability-sensitive decision.
 *
 * Plain class (no Hilt annotations) so `core/android` stays DI-framework-free; constructed in `:app`
 * with the application [Context] and the `@ApplicationScope` [CoroutineScope] (precedent:
 * [com.sidr.launcher.core.android.permission.AndroidPermissionChecker]).
 */
class AndroidDeviceProfiler(
    private val context: Context,
    private val cacheRepository: DeviceProfileCacheRepository,
    private val appScope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() },
) : DeviceProfileProvider {

    @Volatile
    private var cachedProfile: DeviceProfile? = null

    override fun profile(): DeviceProfile {
        cachedProfile?.let { return it }
        val profile = DeviceProfileClassifier.classify(
            ramBytes = totalRamBytes(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
        )
        cachedProfile = profile
        // Fire-and-forget cold-start cache write (lossy LOW_END-vs-rest projection). Never blocks
        // the synchronous read; a failed write just leaves the cache stale.
        appScope.launch {
            cacheRepository.updateCache(DeviceProfileCacheMapping.toCacheEntry(profile, now()))
        }
        return profile
    }

    override fun capability(): DeviceCapability = DeviceProfileClassifier.capability(
        ramBytes = totalRamBytes(),
        cpuCores = Runtime.getRuntime().availableProcessors(),
        nnapiAvailable = Build.VERSION.SDK_INT >= NNAPI_MIN_SDK,
        thermalStatus = currentThermalStatus(),
        isPowerSaveMode = powerManager()?.isPowerSaveMode ?: false,
    )

    private fun totalRamBytes(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0L
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    private fun powerManager(): PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private fun currentThermalStatus(): Int =
        if (Build.VERSION.SDK_INT >= NNAPI_MIN_SDK) {
            powerManager()?.currentThermalStatus ?: DeviceProfileClassifier.NO_THERMAL_SIGNAL
        } else {
            DeviceProfileClassifier.NO_THERMAL_SIGNAL
        }

    private companion object {
        // API 29 (Q): the floor for both getCurrentThermalStatus() and the ORT NNAPI EP window.
        const val NNAPI_MIN_SDK = 29
    }
}
