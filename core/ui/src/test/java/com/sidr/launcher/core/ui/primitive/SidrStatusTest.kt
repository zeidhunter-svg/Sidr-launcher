package com.sidr.launcher.core.ui.primitive

import com.sidr.launcher.core.ui.theme.SidrDarkColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SidrStatusTest {
    @Test fun status_maps_to_the_fixed_status_token() {
        assertEquals(SidrDarkColors.success, SidrStatus.SUCCESS.color(SidrDarkColors))
        assertEquals(SidrDarkColors.attention, SidrStatus.ATTENTION.color(SidrDarkColors))
        assertEquals(SidrDarkColors.caution, SidrStatus.CAUTION.color(SidrDarkColors))
        assertEquals(SidrDarkColors.danger, SidrStatus.DANGER.color(SidrDarkColors))
        assertEquals(SidrDarkColors.info, SidrStatus.INFO.color(SidrDarkColors))
    }

    @Test fun status_is_never_the_accent() {
        SidrStatus.entries.forEach { assertNotEquals(SidrDarkColors.accent, it.color(SidrDarkColors)) }
    }
}
