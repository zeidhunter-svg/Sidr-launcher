package com.sidr.launcher

import com.sidr.launcher.data.ailocal.session.SessionLifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionLifecycleReleaseTest {

    @Test
    fun `releaseSessionResources releases every lifecycle holder`() {
        val first = CountingLifecycle()
        val second = CountingLifecycle()

        releaseSessionResources(listOf(first, second))

        assertEquals(1, first.releaseCalls)
        assertEquals(1, second.releaseCalls)
    }

    private class CountingLifecycle : SessionLifecycle {
        var releaseCalls = 0

        override fun releaseResources() {
            releaseCalls++
        }
    }
}
