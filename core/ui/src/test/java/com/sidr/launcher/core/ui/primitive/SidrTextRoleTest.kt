package com.sidr.launcher.core.ui.primitive

import com.sidr.launcher.core.ui.theme.SidrDarkColors
import com.sidr.launcher.core.ui.theme.SidrTextStyles
import com.sidr.launcher.core.ui.theme.SidrTypography
import org.junit.Assert.assertEquals
import org.junit.Test

class SidrTextRoleTest {
    @Test fun role_maps_to_the_right_text_style() {
        assertEquals(SidrTextStyles.Default.command, SidrTextRole.COMMAND.textStyle(SidrTextStyles.Default, SidrTypography))
        assertEquals(SidrTextStyles.Default.provenance, SidrTextRole.PROVENANCE.textStyle(SidrTextStyles.Default, SidrTypography))
        assertEquals(SidrTextStyles.Default.caption, SidrTextRole.CAPTION.textStyle(SidrTextStyles.Default, SidrTypography))
        assertEquals(SidrTextStyles.Default.sacred, SidrTextRole.SACRED.textStyle(SidrTextStyles.Default, SidrTypography))
        assertEquals(SidrTypography.bodyMedium, SidrTextRole.HUMAN_BODY.textStyle(SidrTextStyles.Default, SidrTypography))
        assertEquals(SidrTypography.labelSmall, SidrTextRole.LABEL.textStyle(SidrTextStyles.Default, SidrTypography))
    }

    @Test fun role_maps_to_the_right_default_color() {
        assertEquals(SidrDarkColors.text, SidrTextRole.COMMAND.defaultColor(SidrDarkColors))
        assertEquals(SidrDarkColors.faint, SidrTextRole.PROVENANCE.defaultColor(SidrDarkColors))
        assertEquals(SidrDarkColors.dim, SidrTextRole.CAPTION.defaultColor(SidrDarkColors))
        assertEquals(SidrDarkColors.dim, SidrTextRole.SYSTEM.defaultColor(SidrDarkColors))
        assertEquals(SidrDarkColors.sacred, SidrTextRole.SACRED.defaultColor(SidrDarkColors))
    }
}
