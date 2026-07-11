package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.primitive.SidrSystemLabel
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * DS-3 sections and top bars (spec §5.5).
 *
 * Rules (spec §5.5):
 * - [SidrSectionHeader] replaces semantic section usage of old [SectionHeader];
 * - [SidrAlphabetHeader] replaces App Drawer sticky alphabet/category headers;
 * - [SidrTopBar] is presentation-only and never owns navigation.
 *
 * Presentation-only: no domain/data/feature imports.
 */

/**
 * Semantic section header — mono, uppercase, wide-tracking label marked as an a11y heading.
 * Replaces the old [SectionHeader] for semantic section usage (spec §5.5).
 */
@Composable
fun SidrSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    SidrSystemLabel(
        text = text,
        modifier = modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .semantics { heading() },
    )
}

/**
 * Alphabet/category header for App Drawer sticky headers (spec §5.5). Same visual as [SidrSectionHeader]
 * but distinct semantic name so callers and future migrations can treat them separately.
 */
@Composable
fun SidrAlphabetHeader(
    text: String,
    modifier: Modifier = Modifier,
) {
    SidrSystemLabel(
        text = text,
        modifier = modifier
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .semantics { heading() },
    )
}

/**
 * Presentation-only top bar. Never owns navigation — callers supply navigation icon + actions slots.
 * Uses M3 [TopAppBar] with SIDR grey container colours (spec §5.5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SidrTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable (() -> Unit))? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = SidrTheme.colors
    TopAppBar(
        modifier = modifier,
        title = {
            SidrText(text = title, role = SidrTextRole.HUMAN_TITLE)
        },
        navigationIcon = { navigationIcon?.invoke() },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.ground,
            titleContentColor = colors.text,
            navigationIconContentColor = colors.text,
            actionIconContentColor = colors.text,
        ),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrTopBarPreview() {
    SidrTheme(darkTheme = true) {
        Column {
            SidrTopBar(title = "Settings")
            SidrSectionHeader("APPEARANCE")
            SidrAlphabetHeader("A")
        }
    }
}
