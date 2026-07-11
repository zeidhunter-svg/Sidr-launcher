package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.core.ui.theme.Strokes

/**
 * Preview primitives for not-yet-built agentic features (Vision MVP task 1). These badge/banner
 * not-live screens so a preview never reads as a shipped feature. Label text carries the meaning
 * ("PREVIEW"); the `attention` status colour is secondary, never colour-only (spec status rule).
 *
 * Presentation-only: no domain/data/feature imports.
 */

/**
 * Small muted pill labelled `PREVIEW`. Use next to a title/header to mark a not-yet-functional
 * surface at a glance.
 */
@Composable
fun SidrPreviewBadge(modifier: Modifier = Modifier) {
    val colors = SidrTheme.colors
    Box(
        modifier = modifier
            .clip(SidrShapes.small)
            .border(BorderStroke(Strokes.hairline, colors.border), SidrShapes.small)
            .padding(Spacing.sm),
    ) {
        SidrText(
            text = "PREVIEW",
            role = SidrTextRole.SYSTEM,
            color = colors.attention,
        )
    }
}

/**
 * Full-width top banner marking an entire screen as a design preview, not a live feature.
 * Marked as an a11y heading so screen readers surface it first.
 */
@Composable
fun SidrPreviewBanner(
    text: String = "PREVIEW — this screen is a design of what's coming; it isn't live yet.",
    modifier: Modifier = Modifier,
) {
    val colors = SidrTheme.colors
    SidrSurface(
        tone = SidrSurfaceTone.RAISED,
        shape = SidrShapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
    ) {
        SidrText(
            text = text,
            role = SidrTextRole.PROVENANCE,
            color = colors.attention,
            modifier = Modifier
                .padding(Spacing.md)
                .semantics { heading() },
        )
    }
}
