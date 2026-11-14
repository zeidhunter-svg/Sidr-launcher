package com.sidr.launcher.data.repository.text

import com.sidr.launcher.domain.text.PlainTextFolding
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android's text folding (A4′ phase 3b, B-1): the shared character folding, then ICU's word segments,
 * each cut again wherever neither a letter, a mark nor a digit stands. The six contract rows are the
 * same rows, by equality, as `PlainTextFoldingTest` (`:domain`) and `JdkTextFoldingTest`
 * (`:consumer:jvm`); the two segmentation tests are what ICU adds — a script written without spaces
 * still comes apart into words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IcuTextFoldingTest {

    @Test
    fun `folds the six contract rows exactly as the plain folding does`() {
        val wordRows = listOf(
            "İnstagram" to listOf("instagram"),
            "سِنْدَاف" to listOf("سنداف"),
            "أإآى" to listOf("اااي"),
            "Vemblic'i aç" to listOf("vemblici", "aç"),
            "orvelle.txt, 7.30!" to listOf("orvelle", "txt", "7", "30"),
        )
        for ((input, expected) in wordRows) {
            assertEquals("words of «$input»", expected, IcuTextFolding.words(input))
            assertEquals("words of «$input», against the plain folding", PlainTextFolding.words(input), IcuTextFolding.words(input))
        }
        assertEquals("ه ي ك", IcuTextFolding.fold("ةـ ی ک"))
        assertEquals(PlainTextFolding.fold("ةـ ی ک"), IcuTextFolding.fold("ةـ ی ک"))
    }

    /**
     * Named limit, measured 2026-11-08: Robolectric's ICU 72.1 has the CJK dictionary, but a name absent
     * from it (measured: «微信») segments into single characters — «打开微信» is [打开, 微, 信] — so a
     * whole-word match over [IcuTextFolding.words] cannot see such a name as one word.
     */
    @Test
    fun `segments a CJK command into words`() {
        assertEquals(listOf("打开", "设置"), IcuTextFolding.words("打开设置"))
    }

    @Test
    fun `segments Thai without spaces`() {
        assertEquals(listOf("เปิด", "ไลน์"), IcuTextFolding.words("เปิดไลน์"))
    }
}
