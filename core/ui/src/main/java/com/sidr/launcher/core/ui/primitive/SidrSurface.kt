package com.sidr.launcher.core.ui.primitive

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.sidr.launcher.core.ui.theme.SidrColors
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Strokes

/** Tonal surface (DS-2). Elevation is a 1px line, never a shadow (grey spec §4). Press-invert = DS-3. */
enum class SidrSurfaceTone { GROUND, SURFACE, RAISED, SACRED, RISK }

internal fun SidrSurfaceTone.background(colors: SidrColors): Color = when (this) {
    SidrSurfaceTone.GROUND -> colors.ground
    SidrSurfaceTone.SURFACE -> colors.surface
    SidrSurfaceTone.RAISED -> colors.raised
    SidrSurfaceTone.SACRED -> colors.surface
    SidrSurfaceTone.RISK -> colors.surface
}

internal fun SidrSurfaceTone.borderColor(colors: SidrColors): Color? = when (this) {
    SidrSurfaceTone.RISK -> colors.caution
    else -> null
}

@Composable
fun SidrSurface(
    tone: SidrSurfaceTone,
    modifier: Modifier = Modifier,
    shape: Shape = SidrShapes.medium,
    content: @Composable () -> Unit,
) {
    val colors = SidrTheme.colors
    val border = tone.borderColor(colors)
    Surface(
        modifier = modifier,
        shape = shape,
        color = tone.background(colors),
        border = border?.let { BorderStroke(Strokes.hairline, it) },
        content = content,
    )
}
