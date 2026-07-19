package com.sidr.launcher.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

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
enum class SidrTab { HOME, APPS, TASKS, AGENTS, ACTIVITY }

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
    // "Apps" (2026-07-12): the App Drawer, promoted from a Home-body row into a real tab peer of
    // Home — real, functional, not a preview.
    SidrTabSpec(SidrTab.APPS, "Apps", Icons.AutoMirrored.Filled.List, isPreview = false),
    SidrTabSpec(SidrTab.TASKS, "Tasks", Icons.Filled.CheckCircle, isPreview = true),
    SidrTabSpec(SidrTab.AGENTS, "Agents", Icons.Filled.Person, isPreview = true),
    SidrTabSpec(SidrTab.ACTIVITY, "Activity", Icons.Filled.Notifications, isPreview = true),
    // Terminal (2026-07-12) is no longer a tab — it moved to an icon-only button in
    // [SidrAppFooter], next to the Settings gear, so it doesn't crowd the 5-tab label row.
)

/**
 * The 5-tab app-shell bottom bar: Home + Apps + 3 preview tabs. [selected] highlights the current
 * tab root; [onSelect] fires on tap (the caller drives the actual navigation).
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

/**
 * The app-wide footer ABOVE the tab bar (moved here from Home 2026-07-12, then reordered above
 * [SidrTabBar] the same day per owner direction — it used to be Home-only content above the retired
 * three-row bottom nav; now it is shared chrome, and the 5-tab row is the true bottom-most strip):
 * the local-first privacy note on the left, a Terminal icon-only button (2026-07-12 — Terminal was
 * demoted from a full tab to an icon here, since a 6th labelled tab crowded the row), a Settings
 * gear, and the `SIDR OS` brand wordmark on the right in provenance (mono) style. The hidden dev-mode
 * arm (7 rapid taps on the wordmark within 3s → [onArmDevMode]) is a Home-only concept (it arms
 * `LauncherViewModel`'s console overlay) — tabs other than Home pass no callback, so the tap is a
 * harmless no-op there. Honest: the launcher core resolves commands on-device; the assistant and
 * smart routing are separate opt-in surfaces.
 */
@Composable
fun SidrAppFooter(
    onSettings: () -> Unit,
    onTerminal: () -> Unit,
    modifier: Modifier = Modifier,
    onArmDevMode: () -> Unit = {},
) {
    var tapCount by remember { mutableStateOf(0) }
    var lastTap by remember { mutableStateOf(0L) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SidrText(
            text = "Local-first · on-device",
            role = SidrTextRole.PROVENANCE,
            modifier = Modifier.weight(1f),
        )
        SidrIconButton(
            icon = Icons.Filled.Build,
            contentDescription = "Terminal preview",
            onClick = onTerminal,
        )
        SidrIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = "Settings",
            onClick = onSettings,
        )
        SidrText(
            text = "SIDR OS",
            role = SidrTextRole.PROVENANCE,
            color = SidrTheme.colors.dim,
            modifier = Modifier.clickable {
                val now = System.currentTimeMillis()
                tapCount = if (now - lastTap < 3000L) tapCount + 1 else 1
                lastTap = now
                if (tapCount >= 7) {
                    tapCount = 0
                    onArmDevMode()
                }
            },
        )
    }
}

/**
 * The resting affordance shown in place of the bottom chrome when it has auto-hidden (2026-07-12
 * calm/idle-hide feature): a thin, low-contrast drawer-style pill inside a full-width, 40 dp tappable
 * strip. Tapping it summons the tab bar (+ footer on Home) back — see [com.sidr.launcher.navigation]
 * `TabRootScaffold`, which renders this as a bottom-anchored overlay on the content (with its own
 * `navigationBarsPadding`) so its size/position are predictable, rather than fighting the Scaffold's
 * `bottomBar` measurement. Reveal is a **tap**, not a bottom-edge swipe, deliberately: a bottom-edge
 * upward swipe collides with Android's system gesture-nav home gesture.
 */
@Composable
fun SidrChromeHandle(
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sized by padding (wrap-content), not a fixed height: the caller anchors this as a bottom
    // overlay and applies system-nav clearance, while this composable owns the tap target itself.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Show navigation bar" }
            .clickable(onClickLabel = "Show navigation bar", onClick = onReveal)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(SidrTheme.colors.dim),
        )
    }
}
