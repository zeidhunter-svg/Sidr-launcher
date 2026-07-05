package com.sidr.launcher.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Sidr corner-shape scale — **brutalist / sharp** (AIL-0). The terminal identity reads as crisp
 * rectangular blocks divided by thin grid borders, not soft cards, so corners are near-square
 * (0–8dp) rather than the Phase-UX 4–28dp rounded scale. Elevation is carried by 1px `outline`
 * borders (see `Color.kt`); animated glow is deferred to the screen blocks (AIL-3/5/6).
 */
val SidrShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)
