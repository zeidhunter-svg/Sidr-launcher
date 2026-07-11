package com.sidr.launcher.feature.settings

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.ui.component.EmptyState
import com.sidr.launcher.core.ui.component.ErrorState
import com.sidr.launcher.core.ui.component.SidrDestructiveButton
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrPreviewBadge
import com.sidr.launcher.core.ui.component.SidrPreviewBanner
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSecondaryButton
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrTerminalAction
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.primitive.SidrProgress
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceDisplayState
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LearnedChoicesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LearnedChoicesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LearnedChoicesContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onDelete = { choice -> viewModel.onDelete(choice.capabilityKey) },
        modifier = modifier,
    )
}

@Composable
private fun LearnedChoicesContent(
    uiState: LearnedChoicesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDelete: (LearnedChoiceView) -> Unit,
    modifier: Modifier = Modifier,
) {
    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = "Memory",
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                    )
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            SidrSectionHeader(text = "LEARNED CHOICES")
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.weight(1f))
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage,
                    onRetry = if (uiState.canRetry) onRetry else null,
                    modifier = Modifier.weight(1f),
                )
                uiState.choices.isEmpty() -> EmptyState(
                    message = "No learned choices yet",
                    modifier = Modifier.weight(1f),
                )
                else -> LearnedChoicesList(
                    choices = uiState.choices,
                    onDelete = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }
            // Preview-only surface (Vision MVP task 5). Not backed by any real bulk-delete/export
            // use case (none exists in domain/memory/resolution) — everything below stays inert
            // and badged so it never reads as a shipped feature.
            if (!uiState.isLoading && uiState.errorMessage == null) {
                MemoryPreviewSection()
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        SidrProgress(modifier = Modifier.fillMaxWidth(0.5f))
    }
}

@Composable
private fun LearnedChoicesList(
    choices: List<LearnedChoiceView>,
    onDelete: (LearnedChoiceView) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(choices, key = { "${it.capabilityKey.actionId.value}:${it.capabilityKey.query}" }) { choice ->
            LearnedChoiceItem(
                choice = choice,
                onDelete = { onDelete(choice) },
            )
        }
    }
}

@Composable
private fun LearnedChoiceItem(
    choice: LearnedChoiceView,
    onDelete: () -> Unit,
) {
    SidrSurface(
        tone = SidrSurfaceTone.SURFACE,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.sm),
        ) {
            LearnedChoiceIcon(
                packageName = choice.targetPackageName,
                label = choice.targetLabel,
            )
            Column(modifier = Modifier.weight(1f)) {
                SidrText(
                    text = "\"${choice.capabilityKey.query}\" → ${choice.targetLabel}",
                    role = SidrTextRole.HUMAN_BODY,
                )
                SidrText(
                    text = displayStateLabel(choice.displayState),
                    role = SidrTextRole.PROVENANCE,
                )
            }
            SidrTerminalAction(
                text = "Forget",
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun LearnedChoiceIcon(packageName: String, label: String) {
    val icon by rememberAppIcon(packageName)
    if (icon != null) {
        Image(
            bitmap = icon!!,
            contentDescription = null,
            modifier = Modifier.size(Sizes.icon),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Sizes.icon)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Text(
                text = label.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun rememberAppIcon(packageName: String): State<ImageBitmap?> {
    val pm = LocalContext.current.packageManager
    val state = remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        val icon = withContext(Dispatchers.IO) {
            try {
                pm.getApplicationIcon(packageName).toImageBitmap()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
        state.value = icon
    }
    return state
}

private fun Drawable.toImageBitmap(): ImageBitmap {
    val bmp = Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp.asImageBitmap()
}

private fun displayStateLabel(state: LearnedChoiceDisplayState): String = when (state) {
    LearnedChoiceDisplayState.Unavailable -> "unavailable"
    is LearnedChoiceDisplayState.Learning -> "learning ${state.streak}/${state.threshold}"
    LearnedChoiceDisplayState.NeedsReconfirm -> "needs reconfirm"
    LearnedChoiceDisplayState.Auto -> "auto"
    LearnedChoiceDisplayState.AutoReady -> "auto-ready"
}

/**
 * Preview-only "Memory" surface (Vision MVP task 5 / A3-S2-2/DS-7). Aliases/Facts/Dismissed are fixed
 * sample rows — not read from any real store, because no such store exists yet. Export/Delete all are
 * deliberately inert (`onClick = {}`): there is no bulk-delete or export use case in the domain layer,
 * and wiring "Delete all" to a loop of single deletes would fabricate an unreviewed feature.
 */
@Composable
private fun MemoryPreviewSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SidrPreviewBanner()

        PreviewSectionHeader(title = "ALIASES")
        PreviewSampleRow(phrase = "open docs", target = "GitHub Docs")
        PreviewSampleRow(phrase = "the group chat", target = "Telegram")

        PreviewSectionHeader(title = "FACTS")
        PreviewSampleRow(phrase = "my timezone", target = "Africa/Cairo")

        PreviewSectionHeader(title = "DISMISSED")
        PreviewSampleRow(phrase = "weather widget suggestion", target = "dismissed")

        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
        ) {
            SidrSecondaryButton(
                text = "Export",
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            SidrDestructiveButton(
                text = "Delete all",
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PreviewSectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = modifier.fillMaxWidth(),
    ) {
        SidrSectionHeader(text = title, modifier = Modifier.weight(1f))
        SidrPreviewBadge(modifier = Modifier.padding(end = Spacing.lg))
    }
}

@Composable
private fun PreviewSampleRow(phrase: String, target: String, modifier: Modifier = Modifier) {
    SidrSurface(
        tone = SidrSurfaceTone.SURFACE,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            SidrText(
                text = "\"$phrase\" → $target",
                role = SidrTextRole.HUMAN_BODY,
            )
            SidrText(
                text = "sample · not real",
                role = SidrTextRole.PROVENANCE,
            )
        }
    }
}
