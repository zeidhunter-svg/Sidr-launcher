package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flat spacing + size tokens for the Sidr design system.
 *
 * A plain `object` (not a CompositionLocal) is deliberate: the launcher has a single density scale
 * and no per-surface spacing overrides, so a global constant set is the pragmatic choice (fork U4).
 * Use these instead of ad-hoc `.dp` literals so padding/rhythm stays consistent app-wide.
 */
object Spacing {
    /** 4dp — hairline gaps, icon-to-label. */
    val xs: Dp = 4.dp

    /** 8dp — default intra-component padding. */
    val sm: Dp = 8.dp

    /** 12dp — field/tile inner padding. */
    val md: Dp = 12.dp

    /** 16dp — screen edge / section padding. */
    val lg: Dp = 16.dp

    /** 24dp — section separation. */
    val xl: Dp = 24.dp

    /** 32dp — large vertical rhythm (empty/error states). */
    val xxl: Dp = 32.dp
}

/**
 * Size tokens for touch targets and fixed-dimension elements.
 */
object Sizes {
    /** Minimum accessible touch target (Material a11y guidance). */
    val minTouchTarget: Dp = 48.dp

    /** App icon glyph box inside an [com.sidr.launcher.core.ui.component.AppTile]. */
    val appIcon: Dp = 48.dp

    /** Overall width budget for an app tile (icon + label column). */
    val appTile: Dp = 72.dp

    /** Leading/trailing icon size inside fields and rows. */
    val icon: Dp = 24.dp

    /** Illustration/glyph size for empty and error states. */
    val stateGlyph: Dp = 48.dp
}
