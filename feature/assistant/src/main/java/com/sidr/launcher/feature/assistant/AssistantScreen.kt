package com.sidr.launcher.feature.assistant

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.ui.component.SidrAssistantComposer
import com.sidr.launcher.core.ui.component.SidrErrorSurface
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrPrimaryButton
import com.sidr.launcher.core.ui.component.SidrPrivacyNotice
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrStreamingIndicator
import com.sidr.launcher.core.ui.component.SidrSurfaceAction
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrDivider
import com.sidr.launcher.core.ui.primitive.SidrProvenanceLine
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Assistant screen — pure render, no business logic (DS-10).
 *
 * Chat only: the provider-settings form lives on the separate [AssistantProviderScreen] (reached from
 * Settings), so this surface never hosts the key-bearing form or an "Edit provider" control. When no
 * provider is configured it discloses that nothing is sent anywhere and points at that surface.
 *
 * DS-10 is a **presentation migration**: streaming, latest-wins cancellation, retry, BYOK/Keystore
 * handling, and the prefill-only initial prompt are all unchanged — every callback still goes
 * straight to the untouched [AssistantViewModel].
 */
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier,
    initialPrompt: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AssistantShell(
        title = sidrString(R.string.assistant_title),
        onBack = viewModel::navigateBack,
        modifier = modifier,
    ) { contentModifier ->
        AssistantContent(
            uiState = uiState,
            initialPrompt = initialPrompt,
            onSend = viewModel::send,
            onRetry = viewModel::retry,
            onOpenProvider = viewModel::openProviderSettings,
            modifier = contentModifier,
        )
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

    AssistantShell(
        title = sidrString(R.string.assistant_provider_title),
        onBack = viewModel::navigateBack,
        modifier = modifier,
    ) { contentModifier ->
        Column(
            modifier = contentModifier.verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            AssistantProviderPanel(
                form = uiState.form,
                onSave = viewModel::saveProvider,
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}

// ── Shell ────────────────────────────────────────────────────────────────────────────────────────

/**
 * Shared SIDR chrome for both assistant surfaces: top bar with a labelled back action, and the
 * horizontal gutter every DS-3 surface uses. The child receives the already-padded modifier so no
 * caller re-derives insets.
 */
@Composable
private fun AssistantShell(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = title,
                navigationIcon = {
                    SidrIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = sidrString(R.string.assistant_back),
                        onClick = onBack,
                    )
                },
            )
        },
    ) { inner ->
        content(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = Spacing.lg),
        )
    }
}

// ── Chat ─────────────────────────────────────────────────────────────────────────────────────────

/**
 * Stateless chat body. Holds exactly one piece of local state — the composer text — seeded once per
 * navigation from the one-shot `initialPrompt` nav arg.
 */
@Composable
internal fun AssistantContent(
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
    val focusManager = LocalFocusManager.current
    val isConfigured = uiState.form.baseUrl.isNotBlank()

    Column(
        modifier = modifier.pointerInput(Unit) {
            // Tap outside the composer to dismiss the soft keyboard (2026-07-12) — taps on the
            // field/buttons are consumed by them; only taps on the empty chat area reach this.
            detectTapGestures(onTap = { focusManager.clearFocus() })
        },
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Column(
                modifier = Modifier.padding(vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (isConfigured) {
                    AssistantMessage(
                        uiState = uiState,
                        onRetry = onRetry,
                        onOpenProvider = onOpenProvider,
                    )
                } else {
                    AssistantNoProviderPanel(onOpenProvider = onOpenProvider)
                }
            }
        }

        if (isConfigured) {
            AssistantStatusLine(form = uiState.form)
            SidrDivider()
            AssistantComposer(
                value = prompt,
                onValueChange = { prompt = it },
                sending = uiState.status is AssistantStatus.Streaming,
                onSend = {
                    onSend(prompt)
                    prompt = ""
                    focusManager.clearFocus()
                },
            )
        }
    }
}

/**
 * Reply / status area. Prose answers render in Interface Sans ([SidrTextRole.HUMAN_BODY]); the
 * cloud disclosure, refusal note, and provenance stay in mono (spec §5).
 */
