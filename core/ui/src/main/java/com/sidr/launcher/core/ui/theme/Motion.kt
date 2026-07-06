package com.sidr.launcher.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp

/**
 * Whether CRT / terminal motion (the blinking block caret, and other animated affordances) is enabled.
 *
 * DF-5 (AIL-6) motion is **LOW_END-gated**: `:app` provides `false` on LOW_END devices so the launcher
 * degrades to a fully static terminal look with no per-frame animation or overlay. Defaults to `true`
 * (motion on) so `@Preview`s and tests exercise the animated path without wiring the device profile.
 */
val LocalSidrMotionEnabled = staticCompositionLocalOf { true }

/** Default spacing between CRT scanlines. */
private val ScanlineGap = 3.dp

/**
 * Draws faint horizontal CRT scanlines **on top of** this element's content (DF-5). Static — the lines
 * do not animate, so the cost is a handful of GPU line draws per frame and nothing on LOW_END where the
 * caller passes [enabled] = false (the overlay is skipped entirely, leaving a clean flat surface).
 *
 * Presentation-only; no dependency beyond `core/ui`. Intended to wrap the whole app surface once at the
 * composition root so the phosphor scanlines read across every screen and navigation transition.
 *
 * @param enabled when false the overlay is not drawn (LOW_END fallback / motion off).
 * @param color the scanline colour; use the brand accent at a very low alpha for a phosphor feel.
 */
fun Modifier.sidrScanlines(
    enabled: Boolean,
    color: Color,
): Modifier = this.drawWithContent {
    drawContent()
    if (!enabled) return@drawWithContent
    val gap = ScanlineGap.toPx().coerceAtLeast(1f)
    val stroke = 1f
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke)
        y += gap
    }
}
