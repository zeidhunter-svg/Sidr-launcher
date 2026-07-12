package com.sidr.launcher.feature.launcher

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.ui.component.AppTile
import com.sidr.launcher.core.ui.component.EmptyState
import com.sidr.launcher.core.ui.component.ErrorState
import com.sidr.launcher.core.ui.component.SidrAlphabetHeader
import com.sidr.launcher.core.ui.component.SidrFilterChip
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrPreviewBadge
import com.sidr.launcher.core.ui.component.SidrPreviewBanner
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrTerminalAction
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.primitive.SidrProgress
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.model.InstalledApp

/**
 * App Drawer (Block X3, restyled to the Vision-MVP artifact look — Task 2; promoted to the "Apps"
 * bottom tab 2026-07-12): the full installed-app list, rendered as a 4-column icon grid with sticky
 * alphabet headers by default. Tapping an app launches it (feeding Favorites / Suggestions on home).
 *
 * [onBack] is optional: it renders a back arrow only when this screen is reached as a *pushed*
 * destination (none currently do — it's kept for a future non-tab entry point). As a tab root
 * (the "Apps" tab) it is `null`, matching every other tab root (Home/Tasks/Agents/Activity/Terminal),
 * none of which have a back arrow — the tab bar itself is how you leave.
 *
 * A screen-local `Groups`/`A-Z` toggle lets the user preview a future grouped-by-category layout.
 * `A-Z` is the real, VM-backed alphabetical grid ([groupIntoSections], unit-tested); `Groups` is a
 * clearly-labelled *design preview* ([SidrPreviewBanner]/[SidrPreviewBadge]) built from a local sample
 * category assignment over the same installed apps — it is NOT a real categorisation engine, and none
 * of this screen-local UI state reaches the ViewModel.
 */
@Composable
fun AppDrawerScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: AppDrawerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(DrawerMode.AZ) }

    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = "All apps",
                navigationIcon = onBack?.let { back ->
                    {
                        SidrIconButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            onClick = back,
                        )
                    }
                },
                actions = {
                    GroupsAzToggle(mode = mode, onModeChange = { mode = it })
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            // Live app filter (Block X4). Voice stays on home, so no mic here; Clear is built in.
            // Submit launches the top match rather than dispatching a command (that lives on home).
            DrawerSearchField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                onSubmit = { viewModel.onQuerySubmitted() },
            )

            // "Ask assistant" affordance (Block X6-C): when the user has typed something, offer to
            // send it to the assistant with the text prefilled (never auto-sent). The route carries
            // the URL-encoded query; the assistant consumes it once and never persists it.
            if (query.isNotBlank()) {
                SidrTerminalAction(
                    text = "Ask assistant: \"$query\"",
                    onClick = {
                        viewModel.navigateTo(Routes.Assistant.routeFor(Uri.encode(query)))
                    },
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = Spacing.lg),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is UiState.Loading -> SidrProgress(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.5f),
                    )
                    is UiState.Empty -> EmptyState(
                        message = if (query.isBlank()) "No apps found." else "Nothing found.",
                    )
                    is UiState.Error -> ErrorState(
                        message = state.error.toDisplayMessage(),
                        onRetry = if (state.retryable) viewModel::retry else null,
                    )
                    is UiState.Success -> when (mode) {
                        DrawerMode.AZ -> DrawerGrid(
                            sections = state.data.sections,
                            onAppClick = viewModel::onAppClicked,
                        )
                        DrawerMode.GROUPS -> GroupsPreviewGrid(
                            apps = state.data.sections.flatMap { it.apps },
                            onAppClick = viewModel::onAppClicked,
                        )
                    }
                }
            }
        }
    }
}

/** Screen-local view mode for the drawer body. Never touches the ViewModel. */
private enum class DrawerMode { AZ, GROUPS }

/**
 * `Groups` / `A-Z` toggle (Step 3): two press-invert filter chips. `Groups` carries a
 * [SidrPreviewBadge] so the not-yet-real grouped view is never mistaken for a shipped feature.
 */
@Composable
private fun GroupsAzToggle(
    mode: DrawerMode,
    onModeChange: (DrawerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SidrFilterChip(
            label = "Groups",
            selected = mode == DrawerMode.GROUPS,
            onClick = { onModeChange(DrawerMode.GROUPS) },
        )
        SidrPreviewBadge()
        SidrFilterChip(
            label = "A-Z",
            selected = mode == DrawerMode.AZ,
            onClick = { onModeChange(DrawerMode.AZ) },
        )
    }
}

