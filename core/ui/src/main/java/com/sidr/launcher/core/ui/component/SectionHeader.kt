package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * A small, understated section label ("Suggestions", "Favorites") used to group home rows.
 * Marked as an accessibility heading so screen-reader users can navigate section-by-section.
 *
 * **Deprecated (DS-3):** semantic section usage migrated to [SidrSectionHeader]. Kept as a
 * compatibility wrapper until all references are removed; no new call sites should use this.
 */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .semantics { heading() },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun SectionHeaderPreview() {
    SidrTheme(darkTheme = true) {
        SectionHeader(text = "FAVORITES")
    }
}
