package com.sidr.launcher.domain.ai.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D1's first mechanism (spec §7.12.1): the model writes the user's words, and the device checks they
 * ARE the user's words — whole words, in order, any case. The base case only: word forms and
 * references to an earlier turn are 3b's (sorts) or `B16`'s, and they fail here as named limits.
 */
class DerivabilityTest {

    @Test
    fun `a whole-word fragment of the command is derivable, whatever its case`() {
        assertTrue(Derivability.isDerivable("Lumora", "открой мне LUMORA пожалуйста"))
        assertTrue(Derivability.isDerivable("tessar two", "запусти Tessar Two"))
    }

    @Test
    fun `part of a word is not derivable`() {
        assertFalse(Derivability.isDerivable("lum", "открой lumora"))
    }

    /** The limit spec §7.12.1 names for 3a: «телегу» is not «телега». An honest refusal, never a guess. */
    @Test
    fun `another form of the word is not derivable`() {
        assertFalse(Derivability.isDerivable("телега", "открой телегу"))
    }

    /**
     * On the JVM «İ».lowercase() is «i» + U+0307 (a combining dot, not a letter), which the word split
     * used to cut at: «İmvoral» became [i, mvoral]. A Turkish user's auto-capitalised word then failed
     * to match the model's plain «imvoral», and the fragment «mvoral» counted as the user's word.
     */
    @Test
    fun `a capital dotted I the user typed matches its plain lowercase form, and never splits the word`() {
        assertTrue(Derivability.isDerivable("imvoral", "aç İmvoral"))
        assertFalse(Derivability.isDerivable("mvoral", "aç İmvoral"))
    }

    /**
     * The Arabic mark defect (B-1): a harakah is a combining mark, not a letter, so the old split cut
     * «سِنْدَاف» into [س, ن, د, اف] and a model that wrote the vowelled form — or a user who typed it —
     * never matched the plain word. Folding drops the harakat before words are split.
     */
    @Test
    fun `a vowelled arabic mention is derivable from an unvowelled command`() {
        assertTrue(Derivability.isDerivable("سِنْدَاف", "افتح سنداف"))
        assertTrue(Derivability.isDerivable("سنداف", "افتح سِنْدَاف"))
    }

    /**
     * «Vemblic'i» is ONE word the user typed (Turkish accusative after an apostrophe), not «Vemblic» +
     * «i». Derivability stays exact (B-17): the user's whole form is derivable, the bare stem — another
     * form — is not, and neither is the suffix as if it were a word of its own.
     */
    @Test
    fun `an apostrophe form is one word for derivability`() {
        assertTrue(Derivability.isDerivable("Vemblic'i", "Vemblic'i aç"))
        assertTrue(Derivability.isDerivable("Vemblic’i", "Vemblic'i aç"))
        assertFalse(Derivability.isDerivable("Vemblic", "Vemblic'i aç"))
        assertFalse(Derivability.isDerivable("i", "Vemblic'i aç"))
    }

    @Test
    fun `a blank mention is not derivable`() {
        assertFalse(Derivability.isDerivable("  ", "открой lumora"))
    }

    @Test
    fun `a value the user typed is said by the user, anything else is the model's`() {
        assertEquals(
            ValueOrigin.SaidBy(PrincipalId.USER),
            Derivability.originOf("ostravel lectures", "найди ostravel lectures в интернете"),
        )
        assertEquals(
            ValueOrigin.ModelGenerated,
            Derivability.originOf("https://quillmere.example/path?q=a&b=c", "открой сайт quillmere"),
        )
    }
}
