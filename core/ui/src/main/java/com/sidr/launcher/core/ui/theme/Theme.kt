package com.sidr.launcher.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Root theme for every Sidr surface. Wrap the app content (and Compose `@Preview`s) in this.
 *
 * Colour resolution order:
 *  1. If [dynamicColor] and the platform is API 31+, use Material You wallpaper-derived colours.
 *  2. Otherwise fall back to the static [SidrLightColorScheme] / [SidrDarkColorScheme].
 *
 * Typography and shapes are always the Sidr scale, independent of the colour source, so the
 * product keeps a consistent visual identity even under dynamic colour.
 *
 * @param darkTheme follow the system setting by default; callers may force a scheme (e.g. a
 *   user theme preference wired in a later block).
 * @param dynamicColor opt into Material You on supported devices (default on).
 */
@Composable
fun SidrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> SidrDarkColorScheme
        else -> SidrLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SidrTypography,
        shapes = SidrShapes,
        content = content,
    )
}
