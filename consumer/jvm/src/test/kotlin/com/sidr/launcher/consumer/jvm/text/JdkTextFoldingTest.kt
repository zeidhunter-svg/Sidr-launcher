package com.sidr.launcher.consumer.jvm.text

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The JVM consumer's text folding (A4′ phase 3b, B-1): the shared character folding, then the JDK's
 * word segments, each cut again wherever neither a letter, a mark nor a digit stands. The six contract
 * rows are the same rows, by equality, as `PlainTextFoldingTest` (`:domain`) and `IcuTextFoldingTest`
 * (`:data:repository`). `:domain`'s jvmTest is not on this module's test classpath, so the rows are
 * restated here rather than shared.
 */
class JdkTextFoldingTest {

    @Test
    fun `folds the six contract rows`() {
        val wordRows = listOf(
            "İnstagram" to listOf("instagram"),
            "سِنْدَاف" to listOf("سنداف"),
            "أإآى" to listOf("اااي"),
            "Vemblic'i aç" to listOf("vemblici", "aç"),
            "orvelle.txt, 7.30!" to listOf("orvelle", "txt", "7", "30"),
        )
        for ((input, expected) in wordRows) {
            assertEquals("words of «$input»", expected, JdkTextFolding.words(input))
        }
        assertEquals("ه ي ك", JdkTextFolding.fold("ةـ ی ک"))
    }

    /** The JDK keeps «отчёт-2.txt» in fewer segments than words; the second cut is what separates them. */
    @Test
    fun `a Cyrillic file name keeps its digits`() {
        assertEquals(listOf("отчёт", "2", "txt"), JdkTextFolding.words("отчёт-2.txt"))
    }
}
