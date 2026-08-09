package com.sidr.launcher.core.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DS-6B Task 7 goldens for [SidrPrayerSummary]/[PrayerSummaryGallery]: dark/light + fontScale-2.0
 * (asserts the vertical layout switch, [PRAYER_SUMMARY_VERTICAL_FONT_SCALE]) + RTL smoke.
 *
 * The gallery stacks all 11 [SidrPrayerSummaryStatus] entries (plus a fallback-branch entry) in one
 * unscrolled column, which overflows the module-default `robolectric.properties` viewport
 * (w360dp-h800dp-xhdpi, ~720x1488px) after ~5 states. The height qualifier is overridden here so every
 * state is captured un-clipped in all four goldens (review finding, DS-6B Task 7 fix round 1).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h3200dp-xhdpi")
class SidrPrayerSummaryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun prayer_summary_dark() = capture(dark = true, name = "prayer_summary_dark")
    @Test fun prayer_summary_light() = capture(dark = false, name = "prayer_summary_light")

    @Test fun prayer_summary_fontscale2() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2.0f)) {
                SidrTheme(darkTheme = true) { PrayerSummaryGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/prayer_summary_fontscale2.png")
    }

    @Test fun prayer_summary_rtl() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SidrTheme(darkTheme = true) { PrayerSummaryGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/prayer_summary_rtl.png")
    }

    private fun capture(dark: Boolean, name: String) {
        compose.setContent { SidrTheme(darkTheme = dark) { PrayerSummaryGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
