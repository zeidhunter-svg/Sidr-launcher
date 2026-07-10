package com.sidr.launcher.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * SIDR semantic colour roles — the identity layer sitting alongside the Material `ColorScheme`.
 *
 * "Soft classic grey" (2026-07-10 visual-identity spec). The brand [accent] (pewter) is used sparingly
 * for interactivity; the status roles ([success]/[attention]/[caution]/[danger]/[info]) are FIXED and
 * never re-tinted by the accent, so risk always reads the same. Consumers read these via
 * `SidrTheme.colors`; raw values stay private-ish to this file.
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
    // fixed semantic status — separate from accent
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

/** Provided by [SidrTheme]; defaults to dark so a bare preview still renders. */
val LocalSidrColors = staticCompositionLocalOf { SidrDarkColors }
