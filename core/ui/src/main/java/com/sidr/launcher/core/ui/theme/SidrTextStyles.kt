package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SIDR role text styles beyond the Material set (2026-07-10 visual-identity spec §3). Mono = the interface
 * shell; serif = sacred. Read via `SidrTheme.textStyles`.
 */
data class SidrTextStyles(
    val command: TextStyle,
    val system: TextStyle,
    val provenance: TextStyle,
    val sacred: TextStyle,
) {
    companion object {
        val Default = SidrTextStyles(
            command = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
            system = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp),
            provenance = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp),
            sacred = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 26.sp),
        )
    }
}
