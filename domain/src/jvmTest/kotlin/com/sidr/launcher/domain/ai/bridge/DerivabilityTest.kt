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
