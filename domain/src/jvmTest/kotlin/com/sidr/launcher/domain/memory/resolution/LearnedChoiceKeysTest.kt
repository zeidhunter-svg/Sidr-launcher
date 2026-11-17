package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.text.PlainTextFolding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The one launch key (B-4) on folded WORDS (owner decision 2026-11-17, phase 3b close M5): the matchers compare
 * words, so the key does too — «Tessar.» and «tessar» are one choice, and «забудь выбор tessar» removes it.
 */
class LearnedChoiceKeysTest {

    private fun key(name: String) = LearnedChoiceKeys.launch(name, PlainTextFolding).query

    /** E4-F3: a name the words leave whole keys byte for byte what the character fold gave. */
    @Test
    fun `a plain name keys byte for byte as before`() {
        listOf("tessar", "Lumora Studio", "миравину", "Vemblic'i", "open my tessar app").forEach { name ->
            assertEquals(name, PlainTextFolding.fold(LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(name))), key(name))
        }
        assertEquals(CapabilityKey(ActionIds.LAUNCH_APP, "lumora studio"), LearnedChoiceKeys.launch("Lumora Studio", PlainTextFolding))
    }

    /** M5: a symbol is not part of a name, so it is not part of the key. The control: the fold alone keeps it. */
    @Test
    fun `a name and the same name with a symbol key the same`() {
        assertNotEquals("tessar", PlainTextFolding.fold("tessar."))
        assertEquals(key("tessar"), key("Tessar."))
        assertEquals(key("lumora"), key("Lumora+"))
        assertEquals("tessar", key("Tessar."))
    }

    /** A name with no word at all keeps its folded form — two different symbol names never share one empty key. */
    @Test
    fun `a name of symbols only keeps its folded form`() {
        assertEquals("★★", key("★★"))
        assertNotEquals(key("★★"), key("♥"))
    }
}
