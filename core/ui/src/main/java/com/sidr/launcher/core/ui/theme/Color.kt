package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Sidr colour tokens — a "modern ultra-cyberpunk / early-computer terminal" identity (AIL-0).
 *
 * The brand is a phosphor CRT: a near-black ground with a single luminous accent doing all the work.
 * Two accents ship — **green (`#00FF66`, default brand)** and **amber (`#FFB000`, alternative)** — each
 * with a **dark** identity scheme and a restrained **light** "blueprint / paper terminal" scheme, giving
 * four schemes selected in [SidrTheme] by [AccentColor] × dark/light.
 *
 * Dynamic colour (Material You) is deliberately **off by default** in [SidrTheme]: a bespoke brand and
 * wallpaper-derived colour are mutually exclusive. Keep raw colours private to this file; consumers read
 * semantic roles off `MaterialTheme.colorScheme`, never these literals.
 *
 * Elevation is expressed through 1px `outline`/`outlineVariant` grid borders rather than tonal shadow, so
 * the dark schemes keep `surface` close to `background`. Animated glow is a motion concern deferred to the
 * screen blocks (AIL-3/5/6).
 */

// ── Shared ─────────────────────────────────────────────────────────────────────
private val Ground = Color(0xFF08090A)   // near-black CRT ground (both dark schemes)
private val TerminalError = Color(0xFFFF5B51)
private val LightError = Color(0xFFB3261E)
private val LightErrorContainer = Color(0xFFF9DEDC)
private val OnLightError = Color(0xFFFFFFFF)
private val OnLightErrorContainer = Color(0xFF410E0B)
private val OnTerminalError = Color(0xFF1A0301)
private val TerminalErrorContainer = Color(0xFF3A0F0C)
private val OnTerminalErrorContainer = Color(0xFFFFB4AB)

// ── GREEN · DARK (default brand identity) ───────────────────────────────────────
internal val GreenDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00FF66),
    onPrimary = Color(0xFF05100A),
    primaryContainer = Color(0xFF12261B),
    onPrimaryContainer = Color(0xFF6EFFA8),
    secondary = Color(0xFF6F8377),
    onSecondary = Color(0xFF08090A),
    secondaryContainer = Color(0xFF1E2922),
    onSecondaryContainer = Color(0xFFC2D2C7),
    tertiary = Color(0xFF6EFFA8),
    onTertiary = Color(0xFF05100A),
    tertiaryContainer = Color(0xFF12261B),
    onTertiaryContainer = Color(0xFF6EFFA8),
    error = TerminalError,
    onError = OnTerminalError,
    errorContainer = TerminalErrorContainer,
    onErrorContainer = OnTerminalErrorContainer,
    background = Ground,
    onBackground = Color(0xFFC2D2C7),
    surface = Color(0xFF0D100E),
    onSurface = Color(0xFFC2D2C7),
    surfaceVariant = Color(0xFF171E19),
    onSurfaceVariant = Color(0xFF6F8377),
    outline = Color(0xFF2A3A30),
    outlineVariant = Color(0xFF1E2922),
)

// ── GREEN · LIGHT (restrained "blueprint / paper terminal") ─────────────────────
internal val GreenLightColorScheme = lightColorScheme(
    primary = Color(0xFF087A38),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE8D6),
    onPrimaryContainer = Color(0xFF05210F),
    secondary = Color(0xFF4A5A50),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE3DD),
    onSecondaryContainer = Color(0xFF0C1410),
    tertiary = Color(0xFF0A5C2C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDE8D6),
    onTertiaryContainer = Color(0xFF05210F),
    error = LightError,
    onError = OnLightError,
    errorContainer = LightErrorContainer,
    onErrorContainer = OnLightErrorContainer,
    background = Color(0xFFE7EAE6),
    onBackground = Color(0xFF0C1410),
    surface = Color(0xFFF1F4F0),
    onSurface = Color(0xFF0C1410),
    surfaceVariant = Color(0xFFDCE3DD),
    onSurfaceVariant = Color(0xFF4A5A50),
    outline = Color(0xFF9FB0A4),
    outlineVariant = Color(0xFFC2CDC4),
)

// ── AMBER · DARK ────────────────────────────────────────────────────────────────
internal val AmberDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFB000),
    onPrimary = Color(0xFF100B02),
    primaryContainer = Color(0xFF2A2113),
    onPrimaryContainer = Color(0xFFFFD37A),
    secondary = Color(0xFF8A7C5C),
    onSecondary = Color(0xFF08090A),
    secondaryContainer = Color(0xFF2A2416),
    onSecondaryContainer = Color(0xFFDFCFA6),
    tertiary = Color(0xFFFFD37A),
    onTertiary = Color(0xFF100B02),
    tertiaryContainer = Color(0xFF2A2113),
    onTertiaryContainer = Color(0xFFFFD37A),
    error = TerminalError,
    onError = OnTerminalError,
    errorContainer = TerminalErrorContainer,
    onErrorContainer = OnTerminalErrorContainer,
    background = Ground,
    onBackground = Color(0xFFDFCFA6),
    surface = Color(0xFF100E0A),
    onSurface = Color(0xFFDFCFA6),
    surfaceVariant = Color(0xFF1B160D),
    onSurfaceVariant = Color(0xFF8A7C5C),
    outline = Color(0xFF3A3320),
    outlineVariant = Color(0xFF2A2416),
)

// ── AMBER · LIGHT ───────────────────────────────────────────────────────────────
internal val AmberLightColorScheme = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0E4C4),
    onPrimaryContainer = Color(0xFF2B1B00),
    secondary = Color(0xFF5A5238),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2DBCB),
    onSecondaryContainer = Color(0xFF171308),
    tertiary = Color(0xFF6E4700),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0E4C4),
    onTertiaryContainer = Color(0xFF2B1B00),
    error = LightError,
    onError = OnLightError,
    errorContainer = LightErrorContainer,
    onErrorContainer = OnLightErrorContainer,
    background = Color(0xFFECE8DF),
    onBackground = Color(0xFF171308),
    surface = Color(0xFFF5F2EA),
    onSurface = Color(0xFF171308),
    surfaceVariant = Color(0xFFE2DBCB),
    onSurfaceVariant = Color(0xFF5A5238),
    outline = Color(0xFFADA283),
    outlineVariant = Color(0xFFCDC4AC),
)
