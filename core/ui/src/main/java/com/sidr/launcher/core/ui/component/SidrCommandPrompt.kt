package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * The terminal `>`-prompt universal-input field (AIL-3 / DF-2). A leading `>` glyph replaces the search
 * magnifier, JetBrains Mono renders the text, an accent caret marks the cursor (a full block caret and
 * blink are DF-5/AIL-6 motion polish, deferred), and thin grid borders carry glowing accent corner ticks.
 * Same callback contract as [SidrSearchField] so it is a drop-in for the home field; the App Drawer keeps
 * [SidrSearchField]. Pure presentation — routing is the caller's decision in [onSubmit] / [onValueChange].
 *
 * @param listening true while a voice recognizer is capturing — the mic affordance switches to the
 *   active accent state (a static fill; pulse/flicker is DF-5 motion, deferred).
 */
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
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurface.copy(alpha = 0.4f),
                )
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
                modifier = Modifier.fillMaxWidth(),
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

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun SidrCommandPromptEmptyPreview() {
    SidrTheme(darkTheme = true) {
        SidrCommandPrompt(value = "", onValueChange = {}, onSubmit = {}, showMic = true)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun SidrCommandPromptTypedPreview() {
    SidrTheme(darkTheme = true) {
        SidrCommandPrompt(value = "open telegram", onValueChange = {}, onSubmit = {}, showMic = true, listening = true)
    }
}
