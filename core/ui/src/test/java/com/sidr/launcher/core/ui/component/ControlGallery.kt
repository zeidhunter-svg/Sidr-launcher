package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrSystemLabel
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.SidrThemePreviews
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Preview-only DS-3 control gallery (proof surface). Not a production screen; no feature dependency.
 * Starts smoke-only; each task adds its control states (buttons → chips → rows → top bar → action gate).
 */
@Composable
fun ControlGallery() {
    Surface(color = SidrTheme.colors.ground) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Buttons ───────────────────────────────────────────────────────
            SidrSystemLabel("BUTTONS")
            SidrPrimaryButton("Continue", onClick = {})
            SidrSecondaryButton("Not now", onClick = {})
            SidrTertiaryButton("Dismiss", onClick = {})
            SidrDestructiveButton("Forget", onClick = {})
            SidrTerminalAction("CONFIRM", onClick = {})
            SidrTerminalAction("SELECTED", onClick = {}, selected = true)
            SidrPrimaryButton("Loading…", onClick = {}, loading = true)
            SidrPrimaryButton("Disabled", onClick = {}, enabled = false)
            SidrPrimaryButton(
                "A very long label that should wrap gracefully at large font scale",
                onClick = {},
            )

            // ── Chips ──────────────────────────────────────────────────────────
            SidrSystemLabel("CHIPS")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SidrRouteChip("APP", selected = true, onClick = {})
                SidrRouteChip("WEB", selected = false, onClick = {})
                SidrRouteChip("SITE", selected = false, onClick = {})
                SidrRouteChip("ASK", selected = false, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SidrFilterChip("4", selected = false, onClick = {})
                SidrFilterChip("6", selected = false, onClick = {})
                SidrFilterChip("8", selected = true, onClick = {})
                SidrFilterChip("10", selected = false, onClick = {})
            }
            SidrSuggestionChip("Open Clock", onClick = {})
            SidrActionChip("Clear", onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SidrStatusChip("LOCAL", SidrStatus.SUCCESS)
                SidrStatusChip("CLOUD", SidrStatus.INFO)
                SidrStatusChip("CONFIRM", SidrStatus.ATTENTION)
                SidrStatusChip("FAILED", SidrStatus.DANGER)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SidrRiskChip("SAFE", SidrRiskTone.Safe)
                SidrRiskChip("CONFIRM", SidrRiskTone.Confirm)
                SidrRiskChip("EXTERNAL", SidrRiskTone.External)
                SidrRiskChip("DESTRUCTIVE", SidrRiskTone.Destructive)
            }
            SidrRouteChip(
                "A very long route label that should not break row height",
                selected = false,
                onClick = {},
            )

            // ── Rows ───────────────────────────────────────────────────────────
            SidrSystemLabel("ROWS")
            SidrNavigationRow(title = "AI provider", onClick = {}, value = "OpenRouter")
            SidrToggleRow(title = "AI suggestions", checked = true, onCheckedChange = {})
            SidrToggleRow(title = "Voice input", checked = false, onCheckedChange = {})
            SidrChoiceRow(title = "Dark", selected = true, onClick = {})
            SidrChoiceRow(title = "Light", selected = false, onClick = {})
            SidrStatusRow(title = "Sync", status = SidrStatus.SUCCESS, statusLabel = "LOCAL", value = "2m ago")
            SidrDestructiveRow(title = "Forget", onClick = {})
            SidrNavigationRow(
                title = "A very long navigation row title that should wrap under at large font scale",
                onClick = {},
                value = "A long value too",
            )

            // ── Top Bar + Sections ─────────────────────────────────────────────
            SidrSystemLabel("TOP BAR")
            SidrTopBar(title = "Settings")
            SidrSectionHeader("APPEARANCE")
            SidrAlphabetHeader("A")

            // ── Universal Input ────────────────────────────────────────────────
            SidrSystemLabel("UNIVERSAL INPUT")
            SidrUniversalInput(
                value = "",
                onValueChange = {},
                onSubmit = {},
                voiceAvailable = true,
                onVoiceClick = {},
            )
            SidrUniversalInput(
                value = "open telegram",
                onValueChange = {},
                onSubmit = {},
                state = SidrUniversalInputState.Typing,
                voiceAvailable = true,
                onVoiceClick = {},
                onClearClick = {},
                routeContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SidrRouteChip("WEB", selected = false, onClick = {})
                        SidrRouteChip("ASK", selected = false, onClick = {})
                        SidrRouteChip("SITE", selected = false, onClick = {})
                    }
                },
                supportingText = "Local · offline",
            )
            SidrUniversalInput(
                value = "",
                onValueChange = {},
                onSubmit = {},
                state = SidrUniversalInputState.Listening,
                voiceAvailable = true,
                onVoiceClick = {},
            )
            SidrUniversalInput(
                value = "",
                onValueChange = {},
                onSubmit = {},
                state = SidrUniversalInputState.Disabled,
                enabled = false,
            )

            // ── Action Gate ─────────────────────────────────────────────────────
            SidrSystemLabel("ACTION GATE")
            SidrActionGate(
                type = SidrActionGateType.ExternalHandoff,
                title = "Open website",
                consequence = "This will open the URL in your browser.",
                target = "https://github.com",
                confirmLabel = "Continue",
                onConfirm = {},
                onCancel = {},
            )
            SidrActionGate(
                type = SidrActionGateType.Destructive,
                title = "Forget memory",
                consequence = "All learned choices will be permanently removed.",
                confirmLabel = "Forget",
                onConfirm = {},
                onCancel = {},
            )
            SidrActionGate(
                type = SidrActionGateType.Confirmation,
                title = "Run action",
                consequence = "This will execute the proposed action.",
                confirmLabel = "Confirm",
                onConfirm = {},
                onCancel = {},
                confirming = true,
            )
        }
    }
}

@SidrThemePreviews
@Composable
private fun ControlGalleryPreview() {
    SidrTheme { ControlGallery() }
}
