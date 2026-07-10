package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Material [androidx.compose.material3.ColorScheme] built from the "soft classic grey" identity
 * ([SidrDarkColors]/[SidrLightColors]). SIDR-specific roles (accent vs fixed status, sacred, provenance)
 * live in [SidrColors]; screens read Material slots off `MaterialTheme.colorScheme` and SIDR roles off
 * `SidrTheme.colors`. The prior green/amber terminal schemes were retired by the 2026-07-10 visual-identity
 * spec.
 */
internal val GreyDarkColorScheme = darkColorScheme(
    primary = SidrDarkColors.accent, onPrimary = SidrDarkColors.ground,
    primaryContainer = SidrDarkColors.raised, onPrimaryContainer = SidrDarkColors.text,
    secondary = SidrDarkColors.dim, onSecondary = SidrDarkColors.ground,
    secondaryContainer = SidrDarkColors.surface, onSecondaryContainer = SidrDarkColors.text,
    tertiary = SidrDarkColors.accent, onTertiary = SidrDarkColors.ground,
    error = SidrDarkColors.danger, onError = SidrDarkColors.ground,
    errorContainer = SidrDarkColors.surface, onErrorContainer = SidrDarkColors.danger,
    background = SidrDarkColors.ground, onBackground = SidrDarkColors.text,
    surface = SidrDarkColors.surface, onSurface = SidrDarkColors.text,
    surfaceVariant = SidrDarkColors.raised, onSurfaceVariant = SidrDarkColors.dim,
    outline = SidrDarkColors.border, outlineVariant = SidrDarkColors.line,
)

internal val GreyLightColorScheme = lightColorScheme(
    primary = SidrLightColors.accent, onPrimary = SidrLightColors.raised,
    primaryContainer = SidrLightColors.surface, onPrimaryContainer = SidrLightColors.text,
    secondary = SidrLightColors.dim, onSecondary = SidrLightColors.raised,
    secondaryContainer = SidrLightColors.surface, onSecondaryContainer = SidrLightColors.text,
    tertiary = SidrLightColors.accent, onTertiary = SidrLightColors.raised,
    error = SidrLightColors.danger, onError = SidrLightColors.raised,
    errorContainer = SidrLightColors.surface, onErrorContainer = SidrLightColors.danger,
    background = SidrLightColors.ground, onBackground = SidrLightColors.text,
    surface = SidrLightColors.surface, onSurface = SidrLightColors.text,
    surfaceVariant = SidrLightColors.raised, onSurfaceVariant = SidrLightColors.dim,
    outline = SidrLightColors.border, outlineVariant = SidrLightColors.line,
)
