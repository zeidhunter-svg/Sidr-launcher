package com.sidr.launcher.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Retained for source compatibility; the "soft classic grey" identity (2026-07-10 visual-identity spec)
 * ignores it — the theme resolves grey for every value. A future grey-temperature choice (neutral/warm/
 * cool) may reuse this enum. The user-facing accent switcher is retired in a later DS block; the stored
 * `accentColor` preference key is intentionally left untouched.
 */
enum class AccentColor { GREEN, AMBER }

/**
 * Root theme for every Sidr surface. Wrap the app content (and Compose `@Preview`s) in this.
 *
 * Colour resolution order:
 *  1. If [dynamicColor] and the platform is API 31+, use Material You wallpaper-derived colours.
 *  2. Otherwise use the fixed **soft classic grey** scheme for [darkTheme] ([accent] is ignored).
 *
 * **Dynamic colour is off by default**: SIDR is a bespoke neutral-grey palette, which wallpaper-derived
 * Material You colour would erase. Callers may opt back in per-surface.
 *
 * Alongside the Material [androidx.compose.material3.ColorScheme], the SIDR semantic roles ([SidrColors])
 * are provided via [LocalSidrColors] and read through `SidrTheme.colors`; role text styles through
 * `SidrTheme.textStyles`. Typography (tri-font) and the softened shape scale apply regardless of colour
 * source, so the product keeps its identity even under dynamic colour.
 *
 * @param darkTheme follow the system setting by default; the dark scheme is the primary identity.
 * @param accent retained for compat; **ignored** (grey for all values).
 * @param dynamicColor opt into Material You on supported devices (default **off** for brand fidelity).
 */
@Composable
fun SidrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.GREEN,   // retained for compat; ignored (grey identity)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val sidrColors = if (darkTheme) SidrDarkColors else SidrLightColors
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> GreyDarkColorScheme
        else -> GreyLightColorScheme
    }

    CompositionLocalProvider(LocalSidrColors provides sidrColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SidrTypography,
            shapes = SidrShapes,
            content = content,
        )
    }
}

/** Accessors for SIDR roles beyond Material — `SidrTheme.colors` / `SidrTheme.textStyles`. */
object SidrTheme {
    val colors: SidrColors
        @Composable @ReadOnlyComposable get() = LocalSidrColors.current
    val textStyles: SidrTextStyles
        @Composable @ReadOnlyComposable get() = SidrTextStyles.Default
}
