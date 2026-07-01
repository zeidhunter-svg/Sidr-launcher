package com.sidr.launcher.core.android.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognizerAvailabilityTest {

    @Test
    fun `framework api available is enough`() {
        val snapshot = SpeechRecognizerAvailability.Snapshot(
            frameworkAvailable = true,
            onDeviceAvailable = false,
            hasRecognitionService = false,
            hasRecognizeSpeechActivity = false,
        )

        assertTrue(snapshot.isAvailable())
    }

    @Test
    fun `resolvable recognition service is a valid fallback`() {
        val snapshot = SpeechRecognizerAvailability.Snapshot(
            frameworkAvailable = false,
            onDeviceAvailable = false,
            hasRecognitionService = true,
            hasRecognizeSpeechActivity = false,
        )

        assertTrue(snapshot.isAvailable())
    }

    @Test
    fun `resolvable recognize speech activity is a valid fallback`() {
        val snapshot = SpeechRecognizerAvailability.Snapshot(
            frameworkAvailable = false,
            onDeviceAvailable = false,
            hasRecognitionService = false,
            hasRecognizeSpeechActivity = true,
        )

        assertTrue(snapshot.isAvailable())
    }

    @Test
    fun `no framework signal and no components means unavailable`() {
        val snapshot = SpeechRecognizerAvailability.Snapshot(
            frameworkAvailable = false,
            onDeviceAvailable = false,
            hasRecognitionService = false,
            hasRecognizeSpeechActivity = false,
        )

        assertFalse(snapshot.isAvailable())
    }
}
