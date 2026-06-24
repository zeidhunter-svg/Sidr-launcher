package com.sidr.launcher.domain.connectivity

import kotlinx.coroutines.flow.Flow

/**
 * Network-reachability port. The Android implementation (over `ConnectivityManager`) lives in
 * `core/android` (precedent: `AndroidPermissionChecker`); the router (Block M) consults it to choose
 * cloud vs static fallback. Launcher core never depends on this — it is only on the assistant path.
 */
interface ConnectivityChecker {
    /** Best-effort synchronous snapshot of current reachability. */
    fun isOnline(): Boolean

    /** Observable reachability; emits the current state on subscription, then on every change. */
    val connectivity: Flow<Boolean>
}
