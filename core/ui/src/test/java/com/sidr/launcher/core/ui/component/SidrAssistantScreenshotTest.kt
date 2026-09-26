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
class SidrAssistantScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun assistant_dark() = capture(dark = true, name = "assistant_dark")
    @Test fun assistant_light() = capture(dark = false, name = "assistant_light")

    @Test fun assistant_fontscale2() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2.0f)) {
                SidrTheme(darkTheme = true) { AssistantGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/assistant_fontscale2.png")
    }

    @Test fun assistant_rtl() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                SidrTheme(darkTheme = true) { AssistantGallery() }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/assistant_rtl.png")
    }

    /**
     * I18N-1 barrier 3 (spec §10.4): the same gallery as [assistant_dark], rendered in the platform
     * `en-XA` pseudolocale, which accents ASCII and pads every string to ~1.4x. What moves here are
     * the resource-backed strings `SidrAssistantComposer`/`SidrStreamingIndicator`/`SidrErrorSurface`
     * read through `sidrString` — the composer placeholder, the streaming label, the FAILED chip and
     * the WHAT/WHY/NEXT labels. The gallery's own prose is caller-supplied Kotlin literal and stays
     * English, exactly as a feature's runtime copy would come from its own module's resources.
     *
     * Depends on `isPseudoLocalesEnabled = true` in `core/ui/build.gradle.kts`.
     *
     * Limitation: pseudolocale expansion is ASCII accenting and padding — it does not exercise
     * Turkish dotted-`İ` and does not reproduce real Cyrillic string lengths. A clean capture here
     * is not evidence that `ru`/`tr` fit.
     */
    @Test
    @Config(qualifiers = "+b+en+XA")
    fun assistant_pseudolocale() {
        compose.setContent {
            SidrTheme(darkTheme = true) { AssistantGallery() }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/assistant_pseudolocale.png")
    }

    private fun capture(dark: Boolean, name: String) {
        compose.setContent { SidrTheme(darkTheme = dark) { AssistantGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
