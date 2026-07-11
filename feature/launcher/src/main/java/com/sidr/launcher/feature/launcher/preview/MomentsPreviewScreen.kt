package com.sidr.launcher.feature.launcher.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.component.SidrIconButton
import com.sidr.launcher.core.ui.component.SidrPreviewBanner
import com.sidr.launcher.core.ui.component.SidrPrimaryButton
import com.sidr.launcher.core.ui.component.SidrScaffold
import com.sidr.launcher.core.ui.component.SidrSecondaryButton
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrStatusChip
import com.sidr.launcher.core.ui.component.SidrTopBar
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Vision MVP Task 12 — interaction-moment previews (Result / Partial / Error).
 *
 * This is a **non-functional mock-up** of what a completed, partially-completed, and failed
 * command outcome will look like once Stage 3 (Agentic OS) executes real multi-step plans. No
 * such execution exists anywhere in this codebase yet, so every piece of copy on this screen is
 * fabricated sample data, deliberately labelled as such, and no button here does anything beyond
 * an inert no-op — there is nothing real to wire to (Honesty hard rule).
 *
 * Presentation-only: no domain/data/feature imports (this file lives in `feature/launcher`, which
 * may depend on `core/ui`, but must not reach into another feature module or into `domain`/`data`).
 * The only real behaviour on this screen is [onBack], which pops the back stack like every other
 * pushed destination — that is legitimate chrome, not a fabricated feature.
 */
@Composable
fun MomentsPreviewScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SidrScaffold(
        modifier = modifier,
        topBar = {
            SidrTopBar(
                title = "Interaction moments",
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.lg),
        ) {
            SidrPreviewBanner()

            ResultMomentSection()
            PartialMomentSection()
            ErrorMomentSection()
        }
    }
}

@Composable
private fun ResultMomentSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SidrSectionHeader(text = "RESULT (SAMPLE)")

        SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SidrStatusChip(label = "✓ COMPLETED", status = SidrStatus.SUCCESS)
                SidrText(text = "Opened Notes", role = SidrTextRole.HUMAN_BODY)
                SidrText(text = "LOCAL · 14 MS", role = SidrTextRole.PROVENANCE)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SidrSecondaryButton(text = "Change", onClick = {})
                }
            }
        }
    }
}

@Composable
private fun PartialMomentSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SidrSectionHeader(text = "PARTIAL (SAMPLE)")

        SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SidrStatusChip(label = "! PARTIALLY COMPLETED", status = SidrStatus.ATTENTION)
                SidrText(
                    text = "Route prepared; reminder not created — permission off",
                    role = SidrTextRole.HUMAN_BODY,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SidrSecondaryButton(text = "Enable", onClick = {})
                    SidrSecondaryButton(text = "View", onClick = {})
                }
            }
        }
    }
}

@Composable
private fun ErrorMomentSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SidrSectionHeader(text = "ERROR (SAMPLE)")

        SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SidrStatusChip(label = "✕ ACTION FAILED", status = SidrStatus.DANGER)
                SidrText(text = "The app isn't installed", role = SidrTextRole.HUMAN_BODY)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SidrPrimaryButton(text = "Open Play Store", onClick = {})
                    SidrSecondaryButton(text = "Choose another", onClick = {})
                }
            }
        }
    }
}
