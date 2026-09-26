package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.primitive.SidrProvenanceLine
import com.sidr.launcher.core.ui.primitive.SidrSystemLabel
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme

/**
 * DS-10 gallery: every Assistant state the migrated surface can be in, rendered from the same
 * `core/ui` pieces `feature/assistant` composes — the DS-10 composer/streaming controls plus the
 * DS-5 privacy and error surfaces.
 *
 * Feature-local wiring (which callback fires) is proven by `:feature:assistant` unit tests; this
 * gallery pins the *look* across dark/light/font-scale-2.0/RTL.
 */
@Composable
fun AssistantGallery() {
    Surface(color = SidrTheme.colors.ground) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SidrSystemLabel("NO PROVIDER")
            SidrPrivacyNotice(
                title = "No AI provider configured",
                body = "The assistant needs a provider you configure and pay for. Until then nothing " +
                    "is sent anywhere, and the rest of the launcher keeps working offline.",
                provenance = {
                    SidrProvenanceLine(source = "local only", details = listOf("no provider configured"))
                },
            )
            SidrPrimaryButton(text = "Open provider settings", onClick = {})

            SidrSystemLabel("IDLE — CLOUD DISCLOSURE")
            SidrPrivacyNotice(
                title = "Replies come from your provider",
                body = "When you tap Send, the message you typed is sent to the AI provider you " +
                    "configured. Nothing else goes with it — no apps, no history, no location, no " +
                    "memory. Your API key stays in this device's Keystore and is never shown or sent " +
                    "anywhere else.",
            )
            SidrProvenanceLine(
                source = "cloud",
                details = listOf("openrouter.ai", "mistralai/mistral-7b-instruct", "key in keystore"),
            )
            SidrAssistantComposer(value = "", onValueChange = {}, onSend = {})

            SidrSystemLabel("STREAMING")
            SidrText(
                text = "The Hijri date advances at sunset, so a Gregorian calendar day maps onto two " +
                    "Hijri days depending on the hour.",
                role = SidrTextRole.HUMAN_BODY,
            )
            SidrStreamingIndicator()
            SidrAssistantComposer(value = "why does the date shift", onValueChange = {}, onSend = {}, sending = true)

            SidrSystemLabel("COMPLETED")
            SidrText(
                text = "Fajr begins at true dawn and ends at sunrise.",
                role = SidrTextRole.HUMAN_BODY,
            )
            SidrAssistantComposer(value = "when is fajr", onValueChange = {}, onSend = {})

            SidrSystemLabel("REFUSAL")
            SidrText(text = "I can't help with that.", role = SidrTextRole.HUMAN_BODY)
            SidrText(
                text = "The assistant declined to answer this one. Nothing went wrong; you can reword " +
                    "the message and send it again.",
                role = SidrTextRole.PROVENANCE,
            )

            SidrSystemLabel("RETRYABLE ERROR")
            SidrErrorSurface(
                title = "Assistant could not reply",
                whatFailed = "The reply did not finish.",
                why = "No network connection.",
                next = "Nothing was saved. Send the same message again when you are ready.",
                primaryAction = SidrSurfaceAction("Retry") {},
            )

            SidrSystemLabel("PROVIDER CTA ERROR")
            SidrErrorSurface(
                title = "Assistant could not reply",
                whatFailed = "The reply did not finish.",
                why = "API key rejected by provider. Update your settings.",
                next = "Open provider settings and check the base URL, model, and API key.",
                primaryAction = SidrSurfaceAction("Fix provider settings") {},
            )

            SidrSystemLabel("UNACTIONABLE ERROR")
            SidrErrorSurface(
                title = "Assistant could not reply",
                whatFailed = "The reply did not finish.",
                why = "Invalid request: check model / settings.",
                next = "Nothing was saved. Rewording the message usually helps.",
            )
        }
    }
}
