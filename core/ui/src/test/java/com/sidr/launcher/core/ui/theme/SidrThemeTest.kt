package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun theme_provides_grey_regardless_of_accent() {
        var bg = Color.Unspecified
        var sidrAccent = Color.Unspecified
        compose.setContent {
            SidrTheme(darkTheme = true, accent = AccentColor.AMBER) {
                bg = MaterialTheme.colorScheme.background
                sidrAccent = SidrTheme.colors.accent
            }
        }
        assertEquals(SidrDarkColors.ground, bg)          // amber ignored → grey ground
        assertEquals(SidrDarkColors.accent, sidrAccent)  // SidrTheme.colors exposed
    }
}
