package com.sidr.launcher.domain.permission

/**
 * Port: reports whether the Android permission backing a [PermissionFeature] is currently held.
 *
 * Implemented in `core/android` over `ContextCompat.checkSelfPermission`; faked in `:core:testing`.
 * A self-check cannot observe the "permanently denied" state, so this returns only
 * [PermissionStatus.GRANTED] or [PermissionStatus.DENIED] — the request flow refines DENIED into
 * [PermissionStatus.PERMANENTLY_DENIED] using the Activity rationale signal.
 *
 * Synchronous: a permission check is a cheap in-process call and is read on the UI path when a
 * feature is triggered (never at startup).
 */
interface PermissionChecker {
    fun status(feature: PermissionFeature): PermissionStatus
}
