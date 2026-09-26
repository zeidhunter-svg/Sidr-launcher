package com.sidr.launcher.core.ui.theme

import androidx.compose.ui.tooling.preview.Preview

/**
 * Multipreview establishing the dark + light "soft classic grey" baseline (DS-1). Annotate a `@Composable`
 * preview with `@SidrThemePreviews` to render it in both themes at once (IDE + screenshot host).
 */
@Preview(name = "Dark", uiMode = 0x21)   // UI_MODE_NIGHT_YES | UI_MODE_TYPE_NORMAL
@Preview(name = "Light", uiMode = 0x11)  // UI_MODE_NIGHT_NO  | UI_MODE_TYPE_NORMAL
annotation class SidrThemePreviews
