package com.sidr.launcher.feature.suggestions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sidr.launcher.domain.suggestions.Suggestion

@Composable
fun SuggestionsRow(
    suggestions: List<Suggestion>,
    onSuggestionTap: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(
            items = suggestions,
            key = { suggestion -> suggestion.actionId },
        ) { suggestion ->
            AssistChip(
                onClick = { onSuggestionTap(suggestion) },
                label = {
                    Text(
                        text = suggestion.label,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
