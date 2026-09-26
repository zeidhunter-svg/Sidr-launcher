package com.sidr.launcher.feature.launcher.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.component.SidrPreviewBanner
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrStatusMarker
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Vision MVP Task 10 — Activity tab design preview.
 *
 * This is a **non-functional mock-up** of a future activity timeline (the A5 Activity Trace
 * backing contract Stage 3 (Agentic OS) may eventually implement). No such trace exists anywhere
 * in this codebase yet, so every row on this screen is fabricated sample data, deliberately
 * labelled as such, and none of it is reactive to any real state — there is nothing real to wire
 * to (Honesty hard rule). Do not add navigation, ViewModels, or domain/data imports to this file.
 *
 * Presentation-only: no domain/data/feature imports (this file lives in `feature/launcher`, which
 * may depend on `core/ui`, but must not reach into another feature module or into `domain`/`data`).
 */
@Composable
fun ActivityPreviewScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.lg),
    ) {
        SidrPreviewBanner()
        TimelineSection()
    }
}

@Composable
private fun TimelineSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SidrSectionHeader(text = "ACTIVITY")

        SampleTimelineRow(
            status = SidrStatus.SUCCESS,
            markerLabel = "DONE",
            description = "Prepared Ktor report · 4 steps · 14:40",
        )
        SampleTimelineRow(
            status = SidrStatus.SUCCESS,
            markerLabel = "LOCAL",
            description = "Opened Türkiye Finans · learned · LOCAL",
        )
        SampleTimelineRow(
            status = SidrStatus.ATTENTION,
            markerLabel = "EXTERNAL",
            description = "Opened GitHub · confirmed external",
        )
        SampleTimelineRow(
            status = SidrStatus.DANGER,
            markerLabel = "FAILED",
            description = "Web action failed · no browser",
        )

        SidrText(
            text = "EPHEMERAL BY DEFAULT · OPT-IN PERSIST",
            role = SidrTextRole.PROVENANCE,
        )
    }
}

@Composable
private fun SampleTimelineRow(status: SidrStatus, markerLabel: String, description: String) {
    SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SidrStatusMarker(status = status, label = markerLabel)
            SidrText(text = description, role = SidrTextRole.HUMAN_BODY)
        }
    }
}
