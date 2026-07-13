package com.sidr.launcher.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.theme.SidrTheme

@Composable
fun SidrForgetGate(
    title: String,
    consequence: String,
    onCancel: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
    evidence: String? = null,
    forgetting: Boolean = false,
) {
    SidrActionGate(
        type = SidrActionGateType.Destructive,
        title = title,
        consequence = consequence,
        confirmLabel = "Forget",
        onConfirm = onForget,
        onCancel = onCancel,
        modifier = modifier,
        reason = evidence,
        confirming = forgetting,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrForgetGatePreview() {
    SidrTheme(darkTheme = true) {
        SidrForgetGate(
            title = "Forget learned choice?",
            consequence = "\"open bank\" will no longer prefer Turkiye Finans.",
            evidence = "The next ambiguous request will ask you to choose again.",
            onCancel = {},
            onForget = {},
        )
    }
}
