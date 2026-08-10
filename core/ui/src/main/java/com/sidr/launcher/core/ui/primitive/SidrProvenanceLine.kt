package com.sidr.launcher.core.ui.primitive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.i18n.sidrString
import java.util.Locale

private const val SEP = " · "

// DISPLAY: [source]/[details] are caller-supplied human copy, so they fold under the user's locale.
internal fun provenanceDisplay(source: String, details: List<String>): String =
    (listOf(source) + details)
        .filter { it.isNotBlank() }
        .joinToString(SEP) { it.trim().uppercase(Locale.getDefault()) }

/**
 * The uppercased, comma-joined segment list the spoken description is built from. The leading
 * "source" word is a translated resource supplied by [SidrProvenanceLine] (I18N-1) — it is not
 * concatenated here, so the whole sentence stays one string per locale.
 */
internal fun provenanceSegments(source: String, details: List<String>): String =
    (listOf(source) + details)
        .filter { it.isNotBlank() }
        .joinToString(", ") { it.trim().uppercase(Locale.getDefault()) }

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
    val description = sidrString(R.string.ui_provenance_content_description, provenanceSegments(source, details))
    SidrText(
        text = provenanceDisplay(source, details),
        role = SidrTextRole.PROVENANCE,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    )
}
