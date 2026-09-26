package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * SIDR role text styles beyond the Material set (2026-07-10 visual-identity spec §3, amended by DS-11 B
 * 2026-08-10). Read via `SidrTheme.textStyles`.
 *
 * The mono/sans split here is the contract [com.sidr.launcher.core.ui.theme.TypographyRoleTest] pins:
 *
 * - [command] + [provenance] — **mono**. The `>` command line and the `LOCAL · 14 MS` provenance line
 *   are the terminal signature (Master Plan §6.3) and both want fixed advance for column alignment.
 * - [system] — **sans** since DS-11 B. Status chips, tab labels, small system labels: interface, not
 *   command. Tracking dropped 1.4 → 0.6sp, because the wide tracking was compensating for mono's dense
 *   fixed advance and reads as artificially spaced on a proportional face.
 * - [sacred] — **serif**, unchanged. Master Plan §6.2 defers the final Arabic-capable sacred face to
 *   DS-6A pending shaping/RTL/diacritics/licence review; `FontFamily.Serif` stays the placeholder.
 */
data class SidrTextStyles(
    val command: TextStyle,
    val system: TextStyle,
    val provenance: TextStyle,
    val caption: TextStyle,
    val sacred: TextStyle,
) {
    companion object {
        val Default = SidrTextStyles(
            command = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
            system = TextStyle(fontFamily = SidrSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
            provenance = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp),
            // Doctrine §16 "Body Small". Added by DS-11 B for secondary prose — settings/row
            // descriptions, permission explanations — which had been borrowing [provenance] simply
            // because it was the available small dim style. That was invisible while the whole shell
            // was mono; once mono means "command or attribution" it is a category error, and it left
            // the single largest block of small text in Settings reading as machine output.
            caption = TextStyle(fontFamily = SidrSans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
            sacred = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 26.sp),
        )
    }
}
