package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * DF-4 confirmation card — the "terminal confirm block" for a risk-gated router proposal (AIL-5).
 *
 * Reads as an early-computer terminal prompt on the AIL-0 tokens: a 1px accent-bordered block with
 * near-square corners and monospace text. The header pairs the `EXECUTE?` prompt with a bracketed
 * `[riskLabel]` accent chip (the risk tag); the body echoes the pending command as a `>`-prefixed
 * line; two bracketed `[ CANCEL ]` / `[ CONFIRM ]` accelerators close it. Nothing runs until CONFIRM.
 *
 * Pure presentation: the caller supplies display-safe strings + the two callbacks. No domain types,
 * so no `domain → ui` edge (the feature maps `LauncherAction`/risk → these strings).
 */
@Composable
fun ConfirmActionCard(
    commandLine: String,
    riskLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.small,
            )
            .padding(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "EXECUTE?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "[$riskLabel]",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = "> $commandLine",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.md),
            horizontalArrangement = Arrangement.End,
        ) {
            BracketButton(label = "CANCEL", onClick = onCancel)
            BracketButton(
                label = "CONFIRM",
                onClick = onConfirm,
                modifier = Modifier.padding(start = Spacing.lg),
            )
        }
    }
}

/** A bracketed terminal button `[ LABEL ]` with a 48dp touch target (matches [RouteChip] styling). */
@Composable
private fun BracketButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "[ $label ]",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .heightIn(min = Sizes.minTouchTarget)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = Spacing.sm),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun ConfirmActionCardPreview() {
    SidrTheme(darkTheme = true) {
        ConfirmActionCard(
            commandLine = "open https://example.com",
            riskLabel = "CONFIRM",
            onConfirm = {},
            onCancel = {},
        )
    }
}
