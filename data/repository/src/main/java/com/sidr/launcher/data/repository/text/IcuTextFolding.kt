package com.sidr.launcher.data.repository.text

import android.icu.text.BreakIterator
import android.icu.util.ULocale
import com.sidr.launcher.domain.text.ScriptFolding
import com.sidr.launcher.domain.text.TextFolding

/**
 * Android's [TextFolding] (A4′ phase 3b, B-1): the shared [ScriptFolding], then ICU's word segments of the
 * folded text — `BreakIterator.getWordInstance(ULocale.ROOT)`, which carries dictionaries for scripts
 * written without spaces — each cut again by [ScriptFolding.pieces]. So «打开设置» and «เปิดไลน์» come
 * apart into words, and `orvelle.txt` is two words here exactly as on every other platform.
 *
 * Named limit, measured under Robolectric (ICU 72.1, 2026-11-08; not seen on a phone): the segments are only
 * as good as ICU's dictionary. «打开设置» comes apart as [打开, 设置], but a brand the CJK dictionary does
 * not hold comes apart into single ideographs — «打开微信» as [打开, 微, 信].
 *
 * A `BreakIterator` is not thread-safe, so each call takes its own; ICU caches the rules and dictionaries
 * behind it. `android.icu` is API 24, below this app's `minSdk` 28.
 */
object IcuTextFolding : TextFolding {

    override fun fold(text: String): String = ScriptFolding.foldChars(text)

    override fun words(text: String): List<String> = segmentsOf(fold(text)).flatMap(ScriptFolding::pieces)

    private fun segmentsOf(text: String): List<String> {
        val breaks = BreakIterator.getWordInstance(ULocale.ROOT)
        breaks.setText(text)
        val segments = mutableListOf<String>()
        var start = breaks.first()
        var end = breaks.next()
        while (end != BreakIterator.DONE) {
            segments += text.substring(start, end)
            start = end
            end = breaks.next()
        }
        return segments
    }
}
