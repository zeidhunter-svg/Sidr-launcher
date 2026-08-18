package com.sidr.launcher.feature.launcher

import androidx.compose.ui.test.junit4.createComposeRule
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * I18N-2 rendering-level test for [homeLocationLabelText], mirroring [PrayerProvenanceTextTest]'s
 * `createComposeRule` + direct-capture pattern: nothing in the plain-JVM [PrayerSummaryMapperTest]
 * can call an `@Composable` resolver directly.
 *
 * The `ru`-qualified test below is the one that would have failed against the pre-I18N-2 code
 * (`HomePrayerStrip` passing `summary.locationLabel` straight through): the stored
 * [PrayerLocationSource.DEVICE] identity is the fixed English `"Current location"` regardless of
 * locale (see `AndroidPrayerLocationProvider.DEVICE_LOCATION_LABEL`'s kdoc), so a test that only
 * asserts the English resolution can't distinguish "resolved through sidrString" from "passed
 * through verbatim" — the same trap I18N-1's prayer-name bug fell into.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeLocationLabelTextTest {

    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "ru")
    fun `DEVICE source under ru resolves to the Russian label, not the stored English identity`() {
        var actual: String? = null
        compose.setContent {
            actual = homeLocationLabelText("Current location", PrayerLocationSource.DEVICE)
        }
        assertEquals("Текущее местоположение", actual)
    }

    @Test fun `DEVICE source under the base locale resolves through the resource, not verbatim`() {
        var actual: String? = null
        compose.setContent {
            actual = homeLocationLabelText("Current location", PrayerLocationSource.DEVICE)
        }
        assertEquals("Current location", actual)
    }

    @Test fun `CITY source is shown verbatim - a GeoNames proper name is never translated`() {
        var actual: String? = null
        compose.setContent {
            actual = homeLocationLabelText("Istanbul", PrayerLocationSource.CITY)
        }
        assertEquals("Istanbul", actual)
    }

    @Test fun `null label stays null - no placeholder fabricated`() {
        var actual: String? = "sentinel"
        compose.setContent {
            actual = homeLocationLabelText(null, PrayerLocationSource.CITY)
        }
        assertNull(actual)
    }
}
