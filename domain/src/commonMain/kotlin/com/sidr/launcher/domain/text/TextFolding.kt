package com.sidr.launcher.domain.text

/**
 * One text-folding contract on every platform (A4′ phase 3b, B-1): what the device compares when it asks
 * "is this the same word?". Its first reader is the bridge's derivability check.
 *
 * [fold] is [ScriptFolding.foldChars], identical everywhere. [words] is the platform's own segments of the
 * folded text, each cut again wherever neither a letter, a mark nor a digit stands, empty pieces dropped:
 * the segmenter is what differs (a script written without spaces comes apart only as far as the platform's
 * dictionary knows its words), the second cut is what makes `orvelle.txt` and `7.30` two words on every
 * platform.
 *
 * Implementations: [PlainTextFolding] here (the whole text is one segment), `IcuTextFolding` on Android
 * (`:data:repository`), `JdkTextFolding` on the JVM consumer (`:consumer:jvm`). Transliteration is not
 * built: `android.icu.text.Transliterator` is API 29 and `minSdk` is 28.
 */
interface TextFolding {
    fun fold(text: String): String
    fun words(text: String): List<String>
}

/**
 * The character folding every [TextFolding] shares (B-1):
 *  - the invariant `lowercase()`;
 *  - combining marks dropped in **two ranges only** — U+0300–U+036F (Latin, Greek, Cyrillic; it holds the
 *    U+0307 that «İ».lowercase() leaves behind) and the Arabic harakat U+064B–U+065F and U+0670. Marks of
 *    other scripts are kept: a Thai or Devanagari vowel sign is part of the word, and dropping it makes
 *    another word;
 *  - Arabic-script letter variants mapped to one form — `أ إ آ ٱ → ا`, `ى → ي`, `ی → ي`, `ک → ك`,
 *    `ة → ه` — and the tatweel U+0640 dropped;
 *  - an apostrophe (`'` or `’`) between two letters dropped, so «Vemblic'i» is one word.
 *
 * It folds what it sees, character by character: a precomposed «ç» stays «ç», and only a combining mark in
 * the two ranges is removed. Nothing here normalises the text first.
 */
object ScriptFolding {

    fun foldChars(text: String): String {
        val kept = StringBuilder(text.length)
        for (c in text.lowercase()) {
            if (isDroppedMark(c) || c == TATWEEL) continue
            kept.append(LETTER_FORMS[c] ?: c)
        }
        return joinApostrophes(kept)
    }

    /** The second cut, applied to every platform's segments: wherever neither a letter, a mark nor a digit stands. */
    fun pieces(segment: String): List<String> = segment.split(SEPARATORS).filter { it.isNotEmpty() }

    private fun isDroppedMark(c: Char): Boolean =
        c in LATIN_GREEK_CYRILLIC_MARKS || c in ARABIC_HARAKAT || c == SUPERSCRIPT_ALEF

    private fun joinApostrophes(text: CharSequence): String {
        val joined = StringBuilder(text.length)
        for (i in text.indices) {
            val betweenLetters = i > 0 && i < text.length - 1 && text[i - 1].isLetter() && text[i + 1].isLetter()
            if (text[i] in APOSTROPHES && betweenLetters) continue
            joined.append(text[i])
        }
        return joined.toString()
    }

    private val LATIN_GREEK_CYRILLIC_MARKS = '\u0300'..'\u036F'
    private val ARABIC_HARAKAT = '\u064B'..'\u065F'
    private const val SUPERSCRIPT_ALEF = '\u0670'
    private const val TATWEEL = '\u0640'
    private val APOSTROPHES = setOf('\u0027', '\u2019')

    private const val ALEF = '\u0627'
    private const val YEH = '\u064A'
    private val LETTER_FORMS: Map<Char, Char> = mapOf(
        '\u0623' to ALEF, // alef with hamza above
        '\u0625' to ALEF, // alef with hamza below
        '\u0622' to ALEF, // alef with madda above
        '\u0671' to ALEF, // alef wasla
        '\u0649' to YEH, // alef maqsura
        '\u06CC' to YEH, // Farsi yeh
        '\u06A9' to '\u0643', // keheh to kaf
        '\u0629' to '\u0647', // ta marbuta to heh
    )

    private val SEPARATORS = Regex("[^\\p{L}\\p{M}\\p{N}]+")
}

/** The folding with no segmenter of its own: the whole text is one segment, then the second cut. */
object PlainTextFolding : TextFolding {
    override fun fold(text: String): String = ScriptFolding.foldChars(text)
    override fun words(text: String): List<String> = ScriptFolding.pieces(fold(text))
}
