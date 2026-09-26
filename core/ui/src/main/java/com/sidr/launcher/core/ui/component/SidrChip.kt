package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrStatusMarker
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.core.ui.theme.Strokes

/**
 * DS-3 chip family (spec §5.3). Press-invert selection: selected/pressed state flips foreground/background
 * with no glow, no scale bounce, and no layout shift (spec §4.5).
 *
 * Rules (spec §5.3):
 * - selected route/filter chip uses press-invert (text fill + ground/surface text depending theme);
 * - status chips use fixed status tokens, never the pewter accent;
 * - risk chip always includes text and marker;
 * - long labels wrap or constrain without resizing the row height unpredictably;
 * - RouteChipRow remains a feature-level row composition, not a second chip implementation.
 *
 * Presentation-only: no domain/data/feature imports.
 */

/**
 * Risk tone for [SidrRiskChip] (spec §5.3). Stable across all surfaces: the same consequence gets the
 * same marker + title hierarchy regardless of source (spec §4.4).
 */
enum class SidrRiskTone { Safe, Confirm, External, Destructive }

internal fun SidrRiskTone.status(): SidrStatus = when (this) {
    SidrRiskTone.Safe -> SidrStatus.SUCCESS
    SidrRiskTone.Confirm -> SidrStatus.ATTENTION
    SidrRiskTone.External -> SidrStatus.INFO
    SidrRiskTone.Destructive -> SidrStatus.DANGER
}

/**
 * Route chip - colourless press-invert selection (APP / WEB / SITE / ASK). Selected flips to accent fill
 * with ground text; unselected is transparent with accent text. No glow, no scale, no layout shift.
 *
 * DS-11 A4 (2026-08-10): **borderless**. The route lane sits directly under the universal input, where
 * four hairline boxes read as heavy furniture rather than accelerators; press-invert alone carries the
 * affordance (Master Plan §6.3 keeps press-invert as part of the terminal signature). The bordered
 * frame is retained for [SidrFilterChip]/[SidrSuggestionChip]/[SidrActionChip], where a resting
 * boundary is what separates one option from the next.
 */
@Composable
fun SidrRouteChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SidrPressInvertChip(
    label = label,
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    enabled = true,
    bordered = false,
)

/**
 * Filter chip - same press-invert frame as route, for filtering selections (e.g. favorites count).
 */
@Composable
fun SidrFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SidrPressInvertChip(
    label = label,
    selected = selected,
    onClick = onClick,
    modifier = modifier,
    enabled = true,
)

/**
 * Suggestion chip - non-selectable, one-tap action affordance (e.g. a suggested app).
 */
@Composable
fun SidrSuggestionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SidrPressInvertChip(
    label = label,
    selected = false,
    onClick = onClick,
    modifier = modifier,
    enabled = true,
)

/**
 * Action chip - non-selectable, one-tap action affordance (e.g. "Clear").
 */
@Composable
fun SidrActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = SidrPressInvertChip(
    label = label,
    selected = false,
    onClick = onClick,
    modifier = modifier,
    enabled = true,
)

/**
 * Status chip - fixed semantic status token, never the accent (spec §5.3). The dot + label carry meaning;
 * colour is never the sole signal (R-ADL-2).
 */
@Composable
fun SidrStatusChip(
    label: String,
    status: SidrStatus,
    modifier: Modifier = Modifier,
) {
    val colors = SidrTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SidrStatusMarker(status = status, label = label)
    }
}

/**
 * Risk chip - always includes text + marker, never colour alone (spec §5.3, §4.4). Maps [tone] to a fixed
 * status token so risk reads the same regardless of source.
 */
@Composable
fun SidrRiskChip(
    label: String,
    tone: SidrRiskTone,
    modifier: Modifier = Modifier,
) {
    SidrStatusChip(label = label, status = tone.status(), modifier = modifier)
}

/**
 * Shared internal press-invert chip frame. Selected/pressed flips fg/bg; no glow, no scale, no layout shift.
 * Disabled chips do not fire callbacks (spec §7).
 *
 * [bordered] controls only the **resting** hairline frame; the press-invert fill is unconditional, so a
 * borderless chip loses no state signal. Borderless chips keep the identical box model (same shape,
 * same padding, same 48dp minimum) — dropping the border must not move anything.
 */
@Composable
private fun SidrPressInvertChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    bordered: Boolean = true,
) {
    val colors = SidrTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = selected || pressed
    // Press-invert to the light text token (artifact `.chip.on`: bg = text, fg = ground); resting is a
    // hairline-bordered transparent chip in `dim`. No glow, no scale, no layout shift.
    val fg = if (active) colors.ground else colors.dim
    val bg = if (active) colors.text else Color.Transparent
    val chipBorder = if (!enabled) colors.line else if (active) colors.text else colors.border
    val chipShape = SidrShapes.small
    Box(
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .clip(chipShape)
            .background(bg)
            .let { base ->
                if (bordered) base.border(BorderStroke(Strokes.hairline, chipBorder), chipShape) else base
            }
            .semantics { role = Role.Button }
            .let { base ->
                if (enabled) base.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = label,
                    role = Role.Button,
                    onClick = onClick,
                ) else base
            }
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        SidrText(
            text = label,
            role = SidrTextRole.SYSTEM,
            color = if (enabled) fg else colors.faint,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrChipPreview() {
    val colors = SidrTheme.colors
    SidrTheme(darkTheme = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            SidrRouteChip("APP", selected = true, onClick = {})
            SidrRouteChip("WEB", selected = false, onClick = {})
            SidrFilterChip("8", selected = true, onClick = {})
            SidrSuggestionChip("Open Clock", onClick = {})
            SidrStatusChip("LOCAL", SidrStatus.SUCCESS)
            SidrRiskChip("SAFE", SidrRiskTone.Safe)
            SidrRiskChip("CONFIRM", SidrRiskTone.Confirm)
            SidrRiskChip("EXTERNAL", SidrRiskTone.External)
            SidrRiskChip("DESTRUCTIVE", SidrRiskTone.Destructive)
        }
    }
}
