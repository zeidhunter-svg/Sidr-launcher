package com.sidr.launcher.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Material [androidx.compose.material3.ColorScheme] built from whichever full [SidrColors] palette
 * [sidrColorsFor] resolved (grey/green/amber) — every Material slot (`background`/`surface`/`primary`/
 * etc.) mirrors that palette exactly the way `SidrTheme.colors` does, so the two stay in lockstep for
 * any theme. SIDR-specific roles (fixed status, sacred, provenance) live in [SidrColors]; screens read
 * Material slots off `MaterialTheme.colorScheme` and SIDR roles off `SidrTheme.colors`.
 */
internal fun greyColorScheme(darkTheme: Boolean, colors: SidrColors) = if (darkTheme) {
    darkColorScheme(
        primary = colors.accent, onPrimary = colors.ground,
        primaryContainer = colors.raised, onPrimaryContainer = colors.text,
        secondary = colors.dim, onSecondary = colors.ground,
        secondaryContainer = colors.surface, onSecondaryContainer = colors.text,
        tertiary = colors.accent, onTertiary = colors.ground,
        error = colors.danger, onError = colors.ground,
        errorContainer = colors.surface, onErrorContainer = colors.danger,
        background = colors.ground, onBackground = colors.text,
        surface = colors.surface, onSurface = colors.text,
        surfaceVariant = colors.raised, onSurfaceVariant = colors.dim,
        outline = colors.border, outlineVariant = colors.line,
    )
} else {
    lightColorScheme(
        primary = colors.accent, onPrimary = colors.raised,
        primaryContainer = colors.surface, onPrimaryContainer = colors.text,
        secondary = colors.dim, onSecondary = colors.raised,
        secondaryContainer = colors.surface, onSecondaryContainer = colors.text,
        tertiary = colors.accent, onTertiary = colors.raised,
        error = colors.danger, onError = colors.raised,
        errorContainer = colors.surface, onErrorContainer = colors.danger,
        background = colors.ground, onBackground = colors.text,
        surface = colors.surface, onSurface = colors.text,
        surfaceVariant = colors.raised, onSurfaceVariant = colors.dim,
        outline = colors.border, outlineVariant = colors.line,
    )
}
