package com.sidr.launcher.feature.assistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.common.UiError

/**
 * Assistant screen — pure render, no business logic.
 *
 * If no provider is configured ([AssistantUiState.form.baseUrl] blank) the provider-settings form
 * is surfaced prominently (first-run). Otherwise the streaming chat UI is shown with an expandable
 * edit-provider section.
 */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isConfigured = uiState.form.baseUrl.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            IconButton(onClick = viewModel::navigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = "Assistant",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (!isConfigured) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Set up a provider to start chatting",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            ProviderSettingsForm(
                form = uiState.form,
                onSave = viewModel::saveProvider,
            )
        } else {
            ChatView(
                uiState = uiState,
                onSend = viewModel::send,
                onRetry = viewModel::retry,
                onSaveProvider = viewModel::saveProvider,
            )
        }
    }
}

@Composable
private fun ChatView(
    uiState: AssistantUiState,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    onSaveProvider: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var prompt by remember { mutableStateOf("") }
    var showProviderForm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {

        // Reply + status area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                when (val status = uiState.status) {
                    AssistantStatus.Idle -> {
                        if (uiState.reply.isNotEmpty()) {
                            Text(text = uiState.reply, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    AssistantStatus.Streaming -> {
                        if (uiState.reply.isNotEmpty()) {
                            Text(
                                text = uiState.reply,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    is AssistantStatus.Done -> {
                        Text(text = uiState.reply, style = MaterialTheme.typography.bodyMedium)
                        if (status.refused) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "The assistant declined to respond.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is AssistantStatus.Error -> {
                        val errorText = when (val err = status.error) {
                            is UiError.Message -> err.text
                            UiError.Network -> "No network connection."
                            UiError.Unknown -> "Something went wrong."
                        }
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (status.retryable) {
                            Button(onClick = onRetry) { Text("Retry") }
                        } else if (status.showProviderCta) {
                            OutlinedButton(onClick = { showProviderForm = true }) {
                                Text("Set up / fix provider")
                            }
                        }
                    }
                }
            }
        }

        // Provider form (expandable — relocated to :feature:settings in a later phase)
        if (showProviderForm) {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Provider settings",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showProviderForm = false }) { Text("Close") }
            }
            ProviderSettingsForm(
                form = uiState.form,
                onSave = onSaveProvider,
            )
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            TextButton(
                onClick = { showProviderForm = true },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit provider")
            }
        }

        HorizontalDivider()

        // Prompt input
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                placeholder = { Text("Ask something…") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (prompt.isNotBlank() && uiState.status !is AssistantStatus.Streaming) {
                            onSend(prompt)
                            prompt = ""
                        }
                    },
                ),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (prompt.isNotBlank()) {
                        onSend(prompt)
                        prompt = ""
                    }
                },
                enabled = prompt.isNotBlank() && uiState.status !is AssistantStatus.Streaming,
            ) {
                Text("Send")
            }
        }
    }
}

/**
 * Provider-settings form. Fields: base URL, model (free-text), API key (masked).
 *
 * Key is never pre-filled — only a "key set ✓" indicator is shown from [ProviderFormState.keySet].
 * The key value goes straight to SecureSecretStore via [onSave] and never touches state.
 *
 * NOTE: Relocate to :feature:settings when that module is created
 * (Block-G wallpaper-button precedent — provider settings belong in settings, not inline here).
 */
@Composable
private fun ProviderSettingsForm(
    form: ProviderFormState,
    onSave: (baseUrl: String, model: String, apiKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var baseUrl by remember(form.baseUrl) { mutableStateOf(form.baseUrl) }
    var model by remember(form.modelId) { mutableStateOf(form.modelId) }
    var apiKey by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Base URL (https://…)") },
            placeholder = { Text("https://openrouter.ai/api/v1") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("Model") },
            placeholder = { Text("mistralai/mistral-7b-instruct") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = {
                Text(if (form.keySet) "API Key (set — replace to update)" else "API Key")
            },
            placeholder = { Text("sk-…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        if (form.saveError != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = form.saveError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { onSave(baseUrl, model, apiKey) },
            modifier = Modifier.fillMaxWidth(),
            enabled = baseUrl.isNotBlank() && model.isNotBlank(),
        ) {
            Text("Save")
        }
    }
}
