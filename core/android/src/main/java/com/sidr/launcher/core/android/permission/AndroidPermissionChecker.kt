package com.sidr.launcher.core.android.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.sidr.launcher.domain.permission.PermissionChecker
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionStatus

/**
 * Android implementation of the [PermissionChecker] port (Block G).
 *
 * This is the single place that maps a [PermissionFeature] to its concrete manifest permission
 * string — that platform knowledge is kept out of `:domain`. Reports GRANTED/DENIED via
 * `checkSelfPermission`; never reports PERMANENTLY_DENIED (a self-check cannot observe it — the
 * request flow refines that in the UI layer).
 *
 * Note: `SET_WALLPAPER` is a *normal* (install-time) permission, so on a manifest-declared build
 * it reports GRANTED without a runtime dialog. The request contract is still exercised in the
 * education flow; the denial path is meaningful for the dangerous (dormant) permissions.
 *
 * Plain class (no Hilt annotations) so `core/android` stays DI-framework-free; constructed and
 * provided in `:app` with the application [Context].
 */
class AndroidPermissionChecker(
    private val context: Context,
) : PermissionChecker {

    override fun status(feature: PermissionFeature): PermissionStatus {
        val permission = manifestPermission(feature) ?: return PermissionStatus.DENIED
        val held = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        return if (held) PermissionStatus.GRANTED else PermissionStatus.DENIED
    }

    /**
     * Manifest permission string backing each feature. Centralised here (Android layer) so the
     * domain enum stays platform-agnostic. Accessibility is intentionally not represented.
     */
    private fun manifestPermission(feature: PermissionFeature): String? = when (feature) {
        PermissionFeature.WALLPAPER -> Manifest.permission.SET_WALLPAPER
        PermissionFeature.VOICE_INPUT -> Manifest.permission.RECORD_AUDIO
        PermissionFeature.CALENDAR_SUGGESTIONS -> Manifest.permission.READ_CALENDAR
        PermissionFeature.LOCATION_SUGGESTIONS -> Manifest.permission.ACCESS_FINE_LOCATION
    }
}
