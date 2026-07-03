package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Sidr colour tokens — a neutral, calm M3 palette seeded around an indigo primary.
 *
 * These are the static fallback schemes used when Material You dynamic colour is unavailable
 * (pre-API 31, or when the user opts out). Dynamic colour, when available, is derived at runtime
 * in [SidrTheme]. Keep raw colours private to this file; consumers read semantic roles off
 * `MaterialTheme.colorScheme`, never these literals.
 */

// ── Light ────────────────────────────────────────────────────────────────────
private val LightPrimary = Color(0xFF4B5BD7)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFDEE0FF)
private val LightOnPrimaryContainer = Color(0xFF00105C)
private val LightSecondary = Color(0xFF5A5D72)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFDFE1F9)
private val LightOnSecondaryContainer = Color(0xFF171B2C)
private val LightTertiary = Color(0xFF76546E)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFFFD7F3)
private val LightOnTertiaryContainer = Color(0xFF2C1229)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)
private val LightBackground = Color(0xFFFEFBFF)
private val LightOnBackground = Color(0xFF1B1B1F)
private val LightSurface = Color(0xFFFEFBFF)
private val LightOnSurface = Color(0xFF1B1B1F)
private val LightSurfaceVariant = Color(0xFFE3E1EC)
private val LightOnSurfaceVariant = Color(0xFF46464F)
private val LightOutline = Color(0xFF767680)
private val LightOutlineVariant = Color(0xFFC7C5D0)

// ── Dark ─────────────────────────────────────────────────────────────────────
private val DarkPrimary = Color(0xFFBBC3FF)
private val DarkOnPrimary = Color(0xFF001489)
private val DarkPrimaryContainer = Color(0xFF2F42BE)
private val DarkOnPrimaryContainer = Color(0xFFDEE0FF)
private val DarkSecondary = Color(0xFFC3C5DD)
private val DarkOnSecondary = Color(0xFF2C2F42)
private val DarkSecondaryContainer = Color(0xFF424659)
private val DarkOnSecondaryContainer = Color(0xFFDFE1F9)
private val DarkTertiary = Color(0xFFE5BAD8)
private val DarkOnTertiary = Color(0xFF44263F)
private val DarkTertiaryContainer = Color(0xFF5C3C56)
private val DarkOnTertiaryContainer = Color(0xFFFFD7F3)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)
private val DarkBackground = Color(0xFF1B1B1F)
private val DarkOnBackground = Color(0xFFE4E1E6)
private val DarkSurface = Color(0xFF1B1B1F)
private val DarkOnSurface = Color(0xFFE4E1E6)
private val DarkSurfaceVariant = Color(0xFF46464F)
private val DarkOnSurfaceVariant = Color(0xFFC7C5D0)
private val DarkOutline = Color(0xFF90909A)
private val DarkOutlineVariant = Color(0xFF46464F)

internal val SidrLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

internal val SidrDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)
