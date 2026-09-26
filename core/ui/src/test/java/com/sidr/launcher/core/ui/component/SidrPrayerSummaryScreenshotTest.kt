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
 *
 * This class also carries the I18N-1 pseudolocale barrier for the module's tightest layout — see
 * [prayer_summary_pseudolocale].
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

    /**
     * I18N-1 barrier 3 (spec §10.4): the same gallery as [prayer_summary_dark], rendered in the
     * platform `en-XA` pseudolocale, which accents ASCII and pads every string to ~1.4x, so layout
     * that only fits English fails visibly.
     *
     * This is the module's highest-value pseudolocale target. The prayer names and times here are
     * caller-supplied Kotlin literals, but the **status chip is not** — [SidrPrayerSummary] resolves
     * all eleven [SidrPrayerSummaryStatus] values through `sidrString`, so `TZ CONFLICT`,
     * `LOCATION UNAVAILABLE` and `AUTHORITY UNAVAILABLE` are `core/ui` resources rendered as
     * uppercase, letter-spaced chips. That is the tightest layout in the module and the likeliest
     * place for `ru`/`tr` to overflow.
     *
     * Depends on `isPseudoLocalesEnabled = true` in `core/ui/build.gradle.kts`; without it this
     * qualifier silently resolves to plain English, which is what [PseudolocaleBarrierGuardTest]
     * exists to catch.
     *
     * The qualifier carries a **leading `+`** so it merges onto this class's `w360dp-h3200dp-xhdpi`
     * override instead of replacing it. Without the `+` the capture would render at the default
     * viewport, clip after ~5 states, and stop being comparable to [prayer_summary_dark].
     *
     * Limitation: pseudolocale expansion is ASCII accenting and padding. It does **not** exercise
     * Turkish dotted-`İ`, and it does not reproduce real Cyrillic lengths — `КОНФЛИКТ ЧАСОВОГО
     * ПОЯСА` is 25 characters against `TZ CONFLICT`'s 11. A clean capture here is **not** evidence
     * that `ru`/`tr` fit; that is the device pass.
     */
    @Test
    @Config(qualifiers = "+b+en+XA")
    fun prayer_summary_pseudolocale() {
        compose.setContent { SidrTheme(darkTheme = true) { PrayerSummaryGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/prayer_summary_pseudolocale.png")
    }

    private fun capture(dark: Boolean, name: String) {
        compose.setContent { SidrTheme(darkTheme = dark) { PrayerSummaryGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
