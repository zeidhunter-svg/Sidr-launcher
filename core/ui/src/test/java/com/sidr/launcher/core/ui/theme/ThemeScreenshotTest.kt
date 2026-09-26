package com.sidr.launcher.core.ui.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThemeScreenshotTest {

    @get:Rule val compose = createComposeRule()

    @Test fun harness_smoke() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        Text("SIDR", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/harness_smoke.png")
    }

    @Test fun grey_sample_dark() = captureSample(dark = true, name = "grey_sample_dark")
    @Test fun grey_sample_light() = captureSample(dark = false, name = "grey_sample_light")

    // 2026-07-12 accent reactivation — green/amber are full themes (own ground/surface/text/accent);
    // the samples must show the accent hue itself plus the two roles that are pinned across every
    // theme choice: sacred and semantic status (release-gate "accent ≠ status" review).
    @Test fun green_sample_dark() = captureSample(dark = true, name = "green_sample_dark", accent = AccentColor.GREEN)
    @Test fun green_sample_light() = captureSample(dark = false, name = "green_sample_light", accent = AccentColor.GREEN)
    @Test fun amber_sample_dark() = captureSample(dark = true, name = "amber_sample_dark", accent = AccentColor.AMBER)
    @Test fun amber_sample_light() = captureSample(dark = false, name = "amber_sample_light", accent = AccentColor.AMBER)

    private fun captureSample(dark: Boolean, name: String, accent: AccentColor = AccentColor.GREY) {
        compose.setContent {
            SidrTheme(darkTheme = dark, accent = accent) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        Text("SIDR", style = MaterialTheme.typography.titleLarge)
                        Text("FAVORITES", style = MaterialTheme.typography.labelSmall)
                        Text("Open Telegram", style = MaterialTheme.typography.bodyMedium)
                        Text("LOCAL · 14 MS", style = SidrTheme.textStyles.provenance)
                        Text("> ACCENT", color = SidrTheme.colors.accent, style = SidrTheme.textStyles.provenance)
                        Text("DANGER", color = SidrTheme.colors.danger, style = MaterialTheme.typography.labelSmall)
                        Text("There is no deity except Allah", style = SidrTheme.textStyles.sacred)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
