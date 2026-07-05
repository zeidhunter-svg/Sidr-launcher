package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/** One bracketed terminal route chip (AIL-3 / DF-3): a tappable `[ label ]` accelerator. */
data class RouteChip(val label: String, val onClick: () -> Unit)

/**
 * The route-chip row shown under the universal-input field (DF-3): bracketed `[ ⌕ web ]` terminal chips
 * for the explicit web / assistant / open-site lanes. Horizontally scrollable so it never wraps the home
 * layout. Pure presentation; the caller supplies each chip's label + tap action.
 */
@Composable
fun RouteChipRow(
    chips: List<RouteChip>,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(chips) { chip ->
            Text(
                text = "[ ${chip.label} ]",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = chip.onClick)
                    .padding(vertical = Spacing.xs, horizontal = Spacing.xs),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun RouteChipRowPreview() {
    SidrTheme(darkTheme = true) {
        RouteChipRow(
            chips = listOf(
                RouteChip("⌕ web") {},
                RouteChip("✦ ask") {},
                RouteChip("⌂ site") {},
            ),
        )
    }
}
