package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes

/**
 * A tappable top-bar icon affordance (e.g. Settings, Assistant). Renders inside a 48dp
 * [IconButton] so the touch target meets accessibility guidance even though the glyph is 24dp.
 *
 * [contentDescription] is required and non-null — these icons are the *only* way to discover
 * Settings / Assistant on the redesigned home, so they must be labelled for TalkBack.
 *
 * **Deprecated (DS-3):** migrated to [SidrIconButton]. Kept as a compatibility wrapper until all
 * references are removed; no new call sites should use this.
 */
@Composable
fun TopBarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Sizes.minTouchTarget),
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
 * [Painter] overload for glyphs that are bundled vector drawables rather than core Material icons
 * (e.g. the Assistant sparkle, which has no core [ImageVector]). Same 48dp target / 24dp glyph.
 */
@Composable
fun TopBarIcon(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(Sizes.minTouchTarget),
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(Sizes.icon),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun TopBarIconPreview() {
    SidrTheme(darkTheme = true) {
        TopBarIcon(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            onClick = {},
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun TopBarIconPainterPreview() {
    SidrTheme(darkTheme = true) {
        TopBarIcon(
            painter = painterResource(com.sidr.launcher.core.ui.R.drawable.ic_assistant_24),
            contentDescription = "Assistant",
            onClick = {},
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
