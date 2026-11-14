package com.sidr.launcher.consumer.jvm.text

import com.sidr.launcher.domain.text.ScriptFolding
import com.sidr.launcher.domain.text.TextFolding
import java.text.BreakIterator
import java.util.Locale

/**
 * The JVM consumer's [TextFolding] (A4′ phase 3b, B-1): the shared [ScriptFolding], then the JDK's word
 * segments of the folded text — `java.text.BreakIterator.getWordInstance(Locale.ROOT)` — each cut again by
 * [ScriptFolding.pieces]. The JDK keeps `orvelle.txt` in one segment; the second cut is what makes it two
 * words here, as on every other platform. Nothing Android: `ModuleIsolationTest` holds that.
 *
 * A `BreakIterator` is not thread-safe, so each call takes its own.
 */
object JdkTextFolding : TextFolding {

    override fun fold(text: String): String = ScriptFolding.foldChars(text)

    override fun words(text: String): List<String> = segmentsOf(fold(text)).flatMap(ScriptFolding::pieces)

    private fun segmentsOf(text: String): List<String> {
        val breaks = BreakIterator.getWordInstance(Locale.ROOT)
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
