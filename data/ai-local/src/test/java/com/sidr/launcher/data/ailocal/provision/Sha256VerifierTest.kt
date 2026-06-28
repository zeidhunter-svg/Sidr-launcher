package com.sidr.launcher.data.ailocal.provision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Sha256VerifierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val verifier = Sha256Verifier()

    // Known NIST vector: SHA-256("abc").
    private val abcHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    private fun fileWith(text: String): File =
        File(tmp.root, "f").apply { writeBytes(text.toByteArray()) }

    @Test
    fun `sha256Hex matches the known vector for abc`() {
        assertEquals(abcHash, verifier.sha256Hex(fileWith("abc")))
    }

    @Test
    fun `verify accepts a matching hash case-insensitively`() {
        assertTrue(verifier.verify(fileWith("abc"), abcHash))
        assertTrue(verifier.verify(fileWith("abc"), abcHash.uppercase()))
    }

    @Test
    fun `verify rejects a mismatching hash`() {
        assertFalse(verifier.verify(fileWith("abc"), "0".repeat(64)))
    }

    @Test
    fun `verify fails closed on a blank expected hash or missing file`() {
        assertFalse(verifier.verify(fileWith("abc"), ""))
        assertFalse(verifier.verify(File(tmp.root, "absent"), abcHash))
    }
}
