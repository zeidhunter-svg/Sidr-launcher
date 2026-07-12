package com.sidr.launcher.feature.assistant

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrPrimaryButton
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSecondaryButton
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.primitive.SidrDivider
import com.sidr.launcher.core.ui.primitive.SidrProgress
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing
import java.net.URI

/**
 * Assistant screen — pure render, no business logic. Chat only: the provider-settings form lives on
 * the separate [AssistantProviderScreen] (reached from Settings), so this surface never hosts the
 * key-bearing form or an "Edit provider" control. When no provider is configured it points the user to
 * that surface instead of embedding the form.
 */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier,
    initialPrompt: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isConfigured = uiState.form.baseUrl.isNotBlank()
    // Tap outside the composer to dismiss the soft keyboard (2026-07-12) — taps on the field/buttons
    // are consumed by them; only taps on the empty chat area reach this handler.
    val focusManager = LocalFocusManager.current

    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = "Assistant",
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = viewModel::navigateBack,
                    )
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Spacing.lg)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
        ) {
            if (!isConfigured) {
                Spacer(modifier = Modifier.height(Spacing.xl))
                SidrText(
                    text = "No AI provider configured yet.",
                    role = SidrTextRole.HUMAN_BODY,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                SidrText(
                    text = "Set one up in Settings → AI provider settings.",
                    role = SidrTextRole.PROVENANCE,
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                SidrPrimaryButton(
                    text = "Open provider settings",
                    onClick = viewModel::openProviderSettings,
                )
            } else {
                ChatView(
                    uiState = uiState,
                    initialPrompt = initialPrompt,
                    onSend = viewModel::send,
                    onRetry = viewModel::retry,
                    onOpenProvider = viewModel::openProviderSettings,
                )
            }
        }
    }
}

/**
 * Dedicated AI-provider setup surface (base URL / model / API key). This is the single place the
 * key-bearing provider form lives; it is reached from Settings → "AI provider settings" and is kept
 * entirely separate from the [AssistantScreen] chat so the two never overlap.
 */
@Composable
fun AssistantProviderScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = "AI provider",
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = viewModel::navigateBack,
                    )
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Spacing.lg)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            ProviderSettingsForm(
                form = uiState.form,
                onSave = viewModel::saveProvider,
            )
        }
    }
}

@Composable
private fun ChatView(
    uiState: AssistantUiState,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenProvider: () -> Unit,
    modifier: Modifier = Modifier,
    initialPrompt: String? = null,
) {
    // Block X6-C: seed the input from the one-shot nav-arg prompt (keyed on it so it initialises once
    // per navigation and never clobbers subsequent user edits). Prefill only — never auto-sent, and
    // it never touches SavedStateHandle (the assistant holds none by design).
    var prompt by remember(initialPrompt) { mutableStateOf(initialPrompt ?: "") }
    val scrollState = rememberScrollState()
    val colors = SidrTheme.colors
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxSize()) {

        // Reply + status area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                when (val status = uiState.status) {
                    AssistantStatus.Idle -> {
                        if (uiState.reply.isNotEmpty()) {
                            SidrText(text = uiState.reply, role = SidrTextRole.HUMAN_BODY)
                        }
                    }

                    AssistantStatus.Streaming -> {
                        if (uiState.reply.isNotEmpty()) {
                            SidrText(text = uiState.reply, role = SidrTextRole.HUMAN_BODY)
                            Spacer(modifier = Modifier.height(Spacing.sm))
                        }
                        SidrProgress(modifier = Modifier.fillMaxWidth())
                    }

                    is AssistantStatus.Done -> {
                        SidrText(text = uiState.reply, role = SidrTextRole.HUMAN_BODY)
                        if (status.refused) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            SidrText(
                                text = "The assistant declined to respond.",
                                role = SidrTextRole.PROVENANCE,
                            )
                        }
                    }

                    is AssistantStatus.Error -> {
                        val errorText = when (val err = status.error) {
                            is UiError.Message -> err.text
                            UiError.Network -> "No network connection."
                            UiError.Unknown -> "Something went wrong."
                        }
                        SidrText(
                            text = errorText,
                            role = SidrTextRole.HUMAN_BODY,
                            color = colors.danger,
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        if (status.retryable) {
                            SidrPrimaryButton(text = "Retry", onClick = onRetry)
                        } else if (status.showProviderCta) {
                            SidrSecondaryButton(text = "Fix provider settings", onClick = onOpenProvider)
                        }
                    }
                }

                // Provenance line (Task 3 step 3): host + model only — never the raw URL/scheme or key.
                if (uiState.form.baseUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    SidrText(
                        text = "CLOUD · ${providerHost(uiState.form.baseUrl)} · ${uiState.form.modelId}",
                        role = SidrTextRole.PROVENANCE,
                    )
                }
            }
        }

        SidrDivider()

        // Prompt input (Task 3 step 4): a single SidrSurface wraps the mono BasicTextField + send icon,
        // matching the DrawerSearchField idiom. Enablement/dispatch logic is unchanged from before.
        SidrSurface(
            tone = SidrSurfaceTone.SURFACE,
            shape = SidrShapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(vertical = Spacing.sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (prompt.isEmpty()) {
                        SidrText(
                            text = "Message",
                            role = SidrTextRole.HUMAN_BODY,
                            color = colors.faint,
                        )
                    }
                    BasicTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.merge(
                            SidrTheme.textStyles.command.copy(color = colors.text),
                        ),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (prompt.isNotBlank() && uiState.status !is AssistantStatus.Streaming) {
                                    onSend(prompt)
                                    prompt = ""
                                    focusManager.clearFocus()
                                }
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SidrIconButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    onClick = {
                        if (prompt.isNotBlank()) {
                            onSend(prompt)
                            prompt = ""
                            focusManager.clearFocus()
                        }
                    },
                    enabled = prompt.isNotBlank() && uiState.status !is AssistantStatus.Streaming,
                    tint = colors.accent,
                )
            }
        }
    }
}

