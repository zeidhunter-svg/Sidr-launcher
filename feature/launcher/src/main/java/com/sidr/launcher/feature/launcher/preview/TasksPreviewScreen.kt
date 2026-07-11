package com.sidr.launcher.feature.launcher.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.component.SidrActionGate
import com.sidr.launcher.core.ui.component.SidrActionGateType
import com.sidr.launcher.core.ui.component.SidrPreviewBanner
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrStatusChip
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrStatusMarker
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Vision MVP Task 8 — Tasks tab design preview.
 *
 * This is a **non-functional mock-up** of the intent → plan → execution → result flow that Stage 3
 * (Agentic OS) will eventually implement. No agentic execution exists anywhere in this codebase yet, so
 * every piece of copy on this screen is fabricated sample data, deliberately labelled as such, and no
 * callback here does anything beyond an inert no-op or local UI state — there is nothing real to wire to
 * (Honesty hard rule). Do not add navigation, ViewModels, or domain/data imports to this file.
 *
 * Presentation-only: no domain/data/feature imports (this file lives in `feature/launcher`, which may
 * depend on `core/ui`, but must not reach into another feature module or into `domain`/`data`).
 */
@Composable
fun TasksPreviewScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.lg),
    ) {
        SidrPreviewBanner()

        IntentAndPlanSection()
        ExecutionSection()
        ResultSection()
    }
}

@Composable
private fun IntentAndPlanSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SidrSectionHeader(text = "INTENT + PLAN")

        SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SidrText(
                    text = "SAMPLE COMMAND — not a real request:",
                    role = SidrTextRole.PROVENANCE,
                )
                SidrText(
                    text = "> open the quarterly report and email it to the team",
                    role = SidrTextRole.COMMAND,
                )
                SidrText(
                    text = "Interpreted intent (sample): find the latest quarterly report file, " +
                        "then draft an email to the team with it attached.",
                    role = SidrTextRole.HUMAN_BODY,
                )
            }
        }

        SamplePlanStep(
            index = 1,
            description = "Search local files for \"quarterly report\" (sample)",
            chipLabel = "LOCAL",
            chipStatus = SidrStatus.SUCCESS,
        )
        SamplePlanStep(
            index = 2,
            description = "Look up the team's email addresses from contacts (sample)",
            chipLabel = "LOCAL",
            chipStatus = SidrStatus.SUCCESS,
        )
        SamplePlanStep(
            index = 3,
            description = "Draft the email body from the report contents (sample)",
            chipLabel = "CLOUD",
            chipStatus = SidrStatus.ATTENTION,
        )
        SamplePlanStep(
            index = 4,
            description = "Send the email via the mail provider's web API (sample)",
            chipLabel = "WEB",
            chipStatus = SidrStatus.INFO,
        )

        SidrText(
            text = "PLAN ≠ EXECUTION · nothing runs yet",
            role = SidrTextRole.PROVENANCE,
        )
    }
}

@Composable
private fun SamplePlanStep(
    index: Int,
    description: String,
    chipLabel: String,
    chipStatus: SidrStatus,
) {
    SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SidrText(
                text = "$index. $description",
                role = SidrTextRole.HUMAN_BODY,
                modifier = Modifier.padding(end = Spacing.sm),
            )
            SidrStatusChip(label = chipLabel, status = chipStatus)
        }
    }
}

@Composable
private fun ExecutionSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SidrSectionHeader(text = "EXECUTION (SAMPLE — NOT RUNNING)")

        SampleExecutionStep(label = "Search local files for \"quarterly report\"", status = SidrStatus.SUCCESS, state = "done")
        SampleExecutionStep(label = "Look up the team's email addresses", status = SidrStatus.SUCCESS, state = "done")
        SampleExecutionStep(label = "Draft the email body from the report", status = SidrStatus.ATTENTION, state = "running")
        SampleExecutionStep(label = "Send the email via the mail provider's web API", status = SidrStatus.INFO, state = "queued")

        // Sample-only consent gate: neither callback below does anything beyond an inert no-op —
        // this whole screen is non-functional and there is no real cloud step to call.
        SidrActionGate(
            type = SidrActionGateType.ExternalHandoff,
            title = "Cloud step — needs consent",
            consequence = "This step would call a cloud API. Sample only — nothing executes.",
            confirmLabel = "Confirm",
            onConfirm = {},
            onCancel = {},
        )
    }
}

@Composable
private fun SampleExecutionStep(label: String, status: SidrStatus, state: String) {
    SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SidrText(
                text = label,
                role = SidrTextRole.HUMAN_BODY,
                modifier = Modifier.padding(end = Spacing.sm),
            )
            SidrStatusMarker(status = status, label = state)
        }
    }
}

@Composable
private fun ResultSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SidrSectionHeader(text = "RESULT (SAMPLE)")

        SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SidrText(text = "• Quarterly_Report_Q2.pdf located (sample)", role = SidrTextRole.HUMAN_BODY)
                SidrText(text = "• Draft email prepared for the team (sample)", role = SidrTextRole.HUMAN_BODY)
                SidrText(text = "• Nothing was actually sent — preview only", role = SidrTextRole.HUMAN_BODY)
            }
        }

        SidrText(
            text = "LOCAL · file scan   WEB · none   CLOUD · summary draft (sample)",
            role = SidrTextRole.PROVENANCE,
        )
    }
}
