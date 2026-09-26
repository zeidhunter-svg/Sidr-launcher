package com.sidr.launcher.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sidr.launcher.R as AppR
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
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
    @StringRes val labelRes: Int,
    /** Preview (not-yet-live) tabs carry a tiny marker in their label (spec: status/preview
     *  is never colour-only) — see [com.sidr.launcher.core.ui.component.SidrPreviewBadge]. A full
     *  badge does not fit the compact tab label slot, so a [PREVIEW_MARKER] stands in. */
    val isPreview: Boolean,
)

/**
 * Marker appended to a preview tab's label. **`•` (U+2022), deliberately not `◦` (U+25E6)**: the tab
 * label now renders in the bundled sans, and IBM Plex Sans has no U+25E6 glyph (verified against the
 * shipped TTF's cmap, as do Nunito Sans and the platform sans). Android would paper over that with
 * font fallback, drawing the marker in some other typeface at some other weight — and on a thinned
 * OEM font set it could tofu outright. Since "a preview tab is visibly a preview" is a release-gate
 * requirement, the marker must be a glyph the bundled face actually owns. U+2022 is present in every
 * candidate face *and* in JetBrains Mono, so it survives the DS-11 B typography swap either way.
 */
private const val PREVIEW_MARKER = "•"

// DS-11 A2 (2026-08-10): labels are lowercase and icon-free. The icons were dropped on owner
// direction ("ничего лишнего") — a launcher's five tab roots are learned by position and word, and
// the glyph row was the loudest thing on an otherwise calm Home.
private val SIDR_TAB_SPECS = listOf(
    SidrTabSpec(SidrTab.HOME, AppR.string.app_tab_home, isPreview = false),
    // "Apps" (2026-07-12): the App Drawer, promoted from a Home-body row into a real tab peer of
    // Home — real, functional, not a preview.
    SidrTabSpec(SidrTab.APPS, AppR.string.app_tab_apps, isPreview = false),
    SidrTabSpec(SidrTab.TASKS, AppR.string.app_tab_tasks, isPreview = true),
    SidrTabSpec(SidrTab.AGENTS, AppR.string.app_tab_agents, isPreview = true),
    SidrTabSpec(SidrTab.ACTIVITY, AppR.string.app_tab_activity, isPreview = true),
    // Terminal (2026-07-12) is no longer a tab — it moved to an icon-only button in
    // [SidrAppFooter], next to the Settings gear, so it doesn't crowd the 5-tab label row.
)

/**
 * The 5-tab app-shell bottom bar: Home + Apps + 3 preview tabs. [selected] highlights the current
 * tab root; [onSelect] fires on tap (the caller drives the actual navigation).
 *
 * DS-11 A2 replaced Material's `NavigationBar`/`NavigationBarItem` with this plain themed row.
 * `NavigationBarItem` requires a non-null `icon` and sizes/positions its selection indicator around
 * that glyph, so an icon-free variant is not expressible through it — and dropping raw Material here
 * also puts the last piece of app-shell chrome on the DS press-invert model (spec §5.3), the same
 * `fg = ground / bg = text` inversion [com.sidr.launcher.core.ui.component.SidrRouteChip] uses.
 *
 * Insets: Material's `NavigationBar` applied its own window-inset padding; this row must do the same
 * ([navigationBarsPadding]) or it renders underneath the system navigation bar.
 */
