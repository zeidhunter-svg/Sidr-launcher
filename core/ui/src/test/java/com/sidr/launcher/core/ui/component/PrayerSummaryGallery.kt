package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.primitive.SidrSystemLabel
import com.sidr.launcher.core.ui.theme.SidrTheme

/**
 * Preview/screenshot gallery for [SidrPrayerSummary] (DS-6B Task 7). Covers all 11
 * [SidrPrayerSummaryStatus] values: the six that keep a live schedule (verified/cached/manual
 * location/timezone-conflict/updating, one cell marked [SidrPrayerTimeUi.isNext]) and the five
 * no-data/failure states that render only the status chip + provenance (spec §8), plus one extra entry
 * exercising [SidrPrayerSummary]'s `else if (locationLabel != null)` fallback branch (empty prayers +
 * blank provenance + a non-null [SidrPrayerSummary]'s locationLabel — review finding, fix round 1).
 */
@Composable
fun PrayerSummaryGallery() {
    val schedule = listOf(
        SidrPrayerTimeUi(name = "Fajr", time = "05:12"),
        SidrPrayerTimeUi(name = "Dhuhr", time = "12:34"),
        SidrPrayerTimeUi(name = "Asr", time = "15:42", isNext = true),
        SidrPrayerTimeUi(name = "Maghrib", time = "18:03"),
        SidrPrayerTimeUi(name = "Isha", time = "19:33"),
    )

    Surface(color = SidrTheme.colors.ground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SidrSystemLabel("VERIFIED CURRENT")
            SidrPrayerSummary(
                prayers = schedule,
                status = SidrPrayerSummaryStatus.VerifiedCurrent,
                provenance = "astronomical authority",
                locationLabel = "Istanbul, TR",
            )

            SidrSystemLabel("CACHED FRESH")
            SidrPrayerSummary(
                prayers = schedule,
                status = SidrPrayerSummaryStatus.CachedFresh,
                provenance = "cached · 2h ago",
                onOpenDetails = {},
            )

            SidrSystemLabel("CACHED STALE")
            SidrPrayerSummary(
                prayers = schedule,
                status = SidrPrayerSummaryStatus.CachedStale,
                provenance = "cached · 9d ago",
            )

            SidrSystemLabel("MANUAL LOCATION")
            SidrPrayerSummary(
                prayers = schedule,
                status = SidrPrayerSummaryStatus.ManualLocation,
                provenance = "manual location",
                locationLabel = "Cairo, EG",
            )

            SidrSystemLabel("TIMEZONE CONFLICT")
            SidrPrayerSummary(
                prayers = schedule,
                status = SidrPrayerSummaryStatus.TimezoneConflict,
                provenance = "astronomical authority",
                locationLabel = "device tz mismatch",
            )

            SidrSystemLabel("UPDATING")
            SidrPrayerSummary(
                prayers = schedule,
                status = SidrPrayerSummaryStatus.Updating,
                provenance = "cached · refreshing",
            )

            SidrSystemLabel("LOCATION UNAVAILABLE")
            SidrPrayerSummary(
                prayers = emptyList(),
                status = SidrPrayerSummaryStatus.LocationUnavailable,
                provenance = "",
            )

            SidrSystemLabel("METHOD REQUIRED")
            SidrPrayerSummary(
                prayers = emptyList(),
                status = SidrPrayerSummaryStatus.MethodRequired,
                provenance = "",
            )

            SidrSystemLabel("AUTHORITY UNAVAILABLE")
            SidrPrayerSummary(
                prayers = emptyList(),
                status = SidrPrayerSummaryStatus.AuthorityUnavailable,
                provenance = "last known · offline",
            )

            SidrSystemLabel("CALCULATION FAILED")
            SidrPrayerSummary(
                prayers = emptyList(),
                status = SidrPrayerSummaryStatus.CalculationFailed,
                provenance = "",
            )

            SidrSystemLabel("NO DATA")
            SidrPrayerSummary(
                prayers = emptyList(),
                status = SidrPrayerSummaryStatus.NoData,
                provenance = "",
            )

            SidrSystemLabel("NO DATA — LOCATION FALLBACK")
            SidrPrayerSummary(
                prayers = emptyList(),
                status = SidrPrayerSummaryStatus.NoData,
                provenance = "",
                locationLabel = "Amman, JO",
            )
        }
    }
}
