package com.sidr.launcher.core.ui.primitive

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.theme.SidrTheme

/**
 * Quiet progress (DS-2). `null` = indeterminate, a value = determinate. Shown only while a real process
 * runs (Sukun `R-SUKUN-1`); idle surfaces render none. Accent used sparingly on a `line` track.
 */
@Composable
fun SidrProgress(modifier: Modifier = Modifier, progress: Float? = null) {
    val colors = SidrTheme.colors
    if (progress == null) {
        LinearProgressIndicator(modifier = modifier, color = colors.accent, trackColor = colors.line)
    } else {
        LinearProgressIndicator(
            progress = { progress },
            modifier = modifier,
            color = colors.accent,
            trackColor = colors.line,
        )
    }
}
