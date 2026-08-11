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
 * **No `memory_pseudolocale` capture — scope limit, NOT a statement that one is worthless
 * (I18N-1 Task 4, spec §10.4).** Task 4 was budgeted exactly two new goldens, `controls_` and
 * `assistant_`, and adding a third was outside its authority.
 *
 * Be clear about what that leaves on the table: the aliases and app labels here are caller-supplied
 * Kotlin literals, but the surrounding chrome is not — [SidrMemoryItem], [SidrMemoryDisclosure] and
 * [SidrForgetGate] read roughly two dozen `core/ui` strings through `sidrString` (the provenance
 * vocabulary, the learning-progress copy, the gate's confirm/cancel labels), all of which `en-XA`
 * would expand.
 *
 * So this gallery is a genuine pseudolocale target. It is deferred, not rejected — see the Task 4
 * report.
 */
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
