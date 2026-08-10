package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Centered error surface with an optional retry action. When [onRetry] is null the button is
 * omitted (non-retryable errors), mirroring the `UiState.Error(retryable)` contract without
 * depending on it — [ErrorState] stays a pure presentation primitive.
 */
@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String = sidrString(R.string.ui_action_retry),
    onRetry: (() -> Unit)? = null,
) {
    SidrErrorSurface(
        title = sidrString(R.string.ui_error_state_title),
        whatFailed = message,
        next = if (onRetry != null) sidrString(R.string.ui_error_state_next) else null,
        primaryAction = onRetry?.let { SidrSurfaceAction(retryLabel, it) },
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.xl),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun ErrorStatePreview() {
    SidrTheme(darkTheme = true) {
        ErrorState(
            message = "Couldn't load your apps.",
            onRetry = {},
        )
    }
}
