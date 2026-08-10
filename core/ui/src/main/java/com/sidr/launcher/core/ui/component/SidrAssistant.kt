package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.primitive.SidrProgress
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * DS-10 assistant controls (spec §4, §8). Two generic, presentation-only pieces the Assistant surface
 * is composed from — deliberately state-free so the feature keeps owning the prompt text (no
 * SavedStateHandle, no history) and every dispatch decision.
 *
 * Presentation-only: no domain/data/feature imports (enforced by `ControlsDependencyGuardTest`).
 */

/** Content descriptions for the composer's send affordance, one per reason it can be unavailable. */
internal const val SEND_READY_DESCRIPTION = "Send message"
internal const val SEND_EMPTY_DESCRIPTION = "Send message, unavailable until you type a message"
internal const val SEND_BUSY_DESCRIPTION = "Send message, unavailable while the assistant is replying"

internal fun sendDescription(hasText: Boolean, sending: Boolean): String = when {
    sending -> SEND_BUSY_DESCRIPTION
    !hasText -> SEND_EMPTY_DESCRIPTION
    else -> SEND_READY_DESCRIPTION
}

/**
 * The assistant composer (DS-10 Task 4): one mono field plus a send affordance inside a single
 * [SidrSurface], matching the App Drawer's search-field idiom (no card-inside-card).
 *
 * Send is dispatched from **both** the IME `Send` action and the button, and only when
 * [value] is non-blank and [sending] is false — the two paths share one guard so they can never
 * disagree. The composable never clears [value]; that stays the caller's decision.
 *
 * The send button's content description names *why* it is unavailable, so TalkBack announces the
 * disabled reason instead of a bare "disabled" (spec §8).
 *
 * @param sending true while a reply is streaming — blocks send without hiding the field.
 */
@Composable
fun SidrAssistantComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Message",
    sending: Boolean = false,
) {
    val colors = SidrTheme.colors
    val canSend = value.isNotBlank() && !sending

    SidrSurface(
        tone = SidrSurfaceTone.SURFACE,
        shape = SidrShapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    SidrText(text = placeholder, role = SidrTextRole.HUMAN_BODY, color = colors.faint)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        SidrTheme.textStyles.command.copy(color = colors.text),
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = placeholder },
                )
            }

            SidrIconButton(
                icon = Icons.AutoMirrored.Filled.Send,
                contentDescription = sendDescription(hasText = value.isNotBlank(), sending = sending),
                onClick = { if (canSend) onSend() },
                enabled = canSend,
                tint = if (canSend) colors.accent else colors.faint,
            )
        }
    }
}

/**
 * Quiet streaming indicator (DS-10 Task 5, spec §5 "streaming state is quiet"): one thin progress
 * line plus a short mono label.
 *
 * The label is the **only** live region on the surface and it carries a fixed string, so TalkBack
 * announces "replying" once per state change rather than re-reading on every streamed token
 * (spec §8: "streaming state announced without noisy repetition").
 */
@Composable
fun SidrStreamingIndicator(
    modifier: Modifier = Modifier,
    label: String = "Replying…",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SidrProgress(modifier = Modifier.fillMaxWidth())
        SidrText(text = label, role = SidrTextRole.SYSTEM)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrAssistantPreview() {
    SidrTheme(darkTheme = true) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SidrAssistantComposer(value = "", onValueChange = {}, onSend = {})
            SidrAssistantComposer(value = "what is the weather", onValueChange = {}, onSend = {})
            SidrAssistantComposer(value = "streaming now", onValueChange = {}, onSend = {}, sending = true)
            SidrStreamingIndicator()
        }
    }
}
