package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Test

class TypographyRoleTest {
    @Test fun system_and_sacred_use_the_right_families() {
        assertEquals(JetBrainsMono, SidrTextStyles.Default.command.fontFamily)
        assertEquals(JetBrainsMono, SidrTextStyles.Default.provenance.fontFamily)
        assertEquals(FontFamily.Serif, SidrTextStyles.Default.sacred.fontFamily)
    }
    @Test fun material_body_is_sans_labels_are_mono() {
        assertEquals(FontFamily.SansSerif, SidrTypography.bodyMedium.fontFamily)
        assertEquals(JetBrainsMono, SidrTypography.labelSmall.fontFamily)
    }
}
