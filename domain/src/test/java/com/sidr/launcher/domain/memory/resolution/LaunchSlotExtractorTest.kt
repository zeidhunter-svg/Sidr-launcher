package com.sidr.launcher.domain.memory.resolution

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchSlotExtractorTest {
    private fun slot(s: String) = LaunchSlotExtractor.slotOf(s)

    @Test fun `strips leading launch verb`() = assertEquals("bank", slot("open bank"))
    @Test fun `strips other launch verb`() = assertEquals("bank", slot("launch bank"))
    @Test fun `strips possessive and trailing app`() = assertEquals("bank", slot("open my bank app"))
    @Test fun `strips go to`() = assertEquals("bank", slot("go to bank"))
    @Test fun `keeps a bare slot`() = assertEquals("bank", slot("bank"))
    @Test fun `preserves a multi-word slot`() = assertEquals("bank of scotland", slot("open bank of scotland"))
    @Test fun `falls back when stripping empties it`() = assertEquals("open", slot("open"))
}
