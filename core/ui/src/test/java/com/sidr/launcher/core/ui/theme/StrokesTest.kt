package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class StrokesTest {
    @Test fun stroke_tokens_match_spec() {
        assertEquals(1.dp, Strokes.hairline)
        assertEquals(2.dp, Strokes.focus)
    }
}