@Composable
private fun AssistantMessage(
    uiState: AssistantUiState,
    onRetry: () -> Unit,
    onOpenProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        when (val status = uiState.status) {
            AssistantStatus.Idle -> {
                if (uiState.reply.isNotEmpty()) {
                    SidrText(text = uiState.reply, role = SidrTextRole.HUMAN_BODY)
                } else {
                    // Nothing said yet: the calm state is the cloud disclosure itself, so the user
                    // reads where their words will go *before* the first Send (spec §6).
                    SidrPrivacyNotice(
                        title = sidrString(R.string.assistant_cloud_disclosure_title),
                        body = sidrString(R.string.assistant_cloud_disclosure_body),
                    )
                }
            }

            AssistantStatus.Streaming -> {
                if (uiState.reply.isNotEmpty()) {
                    SidrText(text = uiState.reply, role = SidrTextRole.HUMAN_BODY)
                }
                SidrStreamingIndicator()
            }

            is AssistantStatus.Done -> {
                SidrText(text = uiState.reply, role = SidrTextRole.HUMAN_BODY)
                if (status.refused) {
                    // Refusal is a *successful* terminal state — calm supporting text, never an
                    // error surface (spec §7).
                    SidrText(
                        text = sidrString(R.string.assistant_refusal_note),
                        role = SidrTextRole.PROVENANCE,
                    )
                }
            }

            is AssistantStatus.Error -> {
                val presentation = status.toPresentation()
                val whyArg = presentation.whyArg
                SidrErrorSurface(
                    title = sidrString(presentation.title),
                    whatFailed = sidrString(presentation.whatFailed),
                    // The mapper already picked the value-present / value-absent key, so this passes
                    // at most one runtime argument and never nests one resource inside another.
                    why = if (whyArg != null) sidrString(presentation.why, whyArg) else sidrString(presentation.why),
                    next = sidrString(presentation.next),
                    primaryAction = presentation.primaryLabel?.let { label ->
                        SidrSurfaceAction(sidrString(label)) {
                            if (status.retryable) onRetry() else onOpenProvider()
                        }
                    },
                )
            }
        }
    }
}

/**
 * The always-visible provenance line, pinned directly above the composer so cloud use is legible at
 * the moment of sending (spec §6). Host + model + key presence only — never the raw base URL and
 * never the key itself.
 */
@Composable
private fun AssistantStatusLine(
    form: ProviderFormState,
    modifier: Modifier = Modifier,
) {
    SidrProvenanceLine(
        source = sidrString(R.string.assistant_provenance_cloud),
        details = providerProvenanceDetails(
            providerProvenance(
                baseUrl = form.baseUrl,
                modelId = form.modelId,
                keySet = form.keySet,
            ),
        ),
        modifier = modifier.padding(vertical = Spacing.sm),
    )
}

/**
 * Renders the pure [ProviderProvenance] value as `SidrProvenanceLine` details.
 *
 * The host slot falls back to the locked `assistant_provider_unknown_host` sentinel — never the raw
 * `baseUrl` — so a malformed URL cannot put its scheme on screen or into TalkBack. These tokens are
 * spec §7.1 machine vocabulary (`translatable="false"`); `SidrProvenanceLine` upper-cases them with
 * `Locale.ROOT`, so nothing here folds case itself.
 */
@Composable
@ReadOnlyComposable
private fun providerProvenanceDetails(provenance: ProviderProvenance): List<String> {
    val host = provenance.host ?: sidrString(R.string.assistant_provider_unknown_host)
    val keyPresence = if (provenance.keySet) {
        sidrString(R.string.assistant_provenance_key_in_keystore)
    } else {
        sidrString(R.string.assistant_provenance_no_key_set)
    }
    return listOfNotNull(host, provenance.modelId, keyPresence)
}

/**
 * The honest provenance for every not-yet-configured state: nothing has left the device, so the line
 * must not say `CLOUD`. Shared by the no-provider panel and the empty provider form (fixed on-device
 * during DS-10, when the empty form claimed `CLOUD · (UNKNOWN HOST)`).
 */
@Composable
private fun LocalOnlyProvenanceLine(modifier: Modifier = Modifier) {
    SidrProvenanceLine(
        source = sidrString(R.string.assistant_provenance_local_only),
        details = listOf(sidrString(R.string.assistant_provenance_no_provider)),
        modifier = modifier,
    )
}

