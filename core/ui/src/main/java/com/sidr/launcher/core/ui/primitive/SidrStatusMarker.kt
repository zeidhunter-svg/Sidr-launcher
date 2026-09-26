package com.sidr.launcher.core.ui.primitive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.theme.SidrColors
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/** Fixed semantic status (DS-2). Never the accent; the dot's meaning is carried by the text label. */
enum class SidrStatus { SUCCESS, ATTENTION, CAUTION, DANGER, INFO }

internal fun SidrStatus.color(colors: SidrColors): Color = when (this) {
    SidrStatus.SUCCESS -> colors.success
    SidrStatus.ATTENTION -> colors.attention
    SidrStatus.CAUTION -> colors.caution
    SidrStatus.DANGER -> colors.danger
    SidrStatus.INFO -> colors.info
}

/**
 * Status shown as a coloured dot **plus** a text label (Adl `R-ADL-2`: risk/status never by colour alone) —
 * readable in greyscale and by TalkBack (the dot is decorative; the label carries meaning).
 */
@Composable
fun SidrStatusMarker(status: SidrStatus, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(status.color(SidrTheme.colors)))
        SidrText(text = label, role = SidrTextRole.SYSTEM)
    }
}
