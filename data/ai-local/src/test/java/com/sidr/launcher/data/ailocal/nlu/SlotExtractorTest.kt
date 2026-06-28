package com.sidr.launcher.data.ailocal.nlu

import org.junit.Assert.assertEquals
import org.junit.Test

/** P2a: heuristic verb/filler-strip slot extraction (pure, no session). */
class SlotExtractorTest {

    @Test
    fun strips_single_leading_verb() {
        assertEquals("telegram", SlotExtractor.extractSlot("open telegram"))
        assertEquals("camera", SlotExtractor.extractSlot("launch camera"))
        assertEquals("spotify", SlotExtractor.extractSlot("fire up spotify"))
    }

    @Test
    fun strips_longest_matching_leading_phrase_first() {
        assertEquals("weather", SlotExtractor.extractSlot("search for weather"))
        assertEquals("the news", SlotExtractor.extractSlot("look up the news"))
        assertEquals("maps", SlotExtractor.extractSlot("can you please maps"))
    }

    @Test
    fun keeps_whole_input_when_no_leading_phrase() {
        assertEquals("telegram", SlotExtractor.extractSlot("telegram"))
        assertEquals("my favourite app", SlotExtractor.extractSlot("my favourite app"))
    }

    @Test
    fun bare_verb_keeps_whole_input() {
        assertEquals("open", SlotExtractor.extractSlot("open"))
        assertEquals("search", SlotExtractor.extractSlot("search"))
    }

    @Test
    fun does_not_strip_a_verb_that_is_a_substring_not_a_leading_word() {
        assertEquals("opener app", SlotExtractor.extractSlot("opener app"))
    }
}
