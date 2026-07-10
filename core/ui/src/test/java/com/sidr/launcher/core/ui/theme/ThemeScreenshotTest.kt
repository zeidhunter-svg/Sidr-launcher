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
}
