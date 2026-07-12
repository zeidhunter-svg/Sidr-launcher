package com.sidr.launcher.feature.launcher.preview

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrPreviewBanner
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Vision MVP Task 11 — Terminal tab design preview.
 *
 * This is a **non-functional mock-up** of a future on-device Python REPL. No interpreter of any
 * kind exists anywhere in this codebase, so this screen carries a sharper version of the Honesty
 * hard rule than its Task 8/9/10 siblings: it is the one preview screen where a naive
 * implementation might be tempted to echo the typed command back as fake "output" for visual
 * completeness. It must not — and does not. The transcript [Column] below the banner is rendered
 * once and never appended to: there is no code path, anywhere in this file, that writes a line
 * into it. Submitting the prompt field only clears that field; nothing is interpreted, nothing is
 * echoed, nothing is sampled/canned.
 *
 * Uses the standard [SidrPreviewBanner] default copy (matching the Task 8/9/10 sibling screens and
 * the literal `TerminalPreviewScreenTest.terminal_never_produces_output` assertion) plus one extra
 * terminal-specific honesty line calling out on-device Python by name — the Task 11 brief asked for
 * both, and they aren't mutually exclusive (`SidrPreviewBanner` only renders one string at a time).
 *
 * Do not add navigation, ViewModels, or domain/data imports to this file. [onBack] is the one
 * exception (added 2026-07-12 when this screen was demoted from a tab root to a pushed
 * destination, reached via the icon-only button in the app-shell footer): it only pops the back
 * stack, like every other pushed destination's back arrow — legitimate chrome, not a fabricated
 * feature.
 *
 * Presentation-only: no domain/data/feature imports (this file lives in `feature/launcher`, which
 * may depend on `core/ui`, but must not reach into another feature module or into `domain`/`data`).
 */
@Composable
fun TerminalPreviewScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var input by remember { mutableStateOf("") }
    val colors = SidrTheme.colors
    val focusManager = LocalFocusManager.current

    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = "Terminal",
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
                .padding(inner)
                .padding(bottom = Spacing.lg)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                },
        ) {
            // Banner + honesty line + transcript share one weighted, scrollable region so that in
            // landscape (where the keyboard eats most of the height) they compress/scroll instead of
            // starving the prompt row: the fixed-height banner and honesty text used to consume all
            // the space above a tall landscape keyboard, pushing the prompt off-screen behind it
            // (2026-07-12 fix). The prompt row stays OUTSIDE this region, pinned at the bottom and
            // lifted by its own `imePadding()`, so it is always visible just above the keyboard.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SidrPreviewBanner()
                SidrText(
                    text = "PREVIEW — on-device Python is coming; this terminal doesn't run yet.",
                    role = SidrTextRole.PROVENANCE,
                    color = colors.attention,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )

                // Transcript area: deliberately, permanently empty. No interpreter exists, so there is
                // no line — echoed command, computed result, or otherwise — that is ever appended here.
                // This Column's emptiness is the load-bearing safety property this whole screen exists
                // to demonstrate (see `terminal_never_produces_output`/
                // `typing_and_submitting_produces_no_output`).
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TERMINAL_TRANSCRIPT_TEST_TAG),
                ) {
                    // Intentionally left empty.
                }
            }

            TerminalPromptRow(
                input = input,
                onInputChange = { input = it },
                // Clear the field only — never append output, never interpret, never fabricate a result.
                // Also drop focus so the keyboard closes on submit (parity with search/assistant).
                onSubmit = {
                    input = ""
                    focusManager.clearFocus()
                },
            )
        }
    }
}

@Composable
private fun TerminalPromptRow(
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = SidrTheme.colors
    SidrSurface(
        tone = SidrSurfaceTone.SURFACE,
        shape = SidrShapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SidrText(text = ">>>", role = SidrTextRole.COMMAND, color = colors.accent)

            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(
                        SidrTheme.textStyles.command.copy(color = colors.text),
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { onSubmit() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * `internal` (not `private`) solely so it's directly referenceable from
 * `feature/launcher/src/test` to assert the transcript never gains children — otherwise unused
 * outside this file (`providerHost` in `AssistantScreen.kt` is the precedent for this pattern).
 */
internal const val TERMINAL_TRANSCRIPT_TEST_TAG = "terminal-transcript"
