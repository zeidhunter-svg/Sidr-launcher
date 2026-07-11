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

    @Test fun description_is_a_composed_sentence_not_glyphs() {
        assertEquals("source LOCAL, 14 MS", provenanceDescription("local", listOf("14 ms")))
        assertEquals(
            "source DIYANET, ISTANBUL, UPDATED 2H AGO",
            provenanceDescription("Diyanet", listOf("Istanbul", "updated 2h ago")),
        )
    }
}
