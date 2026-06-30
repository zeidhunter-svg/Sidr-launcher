package com.sidr.launcher.data.repository.suggestions

import android.content.Context
import android.location.LocationManager
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.permission.PermissionChecker
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionStatus
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionContext
import com.sidr.launcher.domain.suggestions.SuggestionProvider
import com.sidr.launcher.domain.suggestions.SuggestionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Opt-in provider: a single generic "nearby places" nudge from the last-known location fix
 * (Phase 7, Block U2).
 *
 * Gated on [PermissionFeature.LOCATION_SUGGESTIONS] — returns empty immediately when not GRANTED, and
 * never throws. Only used to decide *whether* a recent fix exists; the raw coordinates never leave this
 * provider — the emitted [Suggestion] is a fixed, generic label, no coarse/fine bucket of the actual
 * position is computed or cached (Fork F7-9).
 */
class LocationSuggestionProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SuggestionProvider {

    override suspend fun provide(context: SuggestionContext): List<Suggestion> {
        if (permissionChecker.status(PermissionFeature.LOCATION_SUGGESTIONS) != PermissionStatus.GRANTED) {
            return emptyList()
        }
        return withContext(ioDispatcher) {
            try {
                if (hasRecentFix()) {
                    listOf(
                        Suggestion(
                            label = "Nearby places",
                            actionId = MAPS_APP_PACKAGE,
                            source = SuggestionSource.LOCATION,
                            score = 0.70,
                        ),
                    )
                } else {
                    emptyList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    private fun hasRecentFix(): Boolean {
        val locationManager = this.context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return CANDIDATE_PROVIDERS.any { provider ->
            try {
                locationManager.getLastKnownLocation(provider) != null
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalArgumentException) {
                // Provider not present on this device.
                false
            }
        }
    }

    private companion object {
        const val MAPS_APP_PACKAGE = "com.google.android.apps.maps"
        // FUSED_PROVIDER (API 31+) deliberately omitted — minSdk is 28; GPS/NETWORK/PASSIVE already
        // cover every device this app runs on.
        val CANDIDATE_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }
}
