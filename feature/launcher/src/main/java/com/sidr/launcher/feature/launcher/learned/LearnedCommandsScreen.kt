package com.sidr.launcher.feature.launcher.learned

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.ui.component.EmptyState
import com.sidr.launcher.core.ui.component.ErrorState
import com.sidr.launcher.core.ui.component.SidrForgetGate
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrMemoryItem
import com.sidr.launcher.core.ui.component.SidrMemoryStatus
import com.sidr.launcher.core.ui.component.SidrMemoryType
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrProgress
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.feature.launcher.R
import com.sidr.launcher.feature.launcher.agent.line

/**
 * «Выученные команды» (A4′ phase 4, B-11, `DOC-AML-5`): every learned command, its steps as the session card
 * words them, its streak — and «Забыть». Composed by `:app`'s `NavHost` from Settings' one row.
 */
@Composable
fun LearnedCommandsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LearnedCommandsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LearnedCommandsContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onForget = viewModel::onForget,
        modifier = modifier,
    )
}

@Composable
internal fun LearnedCommandsContent(
    uiState: LearnedCommandsUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onForget: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = sidrString(R.string.launcher_learned_commands_title)
    // «Забыть» asks first (owner «T8: a», 2026-11-20), as `LearnedChoicesScreen` does: the row's tap only names the
    // form; the store is touched by the gate's own confirm.
    var pendingForget by remember { mutableStateOf<String?>(null) }
    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = title,
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = sidrString(R.string.launcher_drawer_back),
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
            SidrSectionHeader(text = title)
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    SidrProgress(modifier = Modifier.fillMaxWidth(0.5f))
                }
                uiState.failed -> ErrorState(
                    message = sidrString(R.string.launcher_learned_commands_failed),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
                uiState.commands.isEmpty() -> EmptyState(
                    message = sidrString(R.string.launcher_learned_commands_empty),
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(uiState.commands, key = { it.form }) { command ->
                        LearnedCommandItem(command = command, onForget = { pendingForget = command.form })
                    }
                }
            }

            pendingForget?.let { form ->
                SidrForgetGate(
                    title = sidrString(R.string.launcher_forget_learned_command_title),
                    consequence = sidrString(R.string.launcher_forget_learned_command_consequence, form),
                    onCancel = { pendingForget = null },
                    onForget = {
                        onForget(form)
                        pendingForget = null
                    },
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun LearnedCommandItem(command: LearnedCommandUiModel, onForget: () -> Unit) {
    // The card's own wording: nothing has run, so a reference reads its source step (as the interpretation card).
    val lines = command.steps.map { step -> step.line(command.form, emptyMap(), observations = emptyMap()) }
    SidrMemoryItem(
        title = command.form,
        value = lines.joinToString("\n"),
        type = SidrMemoryType.LearnedPreference,
        status = if (command.pinned) SidrMemoryStatus.Active else SidrMemoryStatus.Learning,
        evidence = if (command.pinned) {
            sidrString(R.string.launcher_learned_commands_learned)
        } else {
            sidrString(R.string.launcher_learned_commands_learning, command.streak, command.threshold)
        },
        onForget = onForget,
    )
}
