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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sidr.launcher.core.ui.component.EmptyState
import com.sidr.launcher.core.ui.component.ErrorState
import com.sidr.launcher.core.ui.component.SidrChoiceRow
import com.sidr.launcher.core.ui.component.SidrForgetGate
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrMemoryItem
import com.sidr.launcher.core.ui.component.SidrMemoryType
import com.sidr.launcher.core.ui.component.SidrPrimaryButton
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrProgress
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.memory.alias.MAX_ALIAS_PHRASE_LENGTH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AliasesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AliasesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AliasesContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
        modifier = modifier,
    )
}

@Composable
private fun AliasesContent(
    uiState: AliasesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var phrase by rememberSaveable { mutableStateOf("") }
    var selectedPackageName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingForget by remember { mutableStateOf<AliasMemoryUiModel?>(null) }

    LaunchedEffect(uiState.pickerApps) {
        val current = selectedPackageName
        if (current == null || uiState.pickerApps.none { it.packageName == current }) {
            selectedPackageName = uiState.pickerApps.firstOrNull()?.packageName
        }
    }

    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = sidrString(R.string.settings_aliases_title),
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
            contentPadding = PaddingValues(vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            when {
                uiState.isLoading -> item {
                    LoadingState(modifier = Modifier.fillParentMaxHeight())
                }
                uiState.errorMessage != null -> item {
                    ErrorState(
                        message = uiState.errorMessage,
                        onRetry = if (uiState.canRetry) onRetry else null,
                        modifier = Modifier.fillParentMaxHeight(),
                    )
                }
                else -> {
                    item { SidrSectionHeader(text = sidrString(R.string.settings_add_alias_header)) }
                    item {
                        AliasPhraseForm(
                            phrase = phrase,
                            selectedApp = uiState.pickerApps.firstOrNull { it.packageName == selectedPackageName },
                            onPhraseChange = { phrase = it },
                            onSave = {
                                val packageName = selectedPackageName ?: return@AliasPhraseForm
                                onSave(phrase, packageName)
                                phrase = ""
                            },
                            modifier = Modifier.padding(horizontal = Spacing.sm),
                        )
                    }

                    item { SidrSectionHeader(text = sidrString(R.string.settings_target_app_header)) }
                    if (uiState.pickerApps.isEmpty()) {
                        item {
                            EmptyState(
                                message = sidrString(R.string.settings_no_apps_available),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        items(uiState.pickerApps, key = { "picker:${it.packageName}" }) { app ->
                            SidrChoiceRow(
                                title = app.label,
                                selected = app.packageName == selectedPackageName,
                                onClick = { selectedPackageName = app.packageName },
                            )
                        }
                    }

                    item { SidrSectionHeader(text = sidrString(R.string.settings_aliases_section_header)) }
                    pendingForget?.let { alias ->
                        item(key = "forget:${alias.stableId}") {
                            SidrForgetGate(
                                title = sidrString(R.string.settings_forget_alias_title),
                                consequence = sidrString(
                                    R.string.settings_forget_alias_consequence,
                                    alias.phrase,
                                    alias.targetLabel,
                                ),
                                evidence = sidrString(R.string.settings_stored_locally),
                                onCancel = { pendingForget = null },
                                onForget = {
                                    onDelete(alias.stableId)
                                    pendingForget = null
                                },
                                modifier = Modifier.padding(Spacing.md),
                            )
                        }
                    }
                    if (uiState.aliases.isEmpty()) {
                        item {
                            EmptyState(
                                message = sidrString(R.string.settings_aliases_empty_message),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        items(uiState.aliases, key = { it.stableId }) { alias ->
                            AliasItem(alias = alias, onForget = { pendingForget = alias })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AliasPhraseForm(
    phrase: String,
    selectedApp: AliasPickerAppUiModel?,
    onPhraseChange: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedPhrase = CommandNormalizer.normalize(phrase)
    val normalizedApp = selectedApp?.let { CommandNormalizer.normalize(it.label) }
    val phraseTooLong = normalizedPhrase.length > MAX_ALIAS_PHRASE_LENGTH
    val canSave = normalizedPhrase.isNotBlank() && selectedApp != null && !phraseTooLong

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        AliasPhraseField(value = phrase, onValueChange = onPhraseChange)
        selectedApp?.let {
            SidrText(
                text = sidrString(R.string.settings_alias_target_prefix, it.label),
                role = SidrTextRole.PROVENANCE,
            )
        }
        if (normalizedPhrase.isNotBlank() && normalizedPhrase == normalizedApp) {
            SidrText(
                text = sidrString(R.string.settings_alias_phrase_collision_warning),
                role = SidrTextRole.PROVENANCE,
                color = SidrTheme.colors.caution,
            )
        }
        if (phraseTooLong) {
            SidrText(
                text = sidrString(R.string.settings_alias_phrase_too_long),
                role = SidrTextRole.PROVENANCE,
                color = SidrTheme.colors.danger,
            )
        }
        SidrPrimaryButton(
            text = sidrString(R.string.settings_add_alias_button),
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        )
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
private fun AliasPhraseField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SidrTheme.colors
    SidrSurface(
        tone = SidrSurfaceTone.SURFACE,
        shape = SidrShapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
        ) {
            if (value.isEmpty()) {
                SidrText(
                    text = sidrString(R.string.settings_alias_phrase_placeholder),
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {}),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AliasItem(alias: AliasMemoryUiModel, onForget: () -> Unit) {
    SidrMemoryItem(
        title = alias.phrase,
        value = alias.targetLabel,
        type = SidrMemoryType.ExplicitAlias,
        status = alias.status,
        evidence = alias.evidence,
        provenance = alias.provenance,
        localOnly = alias.localOnly,
        leadingContent = {
            AliasIcon(packageName = alias.targetPackageName, label = alias.targetLabel)
        },
        onForget = onForget,
        modifier = Modifier.padding(horizontal = Spacing.sm),
    )
}

@Composable
private fun AliasIcon(packageName: String, label: String) {
    val icon by rememberAliasAppIcon(packageName)
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
private fun rememberAliasAppIcon(packageName: String): State<ImageBitmap?> {
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
