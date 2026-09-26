package com.sidr.launcher.core.ui.primitive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.SidrThemePreviews

/** Preview-only DS-2 primitive gallery (proof surface). Not a production screen; no feature dependency. */
@Composable
fun PrimitiveGallery() {
    Surface(color = SidrTheme.colors.ground) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SidrSystemLabel("PRIMITIVES")
            SidrText("Open Telegram", role = SidrTextRole.COMMAND)
            SidrText("There is no deity except Allah", role = SidrTextRole.SACRED)
            SidrText("Assistant answer in readable prose.", role = SidrTextRole.HUMAN_BODY)
            SidrProvenanceLine("local", listOf("14 ms"))
            SidrProvenanceLine("routed by ai", listOf("openrouter"))
            SidrProvenanceLine("diyanet", listOf("istanbul", "updated 2h ago"))
            SidrStatusMarker(SidrStatus.SUCCESS, "LOCAL")
            SidrStatusMarker(SidrStatus.ATTENTION, "CLOUD")
            SidrStatusMarker(SidrStatus.CAUTION, "CONFIRM")
            SidrStatusMarker(SidrStatus.DANGER, "FAILED")
            SidrDivider()
            SidrSurface(SidrSurfaceTone.RAISED) {
                SidrText("Raised surface", role = SidrTextRole.HUMAN_BODY, modifier = Modifier.padding(12.dp))
            }
            SidrSurface(SidrSurfaceTone.RISK) {
                SidrText("Risk surface", role = SidrTextRole.HUMAN_BODY, modifier = Modifier.padding(12.dp))
            }
            SidrProgress()
            SidrProgress(progress = 0.4f)
        }
    }
}

@SidrThemePreviews
@Composable
private fun PrimitiveGalleryPreview() {
    SidrTheme { PrimitiveGallery() }
}
