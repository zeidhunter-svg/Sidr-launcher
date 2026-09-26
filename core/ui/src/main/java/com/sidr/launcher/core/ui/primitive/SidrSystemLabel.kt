package com.sidr.launcher.core.ui.primitive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.util.Locale

/**
 * Mono, uppercase, wide-tracking system/section label (DS-2). Backs — does not yet replace — the existing
 * `SectionHeader`; production migration is DS-3.
 */
@Composable
fun SidrSystemLabel(text: String, modifier: Modifier = Modifier) {
    // DISPLAY: [text] is caller-supplied human copy, so it folds under the user's locale.
    SidrText(text = text.uppercase(Locale.getDefault()), role = SidrTextRole.SYSTEM, modifier = modifier)
}
