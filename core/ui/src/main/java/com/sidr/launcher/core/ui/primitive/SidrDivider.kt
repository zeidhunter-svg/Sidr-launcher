package com.sidr.launcher.core.ui.primitive

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Strokes

/** 1px hairline divider in the `line` role (DS-2). */
@Composable
fun SidrDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = Strokes.hairline, color = SidrTheme.colors.line)
}
