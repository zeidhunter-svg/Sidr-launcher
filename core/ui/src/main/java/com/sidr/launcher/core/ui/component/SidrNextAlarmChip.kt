package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * The next-alarm chip of Home's date line (A4′ acceptance tail, task 8; owner ruling 5: а — at the line's very start,
 * the date staying centred). Borderless and quiet, in the date line's own `SYSTEM` role and `dim` colour, with the chip
 * family's press-invert (spec §4.5: no glow, no scale, no layout shift) and the 48 dp touch minimum.
 *
 * [text] is what it shows («⏰ 07:30») and [contentDescription] what it says («Next alarm: 07:30») — both the CALLER's:
 * the feature layer formats the time by the device's 12/24-hour setting and picks the strings. The chip is ONE button
 * (`clickable` merges its text into it) whose [contentDescription] is what a screen reader says instead of the glyph.
 *
 * Presentation-only: no domain/data/feature imports (`ControlsDependencyGuardTest`).
 */
@Composable
fun SidrNextAlarmChip(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SidrTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .clip(SidrShapes.small)
            .background(if (pressed) colors.text else Color.Transparent)
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        SidrText(
            text = text,
            role = SidrTextRole.SYSTEM,
            color = if (pressed) colors.ground else colors.dim,
        )
    }
}
