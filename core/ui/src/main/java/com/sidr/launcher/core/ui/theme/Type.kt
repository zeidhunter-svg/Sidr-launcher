package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sidr.launcher.core.ui.R

/**
 * Sidr typography — **tri-font** roles (2026-07-10 visual-identity spec §3, amended by DS-11 B
 * 2026-08-10). The boundary is *command vs interface vs sacred*.
 *
 * **DS-11 B narrowed mono's territory.** It used to own the whole interface shell — chips, tab labels,
 * section labels, settings labels, every small caption — which is what made the UI read "technological"
 * rather than calm. Mono is now reserved for the two places where fixed-advance type carries actual
 * meaning, and everything else moved to sans:
 *
 * - **Mono — JetBrains Mono** ([JetBrainsMono], bundled Regular/Medium/SemiBold/Bold; OFL, see
 *   `core/ui/OFL-JetBrainsMono.txt`): **only** the `>` command line and the provenance line. These are
 *   the terminal signature Master Plan §6.3 preserves, and both genuinely want column alignment.
 * - **Sans — IBM Plex Sans** ([SidrSans], bundled Regular/Medium/SemiBold/Bold; OFL, see
 *   `core/ui/OFL-IBMPlexSans.txt`): everything else — prose, titles, buttons, settings copy, chips,
 *   tab labels, status and section labels. Bundled rather than [FontFamily.SansSerif] so the shell
 *   stops inheriting whichever face the OEM ships, and so Latin/Cyrillic/Greek/Turkish render from one
 *   known file (a prerequisite for the i18n block).
 * - **Serif — system serif** ([SidrSerif]): the Shahada / sacred. Deliberately untouched — Master Plan
 *   §6.2 defers the final Arabic-capable sacred face to DS-6A, after shaping/RTL/diacritics/licence
 *   review. No sans candidate carries Arabic, so this is a separate face by necessity, not oversight.
 *
 * Role styles beyond the Material set live in [SidrTextStyles].
 */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/**
 * Human/interface face — bundled IBM Plex Sans (OFL, `core/ui/OFL-IBMPlexSans.txt`). Humanist and warm
 * rather than geometric, and part of a superfamily (IBM Plex Sans Arabic / IBM Plex Mono) if the sacred
 * or mono roles ever need to join it.
 */
val SidrSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

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
    // DS-11 B: label slots moved mono → sans. They carry app labels, chips and button text — interface,
    // not command — and mono there was the single biggest source of the "technological" read.
    labelLarge = TextStyle(
        fontFamily = SidrSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp,
    ),
    // Section headers ("SUGGESTIONS", "FAVORITES") — small and uppercase-friendly. Tracking dropped
    // 1.6 → 0.8sp with the move to sans: the wide CRT tracking existed to loosen mono's dense fixed
    // advance, and carrying it over to a proportional face just reads as spaced-out text.
    labelSmall = TextStyle(
        fontFamily = SidrSans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
    ),
)
