package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
 * A4′ acceptance tail, task 9 (K5, owner ruling 7): the finished-plan card's two buttons are one size. ONE new golden,
 * `result_surface_equal_actions` (dark, on the theme's ground) — a short «Close» beside a longer «Undo» label; no other
 * golden of that card moves with the fix round's task 4, which makes every two-button card equal-width.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SidrResultSurfaceScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun result_surface_equal_actions() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                Box(Modifier.background(SidrTheme.colors.ground).padding(Spacing.md)) {
                    SidrResultSurface(
                        tone = SidrResultTone.Completed,
                        title = "Plan finished",
                        primaryAction = SidrSurfaceAction("Close") {},
                        secondaryAction = SidrSurfaceAction("Undo all") {},
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/result_surface_equal_actions.png")
    }
}
