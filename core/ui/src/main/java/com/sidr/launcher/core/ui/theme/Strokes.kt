package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Stroke widths (DS-2). Elevation is expressed as 1px lines, not shadow (grey spec §4); 2px is reserved
 * for focus and risk emphasis only.
 */
object Strokes {
    /** 1dp — dividers and surface borders. */
    val hairline: Dp = 1.dp

    /** 2dp — focus ring / risk emphasis only. */
    val focus: Dp = 2.dp
}
