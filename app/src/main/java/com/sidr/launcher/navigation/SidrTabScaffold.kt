package com.sidr.launcher.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.sidr.launcher.core.ui.theme.SidrTheme

/**
 * App-shell bottom tab bar (Vision MVP Task 7). Presentation-only, scoped to `:app` (not a
 * `core/ui` design-system primitive) since it composes an app-level navigation shell, not a
 * reusable widget — but it is themed via [SidrTheme.colors] following the DS press-invert model
 * ([com.sidr.launcher.core.ui.component.SidrChip]'s `SidrPressInvertChip`): the selected tab
 * inverts to a light-text-token indicator with dark-on-light content, unselected tabs sit dim.
 *
 * No navigation logic and no route strings live here — those stay in `AppNavHost.kt`/`Routes.kt`
 * so this file stays a pure, reusable presentation component.
 */
enum class SidrTab { HOME, TASKS, AGENTS, ACTIVITY, TERMINAL }

private data class SidrTabSpec(
    val tab: SidrTab,
    val label: String,
    val icon: ImageVector,
    /** Preview (not-yet-live) tabs carry a tiny mono marker in their label (spec: status/preview
     *  is never colour-only) — see [com.sidr.launcher.core.ui.component.SidrPreviewBadge]. A full
     *  badge does not fit the compact NavigationBarItem label slot, so a `◦` marker stands in. */
    val isPreview: Boolean,
)

private val SIDR_TAB_SPECS = listOf(
    SidrTabSpec(SidrTab.HOME, "Home", Icons.Filled.Home, isPreview = false),
    SidrTabSpec(SidrTab.TASKS, "Tasks", Icons.Filled.CheckCircle, isPreview = true),
    SidrTabSpec(SidrTab.AGENTS, "Agents", Icons.Filled.Person, isPreview = true),
    SidrTabSpec(SidrTab.ACTIVITY, "Activity", Icons.Filled.Notifications, isPreview = true),
    SidrTabSpec(SidrTab.TERMINAL, "Terminal", Icons.Filled.Build, isPreview = true),
)

/**
 * The 5-tab app-shell bottom bar: Home + 4 preview tabs. [selected] highlights the current tab
 * root; [onSelect] fires on tap (the caller drives the actual navigation).
 */
@Composable
fun SidrTabBar(
    selected: SidrTab,
    onSelect: (SidrTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SidrTheme.colors
    NavigationBar(
        modifier = modifier,
        containerColor = colors.surface,
        contentColor = colors.dim,
    ) {
        SIDR_TAB_SPECS.forEach { spec ->
            val label = if (spec.isPreview) "${spec.label} ◦" else spec.label
            NavigationBarItem(
                selected = selected == spec.tab,
                onClick = { onSelect(spec.tab) },
                icon = { Icon(imageVector = spec.icon, contentDescription = null) },
                label = { Text(text = label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.ground,
                    selectedTextColor = colors.ground,
                    indicatorColor = colors.text,
                    unselectedIconColor = colors.dim,
                    unselectedTextColor = colors.dim,
                ),
            )
        }
    }
}
