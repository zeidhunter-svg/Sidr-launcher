package com.sidr.launcher.core.ui.primitive

import org.junit.Assert.assertEquals
import org.junit.Test

class ProvenanceFormatTest {
    @Test fun display_uppercases_and_joins_with_middot() {
        assertEquals("LOCAL · 14 MS", provenanceDisplay("local", listOf("14 ms")))
        assertEquals(
            "DIYANET · ISTANBUL · UPDATED 2H AGO",
            provenanceDisplay("Diyanet", listOf("Istanbul", "updated 2h ago")),
        )
        assertEquals("LOCAL", provenanceDisplay("local", emptyList()))
    }

    @Test fun display_drops_blank_segments() {
        assertEquals("LOCAL · 14 MS", provenanceDisplay("local", listOf("", "14 ms", "   ")))
    }

    /**
     * The spoken description is `<translated "source"> <segments>` (I18N-1: one resource with a
     * positional placeholder, never a concatenation). This pins the segment half; the composed
     * sentence is asserted end-to-end by `PrimitiveSemanticsTest`.
     */
    @Test fun description_segments_are_a_comma_list_not_glyphs() {
        assertEquals("LOCAL, 14 MS", provenanceSegments("local", listOf("14 ms")))
        assertEquals(
            "DIYANET, ISTANBUL, UPDATED 2H AGO",
            provenanceSegments("Diyanet", listOf("Istanbul", "updated 2h ago")),
        )
    }
}