/** DS-3 composer wrapper: the feature owns the text and the post-send reset; core/ui owns the look. */
@Composable
private fun AssistantComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    modifier: Modifier = Modifier,
) {
    SidrAssistantComposer(
        value = value,
        onValueChange = onValueChange,
        onSend = onSend,
        sending = sending,
        modifier = modifier
            .imePadding()
            .padding(vertical = Spacing.sm),
    )
}

/** Unconfigured state: disclose that nothing leaves the device yet, then point at the setup surface. */
@Composable
private fun AssistantNoProviderPanel(
    onOpenProvider: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SidrPrivacyNotice(
            title = sidrString(R.string.assistant_no_provider_title),
            body = sidrString(R.string.assistant_no_provider_body),
            provenance = { LocalOnlyProvenanceLine() },
        )
        SidrPrimaryButton(
            text = sidrString(R.string.assistant_open_provider_settings),
            onClick = onOpenProvider,
        )
    }
}

// ── Provider panel ───────────────────────────────────────────────────────────────────────────────

/**
 * Provider-settings panel. Fields: base URL, model (free-text), API key (masked, password
 * semantics).
 *
 * The key is never pre-filled — only a "key set" indicator is derived from [ProviderFormState.keySet].
 * The key value goes straight to `SecureSecretStore` via [onSave] and never touches state, the
 * provenance line, or a log.
 */
@Composable
internal fun AssistantProviderPanel(
    form: ProviderFormState,
    onSave: (baseUrl: String, model: String, apiKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var baseUrl by remember(form.baseUrl) { mutableStateOf(form.baseUrl) }
    var model by remember(form.modelId) { mutableStateOf(form.modelId) }
    // Never seeded from `form` — the key is write-only from this screen's point of view.
    var apiKey by remember { mutableStateOf("") }
    val colors = SidrTheme.colors

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SidrPrivacyNotice(
            title = sidrString(R.string.assistant_cloud_disclosure_title),
            body = sidrString(R.string.assistant_cloud_disclosure_body),
            provenance = {
                // Never claim "CLOUD" before a provider exists — an empty form is still local-only.
                if (form.baseUrl.isNotBlank()) {
                    SidrProvenanceLine(
                        source = sidrString(R.string.assistant_provenance_cloud),
                        details = providerProvenanceDetails(
                            providerProvenance(
                                baseUrl = form.baseUrl,
                                modelId = form.modelId,
                                keySet = form.keySet,
                            ),
                        ),
                    )
                } else {
                    LocalOnlyProvenanceLine()
                }
            },
        )

        ProviderField(
            label = sidrString(R.string.assistant_field_base_url),
            value = baseUrl,
            onValueChange = { baseUrl = it },
            placeholder = sidrString(R.string.assistant_placeholder_base_url),
        )
        ProviderField(
            label = sidrString(R.string.assistant_field_model),
            value = model,
            onValueChange = { model = it },
            placeholder = sidrString(R.string.assistant_placeholder_model),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            ProviderField(
                label = sidrString(R.string.assistant_field_api_key),
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = sidrString(R.string.assistant_placeholder_api_key),
                visualTransformation = PasswordVisualTransformation(),
                isPassword = true,
            )
            if (form.keySet) {
                SidrText(
                    text = sidrString(R.string.assistant_key_set_note),
                    role = SidrTextRole.PROVENANCE,
                )
            }
            form.saveError?.let { saveError ->
                SidrText(
                    text = sidrString(saveError.messageRes()),
                    role = SidrTextRole.PROVENANCE,
                    color = colors.danger,
                )
            }
        }

        SidrPrimaryButton(
            text = sidrString(R.string.assistant_save),
            onClick = { onSave(baseUrl, model, apiKey) },
            modifier = Modifier.fillMaxWidth(),
            enabled = baseUrl.isNotBlank() && model.isNotBlank(),
        )
    }
}

/**
 * Local labeled-field composition for the provider form: a [SidrSectionHeader] label over a
 * [SidrSurface]-wrapped mono [BasicTextField], matching the idiom used by the App Drawer's
 * `DrawerSearchField` and the assistant composer. Feature-local — not promoted to `core/ui` since no
 * other caller needs a labeled field yet.
 *
 * [isPassword] adds Compose password semantics on top of the visual mask so TalkBack treats the
 * field as secret rather than reading it back character by character (spec §8).
 */
@Composable
private fun ProviderField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isPassword: Boolean = false,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = label
                            if (isPassword) password()
                        },
                )
            }
        }
    }
}