@Composable
fun SidrTabBar(
    selected: SidrTab,
    onSelect: (SidrTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SidrTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SIDR_TAB_SPECS.forEach { spec ->
            SidrTabLabel(
                label = sidrString(spec.labelRes),
                isPreview = spec.isPreview,
                selected = selected == spec.tab,
                onClick = { onSelect(spec.tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One tab label. Press-invert selection (spec §5.3): the active tab flips to the light text token
 * with ground-coloured text; the rest sit dim. No glow, no scale, no layout shift — the inverted
 * fill is the whole affordance.
 *
 * The [PREVIEW_MARKER] is appended to the visible label AND spelled out as ", preview" via
 * `contentDescription`, so a TalkBack user learns a tab is a preview the same way a sighted one does
 * (the marker must never be the visual-only signal — release gate: "no fake decorative
 * agent/activity state"). `mergeDescendants = true` is required for that: without it the explicit
 * description and the child `SidrText` are two separate semantics nodes and the label is announced
 * twice. Merged, the description replaces the child text and TalkBack reads one phrase —
 * "tasks, preview, selected, tab".
 */
@Composable
private fun SidrTabLabel(
    label: String,
    isPreview: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SidrTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = selected || pressed
    val shape = SidrShapes.small
    val previewContentDescription = sidrString(AppR.string.app_tab_preview_content_description, label)
    Box(
        modifier = modifier
            .heightIn(min = Sizes.minTouchTarget)
            .clip(shape)
            .background(if (active) colors.text else Color.Transparent)
            .semantics(mergeDescendants = true) {
                role = Role.Tab
                this.selected = selected
                contentDescription = if (isPreview) previewContentDescription else label
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SidrText(
            text = if (isPreview) "$label $PREVIEW_MARKER" else label,
            role = SidrTextRole.SYSTEM,
            color = if (active) colors.ground else colors.dim,
            maxLines = 1,
        )
    }
}

/**
 * The app-wide footer ABOVE the tab bar (moved here from Home 2026-07-12, then reordered above
 * [SidrTabBar] the same day per owner direction — it used to be Home-only content above the retired
 * three-row bottom nav; now it is shared chrome, and the 5-tab row is the true bottom-most strip):
 * a Terminal icon-only button (2026-07-12 — Terminal was demoted from a full tab to an icon here,
 * since a 6th labelled tab crowded the row), a Settings gear, and the `SIDR OS` brand wordmark on the
 * right in provenance (mono) style. The hidden dev-mode arm (7 rapid taps on the wordmark within 3s →
 * [onArmDevMode]) is a Home-only concept (it arms `LauncherViewModel`'s console overlay) — tabs other
 * than Home pass no callback, so the tap is a harmless no-op there.
 *
 * DS-11 A3 (2026-08-10) removed the leading `Local-first · on-device` note on owner direction: it
 * restated on every tab root what the product already is, and the privacy claims that actually carry
 * information are the per-surface provenance lines (assistant cloud disclosure, prayer provenance,
 * memory "local only"). A [Spacer] took over its `weight(1f)` so the wordmark stays right-aligned.
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
        Spacer(modifier = Modifier.weight(1f))
        SidrIconButton(
            // A real terminal glyph (DS-11 A3): this used to be `Icons.Filled.Build`, a wrench, which
            // read as "tools/settings", not "terminal". `Icons.Filled.Terminal` lives in
            // material-icons-extended — several MB of dependency for one glyph — so this follows the
            // module's existing bundled-vector precedent (ic_mic_24 / ic_assistant_24).
            painter = painterResource(R.drawable.ic_terminal_24),
            contentDescription = sidrString(AppR.string.app_footer_terminal_content_description),
            onClick = onTerminal,
        )
        SidrIconButton(
            icon = Icons.Filled.Settings,
            contentDescription = sidrString(AppR.string.app_footer_settings_content_description),
            onClick = onSettings,
        )
        SidrText(
            text = sidrString(AppR.string.app_wordmark_sidr_os),
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
    // Resolved here, not inside the `.semantics { }` lambda below: that lambda is not a
    // @Composable scope, so sidrString(...) cannot be called from within it (Task 15).
    val revealLabel = sidrString(AppR.string.app_chrome_handle_content_description)
    // Sized by padding (wrap-content), not a fixed height: the caller anchors this as a bottom
    // overlay and applies system-nav clearance, while this composable owns the tap target itself.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = revealLabel }
            .clickable(onClickLabel = revealLabel, onClick = onReveal)
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
