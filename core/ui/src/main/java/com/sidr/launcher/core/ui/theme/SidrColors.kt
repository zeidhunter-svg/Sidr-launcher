package com.sidr.launcher.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * SIDR semantic colour roles — the identity layer sitting alongside the Material `ColorScheme`.
 *
 * "Soft classic grey" (2026-07-10 visual-identity spec) is the default identity, with two full
 * alternate identities — the historical AIL-0 **green** / **amber** brand accents — reactivated as
 * real, user-selectable themes (owner decision, 2026-07-12): each has its own ground/surface/
 * text/dim/faint/accent tuned to its hue, exactly like grey does. Two things stay fixed no matter
 * which of the three the user picks: [sacred] (the Shahada's colour never changes) and the status
 * roles ([success]/[attention]/[caution]/[danger]/[info], so risk always reads the same regardless
 * of theme). Consumers read these via `SidrTheme.colors`; raw values stay private-ish to this file.
 */
@Immutable
data class SidrColors(
    val ground: Color,
    val surface: Color,
    val raised: Color,
    val line: Color,
    val border: Color,
    val text: Color,
    val sacred: Color,
    val dim: Color,
    val faint: Color,
    val accent: Color,
    val accentBorder: Color,
    // fixed semantic status — never re-tinted by the theme/accent choice
    val success: Color,
    val attention: Color,
    val caution: Color,
    val danger: Color,
    val info: Color,
)

val SidrDarkColors = SidrColors(
    ground = Color(0xFF131415), surface = Color(0xFF1B1C1E), raised = Color(0xFF212325),
    line = Color(0xFF2A2C30), border = Color(0xFF3A3D42),
    text = Color(0xFFE8E9EB), sacred = Color(0xFFCBCDD1), dim = Color(0xFFA0A2A8), faint = Color(0xFF6E7076),
    accent = Color(0xFF9BA1AB), accentBorder = Color(0xFF494D54),
    success = Color(0xFF8AA892), attention = Color(0xFFC6A15C), caution = Color(0xFFB8836A),
    danger = Color(0xFFC2695C), info = Color(0xFF8593A0),
)

val SidrLightColors = SidrColors(
    ground = Color(0xFFECEDED), surface = Color(0xFFF5F6F7), raised = Color(0xFFFFFFFF),
    line = Color(0xFFE0E1E4), border = Color(0xFFC9CBCF),
    text = Color(0xFF1C1E21), sacred = Color(0xFF3D4046), dim = Color(0xFF5C5F65), faint = Color(0xFF8A8D93),
    accent = Color(0xFF5F6773), accentBorder = Color(0xFFBCC0C6),
    success = Color(0xFF5E7D66), attention = Color(0xFF94702E), caution = Color(0xFF9A6142),
    danger = Color(0xFFA24A3E), info = Color(0xFF5B6675),
)

// ── Full green/amber themes (2026-07-12 owner decision) — ground/surface/text/dim/faint/accent
// reused verbatim from the pre-DS-1 AIL-0 identity (commit bafd0f3's Green/AmberDark/LightColorScheme:
// background→ground, surface→surface, surfaceVariant→raised, outlineVariant→line, outline→border/
// accentBorder, onBackground→text, secondary→dim, primary→accent). `faint` has no AIL-0 equivalent
// (that palette never needed a fourth grey step) so it's interpolated between `dim` and `border` in
// the same hue family. `sacred` and every status colour are pinned to the grey palette's values
// (via SidrDarkColors/SidrLightColors) regardless of theme — never re-derived per accent.

private val GreenDark = SidrColors(
    ground = Color(0xFF08090A), surface = Color(0xFF0D100E), raised = Color(0xFF171E19),
    line = Color(0xFF1E2922), border = Color(0xFF2A3A30),
    text = Color(0xFFC2D2C7), sacred = SidrDarkColors.sacred, dim = Color(0xFF6F8377), faint = Color(0xFF4A5B50),
    accent = Color(0xFF00FF66), accentBorder = Color(0xFF2A3A30),
    success = SidrDarkColors.success, attention = SidrDarkColors.attention, caution = SidrDarkColors.caution,
    danger = SidrDarkColors.danger, info = SidrDarkColors.info,
)

private val GreenLight = SidrColors(
    ground = Color(0xFFE7EAE6), surface = Color(0xFFF1F4F0), raised = Color(0xFFDCE3DD),
    line = Color(0xFFC2CDC4), border = Color(0xFF9FB0A4),
    text = Color(0xFF0C1410), sacred = SidrLightColors.sacred, dim = Color(0xFF4A5A50), faint = Color(0xFF7C8A7F),
    accent = Color(0xFF087A38), accentBorder = Color(0xFF9FB0A4),
    success = SidrLightColors.success, attention = SidrLightColors.attention, caution = SidrLightColors.caution,
    danger = SidrLightColors.danger, info = SidrLightColors.info,
)

private val AmberDark = SidrColors(
    ground = Color(0xFF08090A), surface = Color(0xFF100E0A), raised = Color(0xFF1B160D),
    line = Color(0xFF2A2416), border = Color(0xFF3A3320),
    text = Color(0xFFDFCFA6), sacred = SidrDarkColors.sacred, dim = Color(0xFF8A7C5C), faint = Color(0xFF5A5236),
    accent = Color(0xFFFFB000), accentBorder = Color(0xFF3A3320),
    success = SidrDarkColors.success, attention = SidrDarkColors.attention, caution = SidrDarkColors.caution,
    danger = SidrDarkColors.danger, info = SidrDarkColors.info,
)

private val AmberLight = SidrColors(
    ground = Color(0xFFECE8DF), surface = Color(0xFFF5F2EA), raised = Color(0xFFE2DBCB),
    line = Color(0xFFCDC4AC), border = Color(0xFFADA283),
    text = Color(0xFF171308), sacred = SidrLightColors.sacred, dim = Color(0xFF5A5238), faint = Color(0xFF8A8060),
    accent = Color(0xFF8A5A00), accentBorder = Color(0xFFADA283),
    success = SidrLightColors.success, attention = SidrLightColors.attention, caution = SidrLightColors.caution,
    danger = SidrLightColors.danger, info = SidrLightColors.info,
)

/** Resolves the full [SidrColors] palette for the chosen [accent] theme + [darkTheme] mode. */
internal fun sidrColorsFor(accent: AccentColor, darkTheme: Boolean): SidrColors = when (accent) {
    AccentColor.GREY -> if (darkTheme) SidrDarkColors else SidrLightColors
    AccentColor.GREEN -> if (darkTheme) GreenDark else GreenLight
    AccentColor.AMBER -> if (darkTheme) AmberDark else AmberLight
}

/** Provided by [SidrTheme]; defaults to dark so a bare preview still renders. */
val LocalSidrColors = staticCompositionLocalOf { SidrDarkColors }
