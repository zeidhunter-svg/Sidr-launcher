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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.component.SidrNavigationRow
import com.sidr.launcher.core.ui.component.SidrPreviewBanner
import com.sidr.launcher.core.ui.component.SidrPrimaryButton
import com.sidr.launcher.core.ui.component.SidrSecondaryButton
import com.sidr.launcher.core.ui.component.SidrSectionHeader
import com.sidr.launcher.core.ui.component.SidrStatusChip
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Vision MVP Task 9 — Agents tab design preview.
 *
 * This is a **non-functional mock-up** of a single sample agent card, previewing what Stage 3
 * (Agentic OS) may eventually look like. No agent runtime exists anywhere in this codebase yet
 * (there is no A1/A4/A6 backing contract to wire to), so every piece of copy on this screen is
 * fabricated sample data, deliberately labelled as such, and every callback here is a genuinely
 * empty no-op — there is nothing real to pause, open, or invoke (Honesty hard rule). Do not add
 * navigation, ViewModels, or domain/data imports to this file.
 *
 * Presentation-only: no domain/data/feature imports (this file lives in `feature/launcher`, which
 * may depend on `core/ui`, but must not reach into another feature module or into `domain`/`data`).
 */
@Composable
fun AgentsPreviewScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = Spacing.lg),
    ) {
        SidrPreviewBanner()
        SampleAgentCard()
    }
}

@Composable
private fun SampleAgentCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SidrText(
                        text = "Research agent",
                        role = SidrTextRole.HUMAN_TITLE,
                        modifier = Modifier.padding(end = Spacing.sm),
                    )
                    SidrStatusChip(label = "Active", status = SidrStatus.SUCCESS)
                }
                SidrText(
                    text = "SAMPLE AGENT — not a real capability: a mock-up of an agent that might one day " +
                        "gather background info on a topic from the web and your local files (sample only).",
                    role = SidrTextRole.HUMAN_BODY,
                )
            }
        }

        SidrSectionHeader(text = "TOOLS")
        SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column {
                SidrNavigationRow(title = "Web search", onClick = {})
                SidrNavigationRow(title = "Document reader", onClick = {})
            }
        }

        SidrSectionHeader(text = "PERMISSIONS")
        SidrSurface(tone = SidrSurfaceTone.SURFACE, shape = SidrShapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column {
                SidrNavigationRow(title = "Network", onClick = {})
                SidrNavigationRow(title = "Temporary context", onClick = {})
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // Both buttons are genuinely inert: there is no real agent runtime behind this sample
            // card, so there is nothing to pause or open (Honesty hard rule).
            SidrSecondaryButton(text = "Pause", onClick = {})
            SidrPrimaryButton(text = "Open", onClick = {})
        }
    }
}
