package com.sidr.launcher.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Sidr corner-shape scale — **softened classic** (2026-07-10 visual-identity spec §4). Not brutalist-sharp
 * and not pill: 7dp chips, 10dp default (input, tiles, moments), 12dp modals. Replaces the AIL-0 brutalist
 * 0–8dp scale. Elevation is still carried by 1px `line`/`border` hairlines (see `SidrColors`), not tonal
 * shadow; `2px` is reserved for focus/risk.
 */
val SidrShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(7.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp),
)
