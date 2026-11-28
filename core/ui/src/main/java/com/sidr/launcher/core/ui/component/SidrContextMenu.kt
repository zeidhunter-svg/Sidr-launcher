package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One entry of a [SidrContextMenu]: its [label] and what it does. The menu dismisses itself after [onClick]. */
data class SidrMenuItem(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * A small context menu — a thin primitive over Material3's `DropdownMenu`, drawn in the theme's colours and
 * typography. Anchor it by placing it inside the same `Box` as the element that opens it. [onDismiss] fires on an
 * outside touch, on back, and after an item has run.
 */
@Composable
fun SidrContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<SidrMenuItem>,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        SidrContextMenuItems(items = items, onDismiss = onDismiss)
    }
}

/**
 * The menu's rows, apart from the popup window: Roborazzi cannot capture a `Popup`, so the golden draws these inside a
 * plain surface instead (`ControlsScreenshotTest.context_menu`).
 */
@Composable
internal fun ColumnScope.SidrContextMenuItems(items: List<SidrMenuItem>, onDismiss: () -> Unit) {
    items.forEach { item ->
        DropdownMenuItem(
            text = {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            onClick = {
                item.onClick()
                onDismiss()
            },
        )
    }
}
