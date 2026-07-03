package com.sidr.launcher.feature.launcher

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.ui.component.EmptyState
import com.sidr.launcher.core.ui.component.ErrorState
import com.sidr.launcher.core.ui.component.SectionHeader
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSearchField
import com.sidr.launcher.core.ui.component.TopBarIcon
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.model.InstalledApp

/**
 * App Drawer (Block X3): the full installed-app list, alphabetical with sticky lettered section
 * headers. Reached from the home "All apps" affordance. Tapping an app launches it (feeding
 * Favorites / Suggestions on home); Back returns to home via the ViewModel's [NavigationEvent].
 */
@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppDrawerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    SidrScaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarIcon(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Text(
                    text = "All apps",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
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
            SidrSearchField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                onSubmit = { viewModel.onQuerySubmitted() },
                showMic = false,
                placeholder = "Search apps",
            )

            // "Ask assistant" affordance (Block X6-C): when the user has typed something, offer to
            // send it to the assistant with the text prefilled (never auto-sent). The route carries
            // the URL-encoded query; the assistant consumes it once and never persists it.
            if (query.isNotBlank()) {
                TextButton(
                    onClick = {
                        viewModel.navigateTo(Routes.Assistant.routeFor(Uri.encode(query)))
                    },
                    modifier = Modifier
                        .align(Alignment.Start)
                        .padding(horizontal = Spacing.sm),
                ) {
                    Text(text = "Ask assistant: \"$query\"")
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val state = uiState) {
                    is UiState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                    is UiState.Empty -> EmptyState(
                        message = if (query.isBlank()) "No apps found." else "Nothing found.",
                    )
                    is UiState.Error -> ErrorState(
                        message = state.error.toDisplayMessage(),
                        onRetry = if (state.retryable) viewModel::retry else null,
                    )
                    is UiState.Success -> DrawerList(
                        sections = state.data.sections,
                        onAppClick = viewModel::onAppClicked,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerList(
    sections: List<DrawerSection>,
    onAppClick: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        sections.forEach { section ->
            stickyHeader(key = "header_${section.letter}") {
                SectionHeader(
                    text = section.letter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
            items(section.apps, key = { it.packageName }) { app ->
                DrawerAppRow(app = app, onClick = { onAppClick(app) })
            }
        }
    }
}

/**
 * Compact drawer row: launcher icon + label, one tappable target. A list row (not a grid tile)
 * scans better for an alphabetical A–Z list. Reuses the module-shared [rememberAppIcon].
 */
@Composable
private fun DrawerAppRow(
    app: InstalledApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = app.label, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = app.label }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DrawerAppIcon(app)
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
