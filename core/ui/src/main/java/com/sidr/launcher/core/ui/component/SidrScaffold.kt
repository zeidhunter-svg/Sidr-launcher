package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Thin M3 [Scaffold] wrapper with Sidr defaults: the themed background as the container colour and
 * an optional top bar. Content receives the inset [PaddingValues]; callers must consume them
 * (`Modifier.padding(inner)`) so content clears the top bar / system bars.
 *
 * Presentation only — no state, no navigation. Keeps every full screen consistent without each
 * feature re-deriving Scaffold defaults.
 */
@Composable
fun SidrScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = topBar,
        content = content,
    )
}
