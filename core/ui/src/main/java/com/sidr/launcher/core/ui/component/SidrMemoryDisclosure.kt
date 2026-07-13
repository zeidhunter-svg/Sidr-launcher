package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

@Composable
fun SidrMemoryDisclosure(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    evidence: String? = null,
    provenance: String? = null,
    onView: (() -> Unit)? = null,
    onForget: (() -> Unit)? = null,
) {
    val summary = listOfNotNull(
        title,
        description,
        evidence,
        provenance,
    ).joinToString(", ")

    SidrSurface(
        tone = SidrSurfaceTone.SURFACE,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = summary },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SidrText(text = title, role = SidrTextRole.HUMAN_TITLE)
            SidrText(text = description, role = SidrTextRole.HUMAN_BODY)

            evidence?.let {
                SidrMemoryEvidence(
                    evidence = it,
                    provenance = provenance,
                    localOnly = true,
                )
            }

            if (onView != null || onForget != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onView?.let {
                        SidrSecondaryButton(text = "View", onClick = it)
                    }
                    onForget?.let {
                        SidrDestructiveButton(
                            text = "Forget",
                            onClick = it,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrMemoryDisclosurePreview() {
    SidrTheme(darkTheme = true) {
        SidrMemoryDisclosure(
            title = "New learned choice",
            description = "\"open bank\" now prefers Turkiye Finans.",
            evidence = "Based on confirmed choices",
            provenance = "Local only",
            onView = {},
            onForget = {},
        )
    }
}
