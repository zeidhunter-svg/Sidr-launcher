package com.sidr.launcher.core.ui.primitive

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.sidr.launcher.core.ui.theme.SidrColors
import com.sidr.launcher.core.ui.theme.SidrTextStyles
import com.sidr.launcher.core.ui.theme.SidrTheme

/**
 * The tri-font entry point (DS-2). A role picks the SIDR text style + default colour so callers stop
 * hand-selecting `MaterialTheme.typography`. Interface = mono; prose = sans; sacred = serif (grey spec §3).
 */
enum class SidrTextRole { COMMAND, SYSTEM, PROVENANCE, SACRED, HUMAN_BODY, HUMAN_TITLE, LABEL }

internal fun SidrTextRole.textStyle(styles: SidrTextStyles, typography: Typography): TextStyle = when (this) {
    SidrTextRole.COMMAND -> styles.command
    SidrTextRole.SYSTEM -> styles.system
    SidrTextRole.PROVENANCE -> styles.provenance
    SidrTextRole.SACRED -> styles.sacred
    SidrTextRole.HUMAN_BODY -> typography.bodyMedium
    SidrTextRole.HUMAN_TITLE -> typography.titleMedium
    SidrTextRole.LABEL -> typography.labelSmall
}

internal fun SidrTextRole.defaultColor(colors: SidrColors): Color = when (this) {
    SidrTextRole.COMMAND -> colors.text
    SidrTextRole.SYSTEM -> colors.dim
    SidrTextRole.PROVENANCE -> colors.faint
    SidrTextRole.SACRED -> colors.sacred
    SidrTextRole.HUMAN_BODY -> colors.text
    SidrTextRole.HUMAN_TITLE -> colors.text
    SidrTextRole.LABEL -> colors.dim
}

@Composable
fun SidrText(
    text: String,
    role: SidrTextRole,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val resolved = if (color == Color.Unspecified) role.defaultColor(SidrTheme.colors) else color
    Text(
        text = text,
        modifier = modifier,
        style = role.textStyle(SidrTheme.textStyles, MaterialTheme.typography),
        color = resolved,
        maxLines = maxLines,
        overflow = overflow,
    )
}
