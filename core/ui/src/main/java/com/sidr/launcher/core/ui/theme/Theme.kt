package com.sidr.launcher.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The Sidr brand accent — the single luminous "phosphor" the terminal identity is built around.
 * [GREEN] is the default brand; [AMBER] is the alternative accent. Both are first-class schemes
 * (see `Color.kt`); a user-facing switcher over this is a later step (plan fork DF-7).
 */
enum class AccentColor { GREEN, AMBER }

/**
 * Root theme for every Sidr surface. Wrap the app content (and Compose `@Preview`s) in this.
 *
 * Colour resolution order:
 *  1. If [dynamicColor] and the platform is API 31+, use Material You wallpaper-derived colours.
 *  2. Otherwise use the fixed Sidr brand scheme for [accent] × [darkTheme].
 *
 * **Dynamic colour is off by default** (AIL-0): the cyberpunk-terminal identity is a bespoke palette,
 * which wallpaper-derived Material You colour would erase. Callers may opt back in per-surface.
 *
 * Typography (JetBrains Mono) and the brutalist shape scale are always applied regardless of the colour
 * source, so the product keeps its identity even under dynamic colour.
 *
 * @param darkTheme follow the system setting by default; the dark scheme is the primary identity.
 * @param accent brand accent; defaults to [AccentColor.GREEN].
 * @param dynamicColor opt into Material You on supported devices (default **off** for brand fidelity).
 */
@Composable
fun SidrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.GREEN,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        accent == AccentColor.AMBER && darkTheme -> AmberDarkColorScheme
        accent == AccentColor.AMBER -> AmberLightColorScheme
        darkTheme -> GreenDarkColorScheme
        else -> GreenLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SidrTypography,
        shapes = SidrShapes,
        content = content,
    )
}
