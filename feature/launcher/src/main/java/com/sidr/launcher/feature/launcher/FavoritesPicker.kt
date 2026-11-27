package com.sidr.launcher.feature.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sidr.launcher.core.ui.component.SidrTertiaryButton
import com.sidr.launcher.core.ui.component.SidrToggleRow
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.model.InstalledApp

/** The picker's test tag (A4′ acceptance tail, task 12): `LauncherScreenFavoritesTest` looks the window up by it. */
internal const val FAVORITES_PICKER_TEST_TAG = "favorites_picker"

/**
 * The favourites window, opened by tapping Home's «Избранное» header (A4′ acceptance tail, task 12; owner ruling 15).
 * Every installed app is a [SidrToggleRow] — on means pinned — the pinned ones standing first in the order they were
 * pinned, the rest in the list's own order. An app Home placed by use (in [favorites], not in [pinned]) carries the
 * «Убрать с главного» action, which takes it out of the automatic places. Pure presentation: the three callbacks go to
 * the ViewModel, which writes the preferences; the rows read what comes back.
 */
@Composable
internal fun FavoritesPicker(
    apps: List<InstalledApp>,
    pinned: List<String>,
    favorites: List<InstalledApp>,
    onPin: (String) -> Unit,
    onUnpin: (String) -> Unit,
    onHide: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val byPackage = apps.associateBy { it.packageName }
    val pinnedApps = pinned.mapNotNull { byPackage[it] }
    val pinnedSet = pinnedApps.mapTo(HashSet()) { it.packageName }
    val rows = pinnedApps + apps.filter { it.packageName !in pinnedSet }
    val automatic = favorites.mapTo(HashSet()) { it.packageName } - pinnedSet

    Dialog(onDismissRequest = onDismiss) {
        SidrSurface(
            tone = SidrSurfaceTone.RAISED,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(FAVORITES_PICKER_TEST_TAG),
        ) {
            Column(modifier = Modifier.padding(vertical = Spacing.md)) {
                SidrText(
                    text = sidrString(R.string.launcher_favorites_edit_title),
                    role = SidrTextRole.HUMAN_TITLE,
                    modifier = Modifier
                        .padding(horizontal = Spacing.lg)
                        .semantics { heading() },
                )
                SidrText(
                    text = sidrString(R.string.launcher_favorites_edit_hint),
                    role = SidrTextRole.CAPTION,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(rows, key = { it.packageName }) { app ->
                        val isPinned = app.packageName in pinnedSet
                        Column {
                            SidrToggleRow(
                                title = app.label,
                                checked = isPinned,
                                onCheckedChange = { on ->
                                    if (on) onPin(app.packageName) else onUnpin(app.packageName)
                                },
                                description = if (isPinned) null else sidrString(R.string.launcher_favorites_pin),
                            )
                            if (app.packageName in automatic) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.lg),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SidrTertiaryButton(
                                        text = sidrString(R.string.launcher_favorites_hide),
                                        onClick = { onHide(app.packageName) },
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.End,
                ) {
                    SidrTertiaryButton(
                        text = sidrString(R.string.launcher_favorites_edit_done),
                        onClick = onDismiss,
                    )
                }
            }
        }
    }
}
