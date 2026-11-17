package com.sidr.launcher.domain.text

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The text-folding contract (A4′ phase 3b, B-1) on the platform with no segmenter of its own: the
 * whole text is one segment, then cut wherever neither a letter, a mark nor a digit stands. The same
 * six contract rows are asserted, by equality, by `IcuTextFoldingTest` (`:data:repository`) and
 * `JdkTextFoldingTest` (`:consumer:jvm`) — one contract, three platforms.
 */
class PlainTextFoldingTest {

    /** On the JVM «İ».lowercase() is «i» + U+0307; the combining dot is dropped, so the word stays whole. */
    @Test
    fun `a capital dotted I folds to plain i and never splits the word`() {
        assertEquals(listOf("instagram"), PlainTextFolding.words("İnstagram"))
    }

    /**
     * The second row (phase 3b close, final review; deferred R2-m3) is U+0670, the superscript alef — dropped
     * by its own clause outside the harakat range, and asserted by no test until now: «هٰذا» folds to «هذا».
     * Written as escapes so the mark cannot vanish from the source unnoticed.
     */
    @Test
    fun `harakat are removed before words are split`() {
        assertEquals(listOf("سنداف"), PlainTextFolding.words("سِنْدَاف"))
        assertEquals(listOf("هذا"), PlainTextFolding.words("هٰذا"))
    }

    /** The hamza-carrying alefs and alef wasla are one alef; alef maqsura and Farsi yeh are yeh; keheh is kaf; ta marbuta is heh; tatweel is gone. */
    @Test
    fun `arabic-script letter variants fold to one form`() {
        assertEquals(listOf("اااي"), PlainTextFolding.words("أإآى"))
        assertEquals("ه ي ك", PlainTextFolding.fold("ةـ ی ک"))
        assertEquals("ا", PlainTextFolding.fold("ٱ"))
    }

    /**
     * An apostrophe — straight or typographic — between two letters joins them («Vemblic'i» is one
     * word); a dot or a comma between two words still cuts them (`orvelle.txt`, `7.30`).
     */
    @Test
    fun `an apostrophe inside a word joins it`() {
        assertEquals(listOf("vemblici", "aç"), PlainTextFolding.words("Vemblic'i aç"))
        assertEquals(listOf("vemblici"), PlainTextFolding.words("Vemblic’i"))
        assertEquals(listOf("orvelle", "txt", "7", "30"), PlainTextFolding.words("orvelle.txt, 7.30!"))
    }

    /** Only two mark ranges are dropped: a Thai vowel sign is part of the word, and stripping it would change the word. */
    @Test
    fun `marks of other scripts are kept`() {
        assertEquals("เปิด", PlainTextFolding.fold("เปิด"))
    }
}
