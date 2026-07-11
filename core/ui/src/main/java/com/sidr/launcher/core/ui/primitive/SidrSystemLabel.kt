package com.sidr.launcher.core.ui.primitive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Mono, uppercase, wide-tracking system/section label (DS-2). Backs — does not yet replace — the existing
 * `SectionHeader`; production migration is DS-3.
 */
@Composable
fun SidrSystemLabel(text: String, modifier: Modifier = Modifier) {
    SidrText(text = text.uppercase(), role = SidrTextRole.SYSTEM, modifier = modifier)
}
