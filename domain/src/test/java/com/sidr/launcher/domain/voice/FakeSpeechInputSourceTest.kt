package com.sidr.launcher.domain.voice

import com.sidr.launcher.core.testing.FakeSpeechInputSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the [SpeechInputSource] port form via [FakeSpeechInputSource] (Block S, S4/S6): the
 * `Ready → Partial* → Final → Ended` success run, the `Error` terminal-as-value (the stream completes
 * normally, no exception), and the unavailable degrade.
 */
class FakeSpeechInputSourceTest {

    @Test
    fun `success run emits Ready, partials, Final, Ended in order`() = runTest {
        val source = FakeSpeechInputSource()
        source.scriptSuccess(partials = listOf("op", "open cam"), finalText = "open camera")

        val states = source.listen("en-US").toList()

        assertEquals(
            listOf(
                SpeechRecognitionState.Ready,
                SpeechRecognitionState.Partial("op"),
                SpeechRecognitionState.Partial("open cam"),
                SpeechRecognitionState.Final("open camera"),
                SpeechRecognitionState.Ended,
            ),
            states,
        )
        assertEquals(listOf("en-US"), source.receivedLanguageTags)
    }

    @Test
    fun `error is a terminal value - stream completes without throwing`() = runTest {
        val source = FakeSpeechInputSource()
        source.scriptError(SpeechRecognitionError.NO_MATCH)

        // toList() returning normally proves the failure was a value, not a thrown exception.
        val states = source.listen(null).toList()

        assertEquals(
            SpeechRecognitionState.Error(SpeechRecognitionError.NO_MATCH),
            states.last(),
        )
        assertEquals(listOf<String?>(null), source.receivedLanguageTags)
    }

    @Test
    fun `availability flips with the fake flag`() {
        assertTrue(FakeSpeechInputSource().isAvailable())
        assertFalse(FakeSpeechInputSource(available = false).isAvailable())
    }
}
