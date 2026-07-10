package com.sidr.launcher.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTokensTest {
    @Test fun dark_grey_tokens_match_spec() {
        assertEquals(Color(0xFF131415), SidrDarkColors.ground)
        assertEquals(Color(0xFFE8E9EB), SidrDarkColors.text)
        assertEquals(Color(0xFF9BA1AB), SidrDarkColors.accent)
        assertEquals(Color(0xFFB8836A), SidrDarkColors.caution)
        assertEquals(Color(0xFFC2695C), SidrDarkColors.danger)
    }

    @Test fun light_grey_tokens_match_spec() {
        assertEquals(Color(0xFFECEDED), SidrLightColors.ground)
        assertEquals(Color(0xFF1C1E21), SidrLightColors.text)
        assertEquals(Color(0xFF5F6773), SidrLightColors.accent)
    }

    @Test fun accent_and_status_are_distinct_tokens() {
        // spec-lock: brand accent is never a status colour
        assertEquals(false, SidrDarkColors.accent == SidrDarkColors.caution)
        assertEquals(false, SidrDarkColors.accent == SidrDarkColors.success)
    }

    @Test fun shapes_are_softened() {
        assertEquals(RoundedCornerShape(10.dp), SidrShapes.medium)   // default 10dp
        assertEquals(RoundedCornerShape(7.dp), SidrShapes.small)     // chips 7dp
        assertEquals(RoundedCornerShape(12.dp), SidrShapes.large)    // modal 12dp
    }

    @Test fun grey_scheme_maps_ground_and_accent() {
        assertEquals(SidrDarkColors.ground, GreyDarkColorScheme.background)
        assertEquals(SidrDarkColors.accent, GreyDarkColorScheme.primary)
        assertEquals(SidrDarkColors.danger, GreyDarkColorScheme.error)
        assertEquals(SidrLightColors.ground, GreyLightColorScheme.background)
    }
}
