package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.theme.LocalSidrMotionEnabled
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * The terminal `>`-prompt universal-input field (AIL-3 / DF-2). A leading `>` glyph replaces the search
 * magnifier, JetBrains Mono renders the text, an idle phosphor **block caret** blinks after the prompt
 * while empty+unfocused (DF-5; LOW_END-gated), the native accent cursor marks the insertion point while
 * typing, and thin grid borders carry glowing accent corner ticks.
 * Same callback contract as [SidrSearchField] so it is a drop-in for the home field; the App Drawer keeps
 * [SidrSearchField]. Pure presentation — routing is the caller's decision in [onSubmit] / [onValueChange].
 *
 * @param listening true while a voice recognizer is capturing — the mic affordance switches to the
 *   active accent state (a static fill; pulse/flicker is DF-5 motion, deferred).
 *
 * @deprecated DS-4 replaced this at Home with [SidrUniversalInput] (soft-classic-grey identity, DS-2/DS-3
 *   primitives, parameterless `onSubmit`). Production usage is now zero; kept only until any remaining
 *   references are migrated, then removed.
 */
@Deprecated(
    message = "Replaced by SidrUniversalInput (DS-4). Migrate remaining call sites, then remove.",
    replaceWith = ReplaceWith("SidrUniversalInput"),
)
@Composable
fun SidrCommandPrompt(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "type a command",
    showMic: Boolean = false,
    listening: Boolean = false,
    onMic: () -> Unit = {},
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    // DF-5 idle block caret: while the field is empty and unfocused, a phosphor block blinks after the
    // prompt (classic terminal idle state). Once focused, the native accent cursor takes over, so the
    // block hides to avoid a double cursor. LOW_END-gated via LocalSidrMotionEnabled — motion off holds
    // the block solid (no per-frame work). While typing, only the native cursor shows.
    val motionEnabled = LocalSidrMotionEnabled.current
    var focused by remember { mutableStateOf(false) }
    var caretVisible by remember { mutableStateOf(true) }
    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) {
            caretVisible = true
            return@LaunchedEffect
        }
        while (true) {
            delay(CARET_BLINK_MS)
            caretVisible = !caretVisible
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .drawBehind {
                // Thin grid border + glowing accent corner ticks (static; brutalist AIL-0 identity).
                val tick = 10.dp.toPx()
                val w = size.width
                val h = size.height
                val stroke = 1.dp.toPx()
                // full thin border
                drawRect(color = onSurface.copy(alpha = 0.12f), size = size, style = Stroke(width = stroke))
                // four corner ticks in accent
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(accent, Offset(x, y), Offset(x + dx, y), strokeWidth = stroke)
                    drawLine(accent, Offset(x, y), Offset(x, y + dy), strokeWidth = stroke)
                }
                corner(0f, 0f, tick, tick)
                corner(w, 0f, -tick, tick)
                corner(0f, h, tick, -tick)
                corner(w, h, -tick, -tick)
            }
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = ">",
            style = MaterialTheme.typography.bodyLarge,
            color = accent,
        )
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!focused) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(width = 9.dp, height = 18.dp)
                                .background(accent.copy(alpha = if (caretVisible) 1f else 0f)),
                        )
                    }
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = onSurface.copy(alpha = 0.4f),
                    )
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = onSurface),
                ),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(value) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (showMic) {
            IconButton(
                onClick = onMic,
                modifier = if (listening) {
                    Modifier.background(accent.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                } else {
                    Modifier
                },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mic_24),
                    contentDescription = if (listening) "Listening" else "Voice input",
                    tint = if (listening) accent else onSurface,
                )
            }
        }
    }
}

/** Terminal caret cadence — ~530ms on/off, a familiar CRT blink rate. */
private const val CARET_BLINK_MS = 530L

@Suppress("DEPRECATION")
@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun SidrCommandPromptEmptyPreview() {
    SidrTheme(darkTheme = true) {
        SidrCommandPrompt(value = "", onValueChange = {}, onSubmit = {}, showMic = true)
    }
}

@Suppress("DEPRECATION")
@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun SidrCommandPromptTypedPreview() {
    SidrTheme(darkTheme = true) {
        SidrCommandPrompt(value = "open telegram", onValueChange = {}, onSubmit = {}, showMic = true, listening = true)
    }
}
