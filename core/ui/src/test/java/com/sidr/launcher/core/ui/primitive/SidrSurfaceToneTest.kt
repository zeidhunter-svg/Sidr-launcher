package com.sidr.launcher.core.ui.primitive

import com.sidr.launcher.core.ui.theme.SidrDarkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SidrSurfaceToneTest {
    @Test fun tone_maps_to_background() {
        assertEquals(SidrDarkColors.ground, SidrSurfaceTone.GROUND.background(SidrDarkColors))
        assertEquals(SidrDarkColors.surface, SidrSurfaceTone.SURFACE.background(SidrDarkColors))
        assertEquals(SidrDarkColors.raised, SidrSurfaceTone.RAISED.background(SidrDarkColors))
        assertEquals(SidrDarkColors.surface, SidrSurfaceTone.SACRED.background(SidrDarkColors))
        assertEquals(SidrDarkColors.surface, SidrSurfaceTone.RISK.background(SidrDarkColors))
    }

    @Test fun only_risk_has_a_border() {
        assertNull(SidrSurfaceTone.SURFACE.borderColor(SidrDarkColors))
        assertNull(SidrSurfaceTone.SACRED.borderColor(SidrDarkColors))
        assertEquals(SidrDarkColors.caution, SidrSurfaceTone.RISK.borderColor(SidrDarkColors))
    }
}
