package com.sidr.launcher.core.ui.primitive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.i18n.sidrString
import java.util.Locale

private const val SEP = " · "

/*
 * Locale.ROOT, not Locale.getDefault(), on both folds below (owner ruling, I18N-1 fix round: spec
 * §7.1 governs over the brief's Step 6 / spec §3.4).
 *
 * [source]/[details] are NOT human copy: every call site feeds this the §7.1-locked machine
 * vocabulary (`local only`, `memory`, `key in keystore`, `no provider configured`) plus host and
 * model ids, which §7.1 classes as data rather than copy. A Turkish default locale maps "i" to "İ",
 * so getDefault() would render `KEY İN KEYSTORE`, `NO PROVİDER CONFİGURED`, `OPENAİ/GPT-4O-MİNİ` and
 * `OPENROUTER.Aİ` — corrupting locked tokens on screen and in TalkBack. ROOT is also exactly the
 * fold Kotlin's no-arg `uppercase()` performed before I18N-1, so this stays behaviour-preserving.
 * Same call as SidrActionSafety.kt (risk/status chips) and SidrMemoryItem.kt (forget description).
 */
internal fun provenanceDisplay(source: String, details: List<String>): String =
    (listOf(source) + details)
        .filter { it.isNotBlank() }
        .joinToString(SEP) { it.trim().uppercase(Locale.ROOT) }

/**
 * The uppercased, comma-joined segment list the spoken description is built from. The leading
 * "source" word is a translated resource supplied by [SidrProvenanceLine] (I18N-1) — it is not
 * concatenated here, so the whole sentence stays one string per locale. The segments themselves are
 * locked vocabulary and ids, so they fold under [Locale.ROOT] (see the note above).
 */
internal fun provenanceSegments(source: String, details: List<String>): String =
    (listOf(source) + details)
        .filter { it.isNotBlank() }
        .joinToString(", ") { it.trim().uppercase(Locale.ROOT) }

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
