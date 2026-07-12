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
 * The brand theme — [GREY] (the "soft classic grey" default) or one of the two historical AIL-0
 * full brand identities, [GREEN]/[AMBER], reactivated as real, complete user choices (owner
 * decision, 2026-07-12): each resolves its own ground/surface/text/dim/faint/accent (see
 * [sidrColorsFor]), not just an accent hue swap on top of grey. [SidrColors.sacred] and every
 * status colour stay fixed no matter which is picked, so the Shahada's colour never changes and
 * risk status colours never get confused with the theme's accent.
 */
enum class AccentColor { GREY, GREEN, AMBER }

/**
 * Root theme for every Sidr surface. Wrap the app content (and Compose `@Preview`s) in this.
 *
 * Colour resolution order:
 *  1. If [dynamicColor] and the platform is API 31+, use Material You wallpaper-derived colours.
 *  2. Otherwise use the full palette [sidrColorsFor] resolves for [accent] × [darkTheme].
 *
 * **Dynamic colour is off by default**: SIDR is a bespoke palette, which wallpaper-derived
 * Material You colour would erase. Callers may opt back in per-surface (dynamic colour takes over the
 * whole Material scheme, so [accent] has no effect when [dynamicColor] is active).
 *
 * Alongside the Material [androidx.compose.material3.ColorScheme], the SIDR semantic roles ([SidrColors])
 * are provided via [LocalSidrColors] and read through `SidrTheme.colors`; role text styles through
 * `SidrTheme.textStyles`. Typography (tri-font) and the softened shape scale apply regardless of colour
 * source, so the product keeps its identity even under dynamic colour.
 *
 * @param darkTheme follow the system setting by default; the dark scheme is the primary identity.
 * @param accent the brand theme; [AccentColor.GREY] is the soft-classic-grey default.
 * @param dynamicColor opt into Material You on supported devices (default **off** for brand fidelity).
 */
@Composable
fun SidrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentColor = AccentColor.GREY,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val sidrColors = sidrColorsFor(accent, darkTheme)
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> greyColorScheme(darkTheme, sidrColors)
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
