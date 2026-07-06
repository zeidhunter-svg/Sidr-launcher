package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing

/** One action in the [CommandBar] (AIL-6): a bracketed terminal `[ label ]` shell command. */
data class CommandBarItem(
    val label: String,
    /** Spoken/verbose name for TalkBack (the visible label may be terse, e.g. `cfg`). */
    val contentDescription: String,
    val onClick: () -> Unit,
)

/**
 * The home command bar (AIL-6) — a thin bordered footer of bracketed terminal tokens carrying the
 * launcher's persistent shell actions (ask / all apps / settings). It replaces the off-theme Material
 * top-bar icons with monospace `[ label ]` accelerators that speak the same visual language as the
 * input route chips ([RouteChipRow]) — same press-invert, same brackets. A 1px top rule with accent
 * corner ticks echoes the [SidrCommandPrompt] frame so the footer reads as the bottom edge of the
 * terminal window. Pure presentation; the caller supplies each token's action.
 */
@Composable
fun CommandBar(
    items: List<CommandBarItem>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val hairline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = 1.dp.toPx()
                val tick = 10.dp.toPx()
                drawLine(hairline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = stroke)
                drawLine(accent, Offset(0f, 0f), Offset(tick, 0f), strokeWidth = stroke)
                drawLine(accent, Offset(size.width - tick, 0f), Offset(size.width, 0f), strokeWidth = stroke)
            }
            .padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            // DF-5 press-invert, identical to the input route chips: held → solid accent + ground text.
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            Text(
                text = "[ ${item.label} ]",
                style = MaterialTheme.typography.labelLarge,
                color = if (pressed) MaterialTheme.colorScheme.onPrimary else accent,
                modifier = Modifier
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClickLabel = item.contentDescription,
                        role = Role.Button,
                        onClick = item.onClick,
                    )
                    .heightIn(min = Sizes.minTouchTarget)
                    .background(if (pressed) accent else Color.Transparent)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = Spacing.sm),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun CommandBarPreview() {
    SidrTheme(darkTheme = true) {
        CommandBar(
            items = listOf(
                CommandBarItem("ask", "Assistant") {},
                CommandBarItem("all apps", "All apps") {},
                CommandBarItem("cfg", "Settings") {},
            ),
        )
    }
}
