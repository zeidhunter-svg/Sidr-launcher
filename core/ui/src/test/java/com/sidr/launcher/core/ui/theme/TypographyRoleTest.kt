package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the tri-font contract (Master Plan §6.2, as amended by DS-11 B 2026-08-10).
 *
 * The boundary these tests defend is *where mono stops*. Before DS-11 mono owned the whole interface
 * shell; it now owns exactly the command line and the provenance line, and every other role is sans.
 * That line is easy to erode one style at a time, so it is asserted here rather than left to the doc.
 */
class TypographyRoleTest {

    @Test fun command_and_provenance_are_the_only_mono_roles() {
        assertEquals(JetBrainsMono, SidrTextStyles.Default.command.fontFamily)
        assertEquals(JetBrainsMono, SidrTextStyles.Default.provenance.fontFamily)

        // DS-11 B: everything else in the role set left mono.
        assertEquals(SidrSans, SidrTextStyles.Default.system.fontFamily)
        assertEquals(SidrSans, SidrTextStyles.Default.caption.fontFamily)
        assertEquals(FontFamily.Serif, SidrTextStyles.Default.sacred.fontFamily)
    }

    @Test fun material_body_title_and_label_slots_are_all_sans() {
        assertEquals(SidrSans, SidrTypography.bodyMedium.fontFamily)
        assertEquals(SidrSans, SidrTypography.bodyLarge.fontFamily)
        assertEquals(SidrSans, SidrTypography.titleMedium.fontFamily)
        assertEquals(SidrSans, SidrTypography.titleLarge.fontFamily)
        // The label slots are the DS-11 B move: chips, app labels and section headers used to be mono.
        assertEquals(SidrSans, SidrTypography.labelLarge.fontFamily)
        assertEquals(SidrSans, SidrTypography.labelSmall.fontFamily)
    }

    @Test fun sans_is_bundled_not_the_platform_default() {
        // The shell must not inherit whichever face the OEM ships — Latin/Cyrillic/Greek/Turkish all
        // have to come from one known file for the typography to be predictable across devices.
        assertNotEquals(FontFamily.SansSerif, SidrSans)
        assertNotEquals(FontFamily.Default, SidrSans)
    }

    @Test fun sans_roles_do_not_inherit_monos_wide_tracking() {
        // The wide CRT tracking existed to loosen mono's dense fixed advance. Carried onto a
        // proportional face it just reads as spaced-out text, so it must stay well below the old
        // 1.4sp/1.6sp once the role is sans.
        assertTrue(
            "system tracking should be relaxed for sans",
            SidrTextStyles.Default.system.letterSpacing.value < 1.0f,
        )
        assertTrue(
            "labelSmall tracking should be relaxed for sans",
            SidrTypography.labelSmall.letterSpacing.value < 1.0f,
        )
    }
}
