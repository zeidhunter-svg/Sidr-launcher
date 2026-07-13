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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SidrMemoryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun memory_dark() = capture(dark = true, name = "memory_dark")
    @Test fun memory_light() = capture(dark = false, name = "memory_light")

    @Test fun memory_fontscale2() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2.0f)) {
                SidrTheme(darkTheme = true) { MemoryGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/memory_fontscale2.png")
    }

    @Test fun memory_rtl() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SidrTheme(darkTheme = true) { MemoryGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/memory_rtl.png")
    }

    private fun capture(dark: Boolean, name: String) {
        compose.setContent { SidrTheme(darkTheme = dark) { MemoryGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
