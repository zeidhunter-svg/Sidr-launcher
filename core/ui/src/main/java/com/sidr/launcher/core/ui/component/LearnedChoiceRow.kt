package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing

/**
 * Terminal-style learned-choice row: `> query → label` followed by a state chip and a delete action.
 *
 * Presentation only: the caller supplies display-safe strings and callbacks. Keeping this in
 * `core/ui` avoids any `domain → ui` dependency; feature code will map domain state to [stateLabel].
 */
@Composable
fun LearnedChoiceRow(
    query: String,
    label: String,
    stateLabel: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .heightIn(min = Sizes.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "> $query → $label",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "[$stateLabel]",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "[ x ]",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(
                    onClickLabel = sidrString(R.string.ui_learned_choice_delete_action_label),
                    role = Role.Button,
                    onClick = onDelete,
                )
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = Spacing.sm),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF08090A)
@Composable
private fun LearnedChoiceRowPreview() {
    SidrTheme(darkTheme = true) {
        LearnedChoiceRow(
            query = "open docs",
            label = "Browser",
            stateLabel = "AUTO READY",
            onDelete = {},
        )
    }
}