/**
 * Host-only display for the provenance line (Task 3 step 3) — never the raw base URL/scheme, never
 * the key. Falls back to a neutral sentinel (never the raw string) if the URL doesn't parse or
 * carries no host, so a malformed `baseUrl` (e.g. `"https://my org.com"` or `"https://"`, both of
 * which throw `URISyntaxException`) can never leak the scheme onto the screen.
 *
 * `internal` (not `private`) solely so it's directly unit-testable from `feature/assistant/src/test`;
 * it is otherwise presentation-only and unused outside this file.
 */
internal fun providerHost(baseUrl: String): String =
    runCatching { URI(baseUrl).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_HOST

private const val UNKNOWN_HOST = "(unknown host)"

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
    // Never seeded from `form` — the key is write-only from this screen's point of view.
    var apiKey by remember { mutableStateOf("") }
    val colors = SidrTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        ProviderField(
            label = "BASE URL",
            value = baseUrl,
            onValueChange = { baseUrl = it },
            placeholder = "https://openrouter.ai/api/v1",
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        ProviderField(
            label = "MODEL",
            value = model,
            onValueChange = { model = it },
            placeholder = "mistralai/mistral-7b-instruct",
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        ProviderField(
            label = "API KEY",
            value = apiKey,
            onValueChange = { apiKey = it },
            placeholder = "sk-…",
            visualTransformation = PasswordVisualTransformation(),
        )
        if (form.keySet) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            SidrText(
                text = "Key set — replace to update.",
                role = SidrTextRole.PROVENANCE,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        if (form.saveError != null) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            SidrText(
                text = form.saveError,
                role = SidrTextRole.PROVENANCE,
                color = colors.danger,
                modifier = Modifier.padding(horizontal = Spacing.lg),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.lg))
        SidrPrimaryButton(
            text = "Save",
            onClick = { onSave(baseUrl, model, apiKey) },
            modifier = Modifier.fillMaxWidth(),
            enabled = baseUrl.isNotBlank() && model.isNotBlank(),
        )
    }
}

/**
 * Local labeled-field composition for the provider form: a [SidrSectionHeader] label over a
 * [SidrSurface]-wrapped mono [BasicTextField], matching the idiom used by the App Drawer's
 * `DrawerSearchField` (Task 2) and this screen's own chat composer (Task 3). Feature-local — not
 * promoted to `core/ui` since no other caller needs a labeled field yet.
 */
@Composable
private fun ProviderField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = SidrTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        SidrSectionHeader(text = label, modifier = Modifier.padding(horizontal = 0.dp))
        SidrSurface(
            tone = SidrSurfaceTone.SURFACE,
            shape = SidrShapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md),
            ) {
                if (value.isEmpty()) {
                    SidrText(
                        text = placeholder,
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
                    visualTransformation = visualTransformation,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
