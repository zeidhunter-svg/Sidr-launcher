package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * The unified search / command field — the single text entry point on the redesigned home and in
 * the App Drawer. It looks like search (leading magnifier, rounded), but its submit action drives
 * the existing command pipeline, so one field does both jobs (fork U6). This component is pure
 * presentation: filtering vs. command dispatch is the caller's decision in [onSubmit]/[onValueChange].
 *
 * Trailing affordance precedence:
 *  1. a **Clear** button whenever [value] is non-empty (quick reset while filtering), else
 *  2. a **Mic** button when [showMic] is true (voice input available), else nothing.
 *
 * @param onSubmit invoked with the current [value] when the user presses the IME action.
 * @param showMic show the mic affordance (caller gates this on recognizer availability).
 * @param onMic invoked when the mic is tapped (caller starts recognition / routes to education).
 */
@Composable
fun SidrSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search or type a command…",
    showMic: Boolean = false,
    onMic: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            when {
                value.isNotEmpty() -> IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Clear",
                    )
                }
                showMic -> IconButton(onClick = onMic) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic_24),
                        contentDescription = "Voice input",
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit(value) }),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun SidrSearchFieldEmptyPreview() {
    SidrTheme(darkTheme = true) {
        SidrSearchField(
            value = "",
            onValueChange = {},
            onSubmit = {},
            showMic = true,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun SidrSearchFieldTypedPreview() {
    SidrTheme(darkTheme = true) {
        SidrSearchField(
            value = "open telegram",
            onValueChange = {},
            onSubmit = {},
            showMic = true,
        )
    }
}
