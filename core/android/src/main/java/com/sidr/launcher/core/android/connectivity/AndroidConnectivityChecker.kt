package com.sidr.launcher.core.android.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Android implementation of the [ConnectivityChecker] port (Block M, Fork P5-7).
 *
 * [isOnline] checks [NetworkCapabilities.NET_CAPABILITY_INTERNET] +
 * [NetworkCapabilities.NET_CAPABILITY_VALIDATED] synchronously on the active network.
 * [connectivity] uses [ConnectivityManager.registerNetworkCallback] via [callbackFlow] to emit the
 * current reachability state on subscription and on every change, then [conflate] + [distinctUntilChanged]
 * to avoid redundant rapid-fire emissions.
 *
 * Plain class (no Hilt annotations); constructed in `:app`'s [ConnectivityModule] with the
 * application [Context]. Not unit-tested in `core/android` (no test deps there — the
 * [AndroidPermissionChecker] precedent); router logic is fully covered via [FakeConnectivityChecker].
 */
class AndroidConnectivityChecker(
    private val context: Context,
) : ConnectivityChecker {

    private val manager: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override val connectivity: Flow<Boolean> = callbackFlow {
        val cm = manager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(isOnline())
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                )
            }
        }

        trySend(isOnline())

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()
}
