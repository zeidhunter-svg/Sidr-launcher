package com.sidr.launcher.domain.memory.resolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CandidateSetFingerprintTest {
    private fun app(p: String) = ResolvedTarget.App(p)

    @Test fun `fingerprint is order-independent`() {
        val a = CandidateSet(listOf(app("com.a"), app("com.b")))
        val b = CandidateSet(listOf(app("com.b"), app("com.a")))
        assertEquals(fingerprintOf(a), fingerprintOf(b))
    }

    @Test fun `fingerprint changes when the set changes`() {
        val a = CandidateSet(listOf(app("com.a"), app("com.b")))
        val c = CandidateSet(listOf(app("com.a"), app("com.c")))
        assertNotEquals(fingerprintOf(a), fingerprintOf(c))
    }

    @Test fun `fingerprint is stable across calls`() {
        val a = CandidateSet(listOf(app("com.a"), app("com.b")))
        assertEquals(fingerprintOf(a), fingerprintOf(a))
    }

    @Test fun `target id is type-prefixed (stable across ResolvedTarget growth)`() {
        assertEquals("app:com.a", targetId(ResolvedTarget.App("com.a")))
    }
}
