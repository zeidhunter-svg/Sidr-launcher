package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes

/**
 * DS-3 icon button (spec §5.2). [TopBarIcon] evolves into [SidrIconButton], preserving its 48dp target
 * and required content description. [TopBarIcon] remains as a compatibility wrapper until usages are
 * migrated (spec §5.2).
 *
 * Presentation-only: no domain/data/feature imports.
 */

/**
 * SIDR icon button — 48dp touch target, 24dp glyph, required content description.
 */
@Composable
fun SidrIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Sizes.minTouchTarget),
        enabled = enabled,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

/**
 * [Painter] overload for glyphs that are bundled vector drawables rather than core [ImageVector]s.
 */
@Composable
fun SidrIconButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Sizes.minTouchTarget),
        enabled = enabled,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrIconButtonPreview() {
    SidrTheme(darkTheme = true) {
        androidx.compose.foundation.layout.Column {
            SidrIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = {},
            )
        }
    }
}
