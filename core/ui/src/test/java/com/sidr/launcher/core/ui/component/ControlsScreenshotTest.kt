package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * DS-3 control gallery Roborazzi harness. Mirrors the DS-2 primitive harness: dark/light, font-scale 2.0,
 * and RTL. Goldens live under `src/test/screenshots/`. Starts smoke-only; states grow with each task.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ControlsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun controls_dark() = capture(dark = true, name = "controls_dark")
    @Test fun controls_light() = capture(dark = false, name = "controls_light")

    @Test fun preview_controls() {
        compose.setContent {
            SidrTheme(darkTheme = true) { PreviewControlsContent() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/preview_controls.png")
    }

    @Test fun preview_controls_light() {
        compose.setContent {
            SidrTheme(darkTheme = false) { PreviewControlsContent() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/preview_controls_light.png")
    }

    @Test fun preview_controls_fontscale2() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2.0f)) {
                SidrTheme(darkTheme = true) { PreviewControlsContent() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/preview_controls_fontscale2.png")
    }

    @Test fun preview_controls_rtl() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SidrTheme(darkTheme = true) { PreviewControlsContent() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/preview_controls_rtl.png")
    }

    @Test fun universal_input_idle() = captureInput(dark = true, name = "universal_input_idle", value = "")
    @Test fun universal_input_typing() = captureInput(dark = true, name = "universal_input_typing", value = "open telegram")
    @Test fun universal_input_light() = captureInput(dark = false, name = "universal_input_light", value = "search something")

    @Test fun controls_fontscale2() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2.0f)) {
                SidrTheme(darkTheme = true) { ControlGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/controls_fontscale2.png")
    }

    @Test fun controls_rtl() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SidrTheme(darkTheme = true) { ControlGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/controls_rtl.png")
    }

    /**
     * I18N-1 barrier 3 (spec §10.4): the same gallery as [controls_dark], rendered in the platform
     * `en-XA` pseudolocale, which accents ASCII and pads every string to ~1.4x, so layout that only
     * fits English fails visibly. Depends on `isPseudoLocalesEnabled = true` in
     * `core/ui/build.gradle.kts` — without it this qualifier silently resolves to plain English.
     *
     * The qualifier carries a **leading `+`** so it merges onto the module's `robolectric.properties`
     * viewport (`w360dp-h800dp-xhdpi`) instead of replacing it; without the `+` the capture would
     * render at a different screen size and stop being comparable to [controls_dark].
     *
     * **Known-vacuous as recorded (I18N-1 Task 4 finding).** `controls_pseudolocale.png` is currently
     * *byte-identical* to `controls_dark.png`. [ControlGallery]'s only resource-backed strings live in
     * its `SidrUniversalInput`, `SidrPreview` and `SidrActionGate` sections, and all three sit below
     * the 800dp fold — the capture ends in the CHIPS row, and everything above it is a Kotlin literal
     * passed in by the gallery. So this golden pins a real rendering path but presently proves
     * nothing about expansion. Raising the viewport would fix it, but that would move the four
     * existing `controls_*` goldens, which this block forbids. Resolve it by capturing the gallery's
     * lower half, or by pointing the barrier at a gallery whose resource strings are on screen (see
     * the notes on [SidrMemoryScreenshotTest] and [SidrPrayerSummaryScreenshotTest]).
     *
     * The live barrier today is `assistant_pseudolocale`, which does move.
     *
     * Limitation: pseudolocale expansion is ASCII accenting and padding. It does **not** exercise
     * Turkish dotted-`İ`, and it does not reproduce real Cyrillic lengths (`КОНФЛИКТ ЧАСОВОГО ПОЯСА`
     * is 25 chars against `TZ CONFLICT`'s 11 on an uppercase status chip). A clean capture here is
     * not evidence that `ru`/`tr` fit; that is the device pass.
     */
    @Test
    @Config(qualifiers = "+b+en+XA")
    fun controls_pseudolocale() {
        compose.setContent {
            SidrTheme(darkTheme = true) { ControlGallery() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/controls_pseudolocale.png")
    }

    @Composable
    private fun PreviewControlsContent() {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SidrPreviewBadge()
            SidrPreviewBanner()
        }
    }

    private fun capture(dark: Boolean, name: String) {
        compose.setContent { SidrTheme(darkTheme = dark) { ControlGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun captureInput(dark: Boolean, name: String, value: String) {
        val typing = value.isNotEmpty()
        compose.setContent {
            SidrTheme(darkTheme = dark) {
                SidrUniversalInput(
                    value = value,
                    onValueChange = {},
                    onSubmit = {},
                    state = if (typing) SidrUniversalInputState.Typing else SidrUniversalInputState.Idle,
                    voiceAvailable = true,
                    onVoiceClick = {},
                    onClearClick = if (typing) ({}) else null,
                    routeContent = if (typing) {
                        {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                SidrRouteChip("WEB", selected = false, onClick = {})
                                SidrRouteChip("ASK", selected = false, onClick = {})
                                SidrRouteChip("SITE", selected = false, onClick = {})
                            }
                        }
                    } else null,
                    supportingText = "Local · offline",
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
