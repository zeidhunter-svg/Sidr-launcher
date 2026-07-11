package com.sidr.launcher.core.ui.primitive

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
class PrimitivesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun primitives_dark() = capture(dark = true, name = "primitives_dark")
    @Test fun primitives_light() = capture(dark = false, name = "primitives_light")

    @Test fun primitives_fontscale2() {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2.0f)) {
                SidrTheme(darkTheme = true) { PrimitiveGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/primitives_fontscale2.png")
    }

    @Test fun primitives_rtl() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SidrTheme(darkTheme = true) { PrimitiveGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/primitives_rtl.png")
    }

    private fun capture(dark: Boolean, name: String) {
        compose.setContent { SidrTheme(darkTheme = dark) { PrimitiveGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
