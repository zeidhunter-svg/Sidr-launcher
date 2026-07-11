package com.sidr.launcher.core.ui.primitive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription

private const val SEP = " · "

internal fun provenanceDisplay(source: String, details: List<String>): String =
    (listOf(source) + details).filter { it.isNotBlank() }.joinToString(SEP) { it.trim().uppercase() }

internal fun provenanceDescription(source: String, details: List<String>): String {
    val segments = (listOf(source) + details).filter { it.isNotBlank() }.map { it.trim().uppercase() }
    return "source " + segments.joinToString(", ")
}

/**
 * DS-2 keystone (Amanah + Ilm): the single visual primitive for origin and truth —
 * `SOURCE · detail · detail` in faint mono, uppercased, wrapping (truth is never truncated). TalkBack reads
 * a composed sentence, not the raw glyphs. Contains no raw sensitive data; the CALLER omits it for ordinary
 * actions (grey spec §6.2, Master Plan §11 DS-2).
 */
@Composable
fun SidrProvenanceLine(
    source: String,
    details: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val description = provenanceDescription(source, details)
    SidrText(
        text = provenanceDisplay(source, details),
        role = SidrTextRole.PROVENANCE,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
}
