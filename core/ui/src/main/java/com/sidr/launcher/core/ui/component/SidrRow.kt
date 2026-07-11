package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrStatusMarker
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * DS-3 row family (spec §5.4). Full-width rows with 48dp minimum touch target; section separation is
 * spacing and optional [SidrDivider], not a card around every row (spec §5.4).
 *
 * Rules (spec §5.4):
 * - [SidrToggleRow] uses one `toggleable` owner; the visual switch has `onCheckedChange = null`;
 * - value text wraps under the title at large font scale rather than clipping;
 * - TalkBack reads title, value/status, description, and role in a sensible order.
 *
 * Presentation-only: no domain/data/feature imports.
 */

/**
 * Navigation row — title + optional description/value, taps navigate. No card per row (spec §5.4).
 */
@Composable
fun SidrNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    enabled: Boolean = true,
) {
    val colors = SidrTheme.colors
    val rowMod = modifier
        .fillMaxWidth()
        .heightIn(min = Sizes.minTouchTarget)
        .clickable(
            enabled = enabled,
            onClickLabel = title,
            role = Role.Button,
            onClick = if (enabled) onClick else ({ }),
        )
        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SidrText(
                text = title,
                role = SidrTextRole.HUMAN_TITLE,
                color = if (enabled) colors.text else colors.faint,
            )
            description?.let {
                SidrText(text = it, role = SidrTextRole.PROVENANCE)
            }
        }
        value?.let {
            SidrText(
                text = it,
                role = SidrTextRole.SYSTEM,
                modifier = Modifier.padding(start = Spacing.md),
                color = if (enabled) colors.dim else colors.faint,
            )
        }
    }
}

/**
 * Toggle row — one `toggleable` owner; the visual switch has `onCheckedChange = null` so the row
 * owns the interaction and the switch reflects state (spec §4.6, §5.4). Cannot double-toggle.
 */
@Composable
fun SidrToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val colors = SidrTheme.colors
    val rowMod = modifier
        .fillMaxWidth()
        .heightIn(min = Sizes.minTouchTarget)
        .toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = if (enabled) onCheckedChange else ({ }),
        )
        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SidrText(
                text = title,
                role = SidrTextRole.HUMAN_TITLE,
                color = if (enabled) colors.text else colors.faint,
            )
            description?.let {
                SidrText(text = it, role = SidrTextRole.PROVENANCE)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
        )
    }
}

/**
 * Choice row — radio-style single selection. Uses `selectable` with `Role.RadioButton`; the visual
 * RadioButton has `onClick = null` so the row owns the interaction (spec §5.4).
 */
@Composable
fun SidrChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val colors = SidrTheme.colors
    val rowMod = modifier
        .fillMaxWidth()
        .heightIn(min = Sizes.minTouchTarget)
        .selectable(
            selected = selected,
            enabled = enabled,
            role = Role.RadioButton,
            onClick = if (enabled) onClick else ({ }),
        )
        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(
            modifier = Modifier.padding(start = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SidrText(
                text = title,
                role = SidrTextRole.HUMAN_TITLE,
                color = if (enabled) colors.text else colors.faint,
            )
            description?.let {
                SidrText(text = it, role = SidrTextRole.PROVENANCE)
            }
        }
    }
}

/**
 * Status row — title + status marker + optional value. Status is label + marker, never colour alone.
 */
@Composable
fun SidrStatusRow(
    title: String,
    status: SidrStatus,
    statusLabel: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    description: String? = null,
) {
    val colors = SidrTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.minTouchTarget)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SidrText(text = title, role = SidrTextRole.HUMAN_TITLE)
            description?.let { SidrText(text = it, role = SidrTextRole.PROVENANCE) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            value?.let {
                SidrText(text = it, role = SidrTextRole.SYSTEM, color = colors.dim)
            }
            SidrStatusMarker(status = status, label = statusLabel)
        }
    }
}

/**
 * Destructive row — navigation row with explicit danger label (spec §5.4: destructive row has explicit label).
 * Muted border/text treatment, not a loud red fill.
 */
@Composable
fun SidrDestructiveRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val colors = SidrTheme.colors
    val rowMod = modifier
        .fillMaxWidth()
        .heightIn(min = Sizes.minTouchTarget)
        .clickable(
            enabled = enabled,
            onClickLabel = title,
            role = Role.Button,
            onClick = if (enabled) onClick else ({ }),
        )
        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SidrText(
                text = title,
                role = SidrTextRole.HUMAN_TITLE,
                color = if (enabled) colors.danger else colors.faint,
            )
            description?.let {
                SidrText(text = it, role = SidrTextRole.PROVENANCE)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrRowPreview() {
    SidrTheme(darkTheme = true) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SidrNavigationRow(title = "AI provider", onClick = {}, value = "OpenRouter")
            SidrToggleRow(title = "AI suggestions", checked = true, onCheckedChange = {})
            SidrChoiceRow(title = "Dark", selected = true, onClick = {})
            SidrStatusRow(title = "Sync", status = SidrStatus.SUCCESS, statusLabel = "LOCAL")
            SidrDestructiveRow(title = "Forget", onClick = {})
        }
    }
}
