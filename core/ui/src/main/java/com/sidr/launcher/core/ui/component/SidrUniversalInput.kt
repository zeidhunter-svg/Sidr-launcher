package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.core.ui.theme.Strokes
import kotlinx.coroutines.delay

/**
 * DS-4 Universal Input state (spec §7). Maps to observable launcher states; preview-only states
 * (Listening, Interpreting, Executing) are available for screenshot coverage but not wired to
 * production until real backing exists.
 */
enum class SidrUniversalInputState {
    Idle,
    Focused,
    Typing,
    Listening,
    Interpreting,
    Ambiguous,
    Proposed,
    Executing,
    Error,
    Disabled,
}

/**
 * DS-4 Universal Input — the primary Home control (spec §7). Replaces [SidrCommandPrompt] at Home
 * while preserving the existing command pipeline byte-for-byte.
 *
 * Rules (spec §7):
 * - `>` prompt marker stays, but TalkBack must not read it as "greater than";
 * - block caret appears only when appropriate for real focus/idle state;
 * - no fake spinner/waveform;
 * - mic state is explicit and accessible;
 * - clear action has a 48dp target;
 * - route content is a slot so route mapping remains feature-owned;
 * - `onSubmit` is parameterless; the caller already owns `value`;
 * - IME search/submit path must call the same callback as before.
 *
 * Presentation-only: no domain/data/feature imports.
 */
@Composable
fun SidrUniversalInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    state: SidrUniversalInputState = SidrUniversalInputState.Idle,
    placeholder: String = "Ask, search, open or automate",
    enabled: Boolean = true,
    voiceAvailable: Boolean = false,
    onVoiceClick: (() -> Unit)? = null,
    onClearClick: (() -> Unit)? = null,
    routeContent: (@Composable (() -> Unit))? = null,
    supportingText: String? = null,
) {
    val colors = SidrTheme.colors
    val accent = colors.accent
    val onSurface = colors.text

    // Block caret: visible only when idle/unfocused and empty (spec §7).
    // Blinks at terminal cadence; motion-gated via a simple LaunchedEffect.
    var focused by remember { mutableStateOf(false) }
    var caretVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CARET_BLINK_MS)
            caretVisible = !caretVisible
        }
    }

    val showBlockCaret = !focused && value.isEmpty() && enabled
    val isTyping = value.isNotEmpty()

    Column(modifier = modifier.fillMaxWidth()) {
        // Input surface
        SidrSurface(
            tone = if (state == SidrUniversalInputState.Error) SidrSurfaceTone.RISK else SidrSurfaceTone.SURFACE,
            shape = SidrShapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .then(
                    // Signal-toned hairline around the input (artifact `--sigbd`). The RISK tone already
                    // carries its own border, so only add this in the normal state.
                    if (state == SidrUniversalInputState.Error) {
                        Modifier
                    } else {
                        Modifier.border(Strokes.hairline, colors.accentBorder, SidrShapes.medium)
                    },
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // `>` prompt marker — decorative, not read as "greater than" (spec §7).
                SidrText(
                    text = ">",
                    role = SidrTextRole.COMMAND,
                    color = accent,
                    modifier = Modifier.semantics {
                        // Prevent TalkBack from reading ">" as "greater than".
                        contentDescription = "Prompt"
                    },
                )

                // Text field + block caret
                Box(modifier = Modifier.weight(1f)) {
                    if (!isTyping) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Caret slot is always reserved while empty; only its colour blinks, so the
                            // placeholder never shifts with the blink (it's transparent on the off phase).
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(width = 9.dp, height = 18.dp)
                                    .background(if (showBlockCaret && caretVisible) accent else Color.Transparent),
                            )
                            SidrText(
                                text = placeholder,
                                role = SidrTextRole.HUMAN_BODY,
                                color = colors.faint,
                            )
                        }
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        enabled = enabled,
                        singleLine = true,
                        textStyle = LocalTextStyle.current.merge(
                            SidrTheme.textStyles.command.copy(color = onSurface),
                        ),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused },
                    )
                }

                // Clear affordance — 48dp target (spec §7).
                if (isTyping && onClearClick != null) {
                    SidrIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Clear input",
                        onClick = onClearClick,
                        tint = colors.dim,
                    )
                }

                // Mic affordance — explicit and accessible (spec §7).
                if (voiceAvailable && onVoiceClick != null) {
                    val listening = state == SidrUniversalInputState.Listening
                    val micMod = if (listening) {
                        Modifier
                            .size(Sizes.minTouchTarget)
                            .background(accent.copy(alpha = 0.15f), SidrShapes.small)
                    } else {
                        Modifier.size(Sizes.minTouchTarget)
                    }
                    androidx.compose.material3.IconButton(
                        onClick = onVoiceClick,
                        modifier = micMod,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_mic_24),
                            contentDescription = if (listening) "Listening — voice input active" else "Voice input",
                            tint = if (listening) accent else colors.dim,
                        )
                    }
                }
            }
        }

        // Route content slot — feature-owned (spec §7).
        routeContent?.invoke()

        // Supporting text — provenance/status line (spec §7).
        supportingText?.let { text ->
            SidrText(
                text = text,
                role = SidrTextRole.PROVENANCE,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
            )
        }
    }
}

/** Terminal caret cadence — ~530ms on/off, a familiar CRT blink rate. */
private const val CARET_BLINK_MS = 530L

// ── Previews ────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrUniversalInputIdlePreview() {
    SidrTheme(darkTheme = true) {
        SidrUniversalInput(
            value = "",
            onValueChange = {},
            onSubmit = {},
            voiceAvailable = true,
            onVoiceClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrUniversalInputTypingPreview() {
    SidrTheme(darkTheme = true) {
        SidrUniversalInput(
            value = "open telegram",
            onValueChange = {},
            onSubmit = {},
            state = SidrUniversalInputState.Typing,
            voiceAvailable = true,
            onVoiceClick = {},
            onClearClick = {},
            routeContent = {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    SidrRouteChip("WEB", selected = false, onClick = {})
                    SidrRouteChip("ASK", selected = false, onClick = {})
                    SidrRouteChip("SITE", selected = false, onClick = {})
                }
            },
            supportingText = "Local · offline",
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrUniversalInputListeningPreview() {
    SidrTheme(darkTheme = true) {
        SidrUniversalInput(
            value = "",
            onValueChange = {},
            onSubmit = {},
            state = SidrUniversalInputState.Listening,
            voiceAvailable = true,
            onVoiceClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrUniversalInputDisabledPreview() {
    SidrTheme(darkTheme = true) {
        SidrUniversalInput(
            value = "",
            onValueChange = {},
            onSubmit = {},
            state = SidrUniversalInputState.Disabled,
            enabled = false,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F3F0)
@Composable
private fun SidrUniversalInputLightPreview() {
    SidrTheme(darkTheme = false) {
        SidrUniversalInput(
            value = "search something",
            onValueChange = {},
            onSubmit = {},
            state = SidrUniversalInputState.Typing,
            voiceAvailable = true,
            onVoiceClick = {},
            onClearClick = {},
        )
    }
}
