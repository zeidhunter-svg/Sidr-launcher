package com.sidr.launcher.core.android.prayer

import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.sidr.launcher.domain.permission.PermissionChecker
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.permission.PermissionStatus
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationProvider
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round

/**
 * [PrayerLocationProvider] impl: a ONE-SHOT device location read (DS-6B Task 8) — the Block-T/U
 * `AndroidSpeechInputSource`/`LocationSuggestionProvider` precedent for "a plain, Hilt-free Android
 * port impl, wired in :app". Only reachable from the prayer settings screen's explicit opt-in button
 * (never from Home/entry) and only after the user grants [PermissionFeature.PRAYER_LOCATION].
 *
 * **One-shot, never continuous:** reads the freshest *last-known* fix across the same candidate
 * provider set `LocationSuggestionProvider` already uses (GPS/NETWORK/PASSIVE) via a single
 * `getLastKnownLocation` call per provider. No `requestLocationUpdates`/`LocationListener`/callback
 * is ever registered — there is nothing to "keep" or unregister after this function returns.
 *
 * **Rounding happens BEFORE construction, in this class, and nowhere else:** the precise
 * `Location.getLatitude()/getLongitude()` value exists only in the local [freshestLastKnownFix]
 * result; it is rounded to 2 decimal places immediately and only the rounded value is ever used to
 * build the returned [PrayerLocation] (`source = DEVICE`). The precise coordinate is never logged,
 * cached, or passed anywhere else, and — because [PrayerLocation.init] itself requires
 * `round(v * 100.0) / 100.0 == v` — a caller could not construct an unrounded [PrayerLocation] here
 * even by mistake (spec §0.5).
 *
 * **Timezone decision (documented, not silently assumed):** a raw lat/lon fix carries no timezone of
 * its own, and a real coarse-to-timezone geocoding lookup is out of scope for this task. [tzId] uses
 * the device's OWN current default zone ([ZoneId.systemDefault]) as an honest, cheap approximation —
 * correct for the overwhelming common case (the device is where the user is). This is not silently
 * wrong even when it IS wrong: [com.sidr.launcher.domain.prayer.GetPrayerContextUseCase] independently
 * compares the location's [PrayerLocation.tzId] against the device zone and surfaces
 * [com.sidr.launcher.domain.prayer.TimeZoneState.CONFLICT] whenever they diverge (e.g. a travelling
 * user whose device zone hasn't caught up) — visible, never faked (spec §2/§6).
 *
 * Never throws an expected failure: missing permission degrades to `Success(null)` (still a valid "no
 * location chosen" state, not an error); a platform/security failure reading the location service
 * degrades to `Success(null)` or [OperationResult.Failure] — either way the manual city path remains
 * fully usable, and nothing in the launcher breaks (spec §0.4).
 */
class AndroidPrayerLocationProvider(
    private val context: Context,
    private val permissionChecker: PermissionChecker,
) : PrayerLocationProvider {

    override suspend fun currentLocation(): OperationResult<PrayerLocation?> {
        if (permissionChecker.status(PermissionFeature.PRAYER_LOCATION) != PermissionStatus.GRANTED) {
            return OperationResult.Success(null)
        }

        return try {
            val fix = freshestLastKnownFix() ?: return OperationResult.Success(null)

            val lat2dp = roundTo2dp(fix.latitude)
            val lon2dp = roundTo2dp(fix.longitude)
            if (lat2dp !in -90.0..90.0 || lon2dp !in -180.0..180.0) {
                // A malformed/out-of-range platform fix — treat as no usable fix rather than fail.
                return OperationResult.Success(null)
            }

            OperationResult.Success(
                PrayerLocation(
                    label = DEVICE_LOCATION_LABEL,
                    lat2dp = lat2dp,
                    lon2dp = lon2dp,
                    tzId = ZoneId.systemDefault().id,
                    source = PrayerLocationSource.DEVICE,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            // Permission was revoked between the check above and the read (a real race, not
            // hypothetical) — degrade to "no fix", never crash.
            OperationResult.Success(null)
        } catch (e: Exception) {
            OperationResult.Failure(
                OperationError.UnknownError(reason = "prayer_device_location_read_failed"),
            )
        }
    }

    /**
     * The most recent last-known fix across [CANDIDATE_PROVIDERS], or null when none exists. Reads
     * ONLY `getLastKnownLocation` — never registers a listener/callback for continuous updates.
     */
    private fun freshestLastKnownFix(): Location? {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return CANDIDATE_PROVIDERS.mapNotNull { provider ->
            try {
                locationManager.getLastKnownLocation(provider)
            } catch (_: SecurityException) {
                null
            } catch (_: IllegalArgumentException) {
                // Provider not present on this device.
                null
            }
        }.maxByOrNull { it.time }
    }

    private companion object {
        const val DEVICE_LOCATION_LABEL = "Current location"

        fun roundTo2dp(value: Double): Double = round(value * 100.0) / 100.0

        // Same candidate set as `LocationSuggestionProvider` (`:data:repository`). FUSED_PROVIDER
        // (API 31+) deliberately omitted — minSdk is 28; GPS/NETWORK/PASSIVE already cover every
        // device this app runs on.
        val CANDIDATE_PROVIDERS = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }
}
