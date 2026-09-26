package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun grey_theme_matches_the_fixed_soft_grey_base() {
        var bg = Color.Unspecified
        var sidrAccent = Color.Unspecified
        compose.setContent {
            SidrTheme(darkTheme = true, accent = AccentColor.GREY) {
                bg = MaterialTheme.colorScheme.background
                sidrAccent = SidrTheme.colors.accent
            }
        }
        assertEquals(SidrDarkColors.ground, bg)
        assertEquals(SidrDarkColors.accent, sidrAccent)
    }

    @Test fun green_theme_changes_ground_and_accent_but_not_sacred_or_status() {
        var accent = Color.Unspecified
        var ground = Color.Unspecified
        var sacred = Color.Unspecified
        var success = Color.Unspecified
        var primary = Color.Unspecified
        compose.setContent {
            SidrTheme(darkTheme = true, accent = AccentColor.GREEN) {
                accent = SidrTheme.colors.accent
                ground = MaterialTheme.colorScheme.background
                sacred = SidrTheme.colors.sacred
                success = SidrTheme.colors.success
                primary = MaterialTheme.colorScheme.primary
            }
        }

        // Green is a full theme now: both the accent hue AND the ground really change.
        assertNotEquals(SidrDarkColors.accent, accent)
        assertNotEquals(SidrDarkColors.ground, ground)
        // Material's primary tracks the SIDR accent role (not just SidrTheme.colors).
        assertEquals(accent, primary)
        // The Shahada's colour and the fixed status roles never move, no matter the theme.
        assertEquals(SidrDarkColors.sacred, sacred)
        assertEquals(SidrDarkColors.success, success)
    }

    @Test fun amber_theme_changes_ground_and_accent_but_not_sacred_or_status() {
        var accent = Color.Unspecified
        var ground = Color.Unspecified
        var sacred = Color.Unspecified
        var danger = Color.Unspecified
        compose.setContent {
            SidrTheme(darkTheme = true, accent = AccentColor.AMBER) {
                accent = SidrTheme.colors.accent
                ground = MaterialTheme.colorScheme.background
                sacred = SidrTheme.colors.sacred
                danger = SidrTheme.colors.danger
            }
        }

        assertNotEquals(SidrDarkColors.accent, accent)
        assertNotEquals(SidrDarkColors.ground, ground)
        assertEquals(SidrDarkColors.sacred, sacred)
        assertEquals(SidrDarkColors.danger, danger)
    }

    @Test fun green_and_amber_themes_are_distinct() {
        var greenAccent = Color.Unspecified
        var greenSurface = Color.Unspecified
        compose.setContent {
            SidrTheme(darkTheme = true, accent = AccentColor.GREEN) {
                greenAccent = SidrTheme.colors.accent
                greenSurface = SidrTheme.colors.surface
            }
        }
        // Re-derive amber's palette directly (no second setContent in this test rule instance).
        // Dark `ground` is deliberately shared between the two (faithful to the AIL-0 source
        // palette, where both dark schemes used the same near-black base) — `accent`/`surface`
        // are where the two themes actually diverge.
        val amber = sidrColorsFor(AccentColor.AMBER, darkTheme = true)
        assertNotEquals(greenAccent, amber.accent)
        assertNotEquals(greenSurface, amber.surface)
    }

    @Test fun light_mode_still_resolves_a_distinct_palette_per_theme() {
        val grey = sidrColorsFor(AccentColor.GREY, darkTheme = false)
        val green = sidrColorsFor(AccentColor.GREEN, darkTheme = false)
        val amber = sidrColorsFor(AccentColor.AMBER, darkTheme = false)
        assertNotEquals(grey.ground, green.ground)
        assertNotEquals(grey.ground, amber.ground)
        assertNotEquals(green.ground, amber.ground)
        // Sacred and status still pinned to the grey light palette across all three.
        assertEquals(SidrLightColors.sacred, green.sacred)
        assertEquals(SidrLightColors.sacred, amber.sacred)
        assertEquals(SidrLightColors.success, green.success)
        assertEquals(SidrLightColors.success, amber.success)
    }
}