/**
 * DS search row (Step 1): a [SidrSurface] wrapping a mono [BasicTextField], styled like
 * [com.sidr.launcher.core.ui.component.SidrUniversalInput]'s field but without its mic/route-chip
 * concerns (the drawer search has no mic and no route chips — just text + clear).
 */
@Composable
private fun DrawerSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SidrTheme.colors
    SidrSurface(
        tone = SidrSurfaceTone.SURFACE,
        shape = SidrShapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    SidrText(
                        text = "Search applications",
                        role = SidrTextRole.HUMAN_BODY,
                        color = colors.faint,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        SidrTheme.textStyles.command.copy(color = colors.text),
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Clear affordance — 48dp target, same contract as SidrUniversalInput's.
            if (value.isNotEmpty()) {
                SidrIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Clear search",
                    onClick = { onValueChange("") },
                    tint = colors.dim,
                )
            }
        }
    }
}

/**
 * The real, VM-backed alphabetical grid (Step 2): a [LazyColumn] with a sticky [SidrAlphabetHeader]
 * per [DrawerSection], each section's apps chunked into 4-wide rows of [AppTile]s.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerGrid(
    sections: List<DrawerSection>,
    onAppClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        sections.forEach { section ->
            stickyHeader(key = "header_${section.letter}") {
                SidrAlphabetHeader(
                    text = section.letter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
            section.apps.chunked(APPS_PER_ROW).forEachIndexed { rowIndex, rowApps ->
                item(key = "row_${section.letter}_$rowIndex") {
                    DrawerGridRow(apps = rowApps, onAppClick = onAppClick)
                }
            }
        }
    }
}

/**
 * **Preview only** (Step 3): a grouped-by-category grid built from a fixed local sample-category
 * list, with the real installed apps distributed round-robin across it — index-based, no content
 * signal read from the app at all. This is deliberately not a categorisation engine; the banner +
 * badge (in [GroupsAzToggle]) make the preview status explicit to the user at all times.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupsPreviewGrid(
    apps: List<InstalledApp>,
    onAppClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buckets = remember(apps) {
        val byCategory = LinkedHashMap<String, MutableList<InstalledApp>>()
        SAMPLE_CATEGORIES.forEach { byCategory[it] = mutableListOf() }
        apps.forEachIndexed { index, app ->
            byCategory.getValue(SAMPLE_CATEGORIES[index % SAMPLE_CATEGORIES.size]).add(app)
        }
        byCategory.filterValues { it.isNotEmpty() }
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item(key = "groups_preview_banner") {
            SidrPreviewBanner()
        }
        buckets.forEach { (category, categoryApps) ->
            stickyHeader(key = "group_header_$category") {
                SidrAlphabetHeader(
                    text = category.uppercase(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
            categoryApps.chunked(APPS_PER_ROW).forEachIndexed { rowIndex, rowApps ->
                item(key = "group_row_${category}_$rowIndex") {
                    DrawerGridRow(apps = rowApps, onAppClick = onAppClick)
                }
            }
        }
        item(key = "groups_preview_provenance") {
            SidrText(
                text = "ON-DEVICE · OFFLINE",
                role = SidrTextRole.PROVENANCE,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg),
            )
        }
    }
}

/** Fixed, clearly-illustrative sample category labels for the Groups preview — not real categories. */
private val SAMPLE_CATEGORIES = listOf("Finance", "Messaging", "Media", "Productivity", "Other")

/** Apps per grid row, shared by the real A-Z grid and the Groups preview grid. */
private const val APPS_PER_ROW = 4

/**
 * One 4-wide row of [AppTile]s; short rows pad their remaining cells with equal-weight spacers so
 * icons stay left-aligned under a consistent column grid instead of centering as a group.
 */
@Composable
private fun DrawerGridRow(
    apps: List<InstalledApp>,
    onAppClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        apps.forEach { app ->
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                AppTile(
                    label = app.label,
                    onClick = { onAppClick(app) },
                    icon = { DrawerAppIcon(app) },
                )
            }
        }
        repeat(APPS_PER_ROW - apps.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DrawerAppIcon(app: InstalledApp) {
    val icon by rememberAppIcon(app.packageName)
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = Modifier.size(Sizes.appIcon),
        )
    } else {
        Box(
            modifier = Modifier
                .size(Sizes.appIcon)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
    }
}

private fun UiError.toDisplayMessage(): String = when (this) {
    is UiError.Message -> text
    UiError.Network -> "Couldn't load your apps — check your connection."
    UiError.Unknown -> "Couldn't load your apps."
}
