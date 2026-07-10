package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sidr.launcher.core.ui.R

/**
 * Sidr typography — **tri-font** roles (2026-07-10 visual-identity spec §3). The boundary is *interface vs
 * prose vs sacred*: the mono shell stays wide (a launcher is mostly interface, and the mono signature is
 * the recognizable identity), sans is reserved for genuine prose, serif is the sacred face.
 *
 * - **Mono — JetBrains Mono** ([JetBrainsMono], bundled Regular/Medium/SemiBold/Bold; OFL, see
 *   `core/ui/OFL-JetBrainsMono.txt`): the `>` prompt, commands, statuses, section labels, chips, captions,
 *   provenance, all metadata. Section headers keep wide tracking.
 * - **Sans — system sans** ([SidrSans]): app labels in prose contexts, buttons, descriptions, settings
 *   copy, assistant answers, long-form prose.
 * - **Serif — system serif** ([SidrSerif]): the Shahada / sacred and optionally long-form prose.
 *
 * In [SidrTypography] the body/title Material slots resolve to sans (readable prose) while the label slots
 * stay mono (the identity). Role styles beyond the Material set live in [SidrTextStyles].
 */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Human/prose face — the platform sans (system-ui). */
val SidrSans = FontFamily.SansSerif

/** Sacred face — the platform serif (Shahada / long-form prose). */
val SidrSerif = FontFamily.Serif

val SidrTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = SidrSans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SidrSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SidrSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SidrSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp,
    ),
    // Section headers ("SUGGESTIONS", "FAVORITES") — small, uppercase-friendly, wide CRT tracking.
    labelSmall = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.6.sp,
    ),
)
