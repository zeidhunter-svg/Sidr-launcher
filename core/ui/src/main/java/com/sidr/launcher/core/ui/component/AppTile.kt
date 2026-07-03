package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * A single launchable app cell: an icon over a one-line label. Used on the home Favorites row and
 * in the App Drawer grid.
 *
 * The [icon] is a composable slot so that icon loading (a `PackageManager`/`Drawable` concern that
 * belongs to a feature module) stays out of `core/ui` — this keeps the design system free of any
 * `domain`/`data` dependency. The whole tile is one clickable target labelled with [label]; the
 * icon slot is decorative.
 */
@Composable
fun AppTile(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .width(Sizes.appTile)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = label }
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier.size(Sizes.appIcon),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview
@Composable
private fun AppTilePreview() {
    SidrTheme {
        AppTile(
            label = "Telegram",
            onClick = {},
            icon = {
                Box(
                    modifier = Modifier
                        .size(Sizes.appIcon)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                )
            },
        )
    }
}
