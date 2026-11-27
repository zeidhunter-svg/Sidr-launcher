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
 * A4′ acceptance tail, task 8 (K13): the next-alarm chip's own Roborazzi scenario, `next_alarm_chip` — ONE golden, dark
 * theme (Home's), on the theme's ground. Home itself has no goldens and gets none; the chip's place on the date line is
 * `LauncherScreenAlarmChipTest`'s, by semantics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SidrNextAlarmChipScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun next_alarm_chip() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                Box(Modifier.background(SidrTheme.colors.ground).padding(Spacing.md)) {
                    SidrNextAlarmChip(text = "⏰ 07:30", contentDescription = "Next alarm: 07:30", onClick = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/next_alarm_chip.png")
    }
}
