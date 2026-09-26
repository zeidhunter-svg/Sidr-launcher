package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.primitive.SidrSystemLabel
import com.sidr.launcher.core.ui.theme.SidrTheme

@Composable
fun MemoryGallery() {
    Surface(color = SidrTheme.colors.ground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SidrSystemLabel("MEMORY ITEMS")
            SidrMemoryItem(
                title = "open bank",
                value = "Turkiye Finans",
                type = SidrMemoryType.LearnedPreference,
                status = SidrMemoryStatus.Active,
                evidence = "Based on confirmed choices",
                provenance = "Learned from ambiguous launches",
                lastUsed = "Last used 8 Jul 2026",
                onForget = {},
            )
            SidrMemoryItem(
                title = "open maps",
                value = "Maps",
                type = SidrMemoryType.LearnedPreference,
                status = SidrMemoryStatus.Learning,
                evidence = "Learning from confirmed choices",
                provenance = "Device memory",
            )
            SidrMemoryItem(
                title = "message the project channel",
                value = "A very long application name that wraps cleanly without overlapping actions",
                type = SidrMemoryType.LearnedPreference,
                status = SidrMemoryStatus.NeedsReconfirmation,
                evidence = "Needs reconfirmation before auto-open",
                provenance = "Device memory",
                onForget = {},
            )
            SidrMemoryItem(
                title = "old notes app",
                value = "Target app unavailable",
                type = SidrMemoryType.LearnedPreference,
                status = SidrMemoryStatus.Unavailable,
                evidence = "Target unavailable",
                provenance = "Device memory",
            )
            SidrMemoryItem(
                title = "work chat",
                value = "Telegram",
                type = SidrMemoryType.ExplicitAlias,
                status = SidrMemoryStatus.Active,
                evidence = "User-declared alias",
                provenance = "Settings",
                onEdit = {},
                onForget = {},
            )

            SidrSystemLabel("DISCLOSURE")
            SidrMemoryDisclosure(
                title = "New learned choice",
                description = "\"open bank\" now prefers Turkiye Finans.",
                evidence = "Based on confirmed choices",
                provenance = "Local only",
                onView = {},
                onForget = {},
            )

            SidrSystemLabel("FORGET GATE")
            SidrForgetGate(
                title = "Forget learned choice?",
                consequence = "\"open bank\" will no longer prefer Turkiye Finans. The next ambiguous request will ask you to choose again.",
                evidence = "Stored only on this device.",
                onCancel = {},
                onForget = {},
            )

            SidrSystemLabel("EMPTY")
            EmptyState(message = "No learned choices yet\nPreferences appear only after confirmed choices.\nStored only on this device.")
        }
    }
}
