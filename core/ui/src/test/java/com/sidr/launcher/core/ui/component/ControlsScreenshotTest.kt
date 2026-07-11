package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
            SidrTheme(darkTheme = true) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SidrPreviewBadge()
                    SidrPreviewBanner()
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/preview_controls.png")
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
