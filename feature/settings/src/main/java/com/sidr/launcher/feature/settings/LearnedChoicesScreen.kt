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
import androidx.compose.runtime.setValue
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
import com.sidr.launcher.core.ui.component.SidrForgetGate
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrMemoryItem
import com.sidr.launcher.core.ui.component.SidrMemoryType
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrProgress
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
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
        onDelete = viewModel::onDelete,
        modifier = modifier,
    )
}

@Composable
private fun LearnedChoicesContent(
    uiState: LearnedChoicesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingForget by remember { mutableStateOf<LearnedChoiceMemoryUiModel?>(null) }

    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = sidrString(R.string.settings_memory_top_bar_title),
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = sidrString(R.string.settings_back),
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
            SidrSectionHeader(text = sidrString(R.string.settings_learned_choices_header))
            when {
                uiState.isLoading -> LoadingState(modifier = Modifier.weight(1f))
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage,
                    onRetry = if (uiState.canRetry) onRetry else null,
                    modifier = Modifier.weight(1f),
                )
                uiState.choices.isEmpty() -> EmptyState(
                    message = sidrString(R.string.settings_learned_choices_empty_message),
                    modifier = Modifier.weight(1f),
                )
                else -> LearnedChoicesList(
                    choices = uiState.choices,
                    onForget = { pendingForget = it },
                    modifier = Modifier.weight(1f),
                )
            }

            pendingForget?.let { choice ->
                SidrForgetGate(
                    title = sidrString(R.string.settings_forget_learned_choice_title),
                    consequence = sidrString(
                        R.string.settings_forget_learned_choice_consequence,
                        choice.phrase,
                        choice.targetLabel,
                    ),
                    evidence = sidrString(R.string.settings_stored_locally),
                    onCancel = { pendingForget = null },
                    onForget = {
                        onDelete(choice.stableId)
                        pendingForget = null
                    },
                    modifier = Modifier.padding(Spacing.md),
                )
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
    choices: List<LearnedChoiceMemoryUiModel>,
    onForget: (LearnedChoiceMemoryUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(choices, key = { it.stableId }) { choice ->
            LearnedChoiceItem(
                choice = choice,
                onForget = { onForget(choice) },
            )
        }
    }
}

@Composable
private fun LearnedChoiceItem(
    choice: LearnedChoiceMemoryUiModel,
    onForget: () -> Unit,
) {
    SidrMemoryItem(
        title = choice.phrase,
        value = choice.targetLabel,
        type = SidrMemoryType.LearnedPreference,
        status = choice.status,
        evidence = evidenceText(choice.evidence),
        provenance = provenanceText(choice.provenance),
        lastUsed = choice.lastUsed,
        localOnly = choice.localOnly,
        leadingContent = {
            LearnedChoiceIcon(
                packageName = choice.targetPackageName,
                label = choice.targetLabel,
            )
        },
        onForget = onForget,
    )
}

@Composable
private fun evidenceText(evidence: LearnedChoiceEvidence): String = when (evidence) {
    LearnedChoiceEvidence.Unavailable -> sidrString(R.string.settings_learned_evidence_unavailable)
    is LearnedChoiceEvidence.Learning ->
        sidrString(R.string.settings_learned_evidence_learning, evidence.streak, evidence.threshold)
    LearnedChoiceEvidence.NeedsReconfirm -> sidrString(R.string.settings_learned_evidence_reconfirm)
    LearnedChoiceEvidence.Confirmed -> sidrString(R.string.settings_learned_evidence_confirmed)
}

@Composable
private fun provenanceText(provenance: LearnedChoiceProvenance): String = when (provenance) {
    LearnedChoiceProvenance.ConfirmedChoices -> sidrString(R.string.settings_learned_provenance)
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
