package com.sidr.launcher.data.repository.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sidr.launcher.data.repository.preferences.createTestDataStore
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKey
import com.sidr.launcher.domain.security.SecretKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM unit tests for [SecureSecretStoreImpl] using [FakeSecretCipher] — no Android Keystore. The real
 * [KeystoreSecretCipher] is verified separately on-device (`SecretStoreInstrumentedTest`). The
 * DataStore runs on a temp file (the Block E [createTestDataStore] helper), no Robolectric needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SecureSecretStoreImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val providerA: SecretKey = SecretKeys.apiKey(AiProviderId("cloud-default"))
    private val providerB: SecretKey = SecretKeys.apiKey(AiProviderId("cloud-compatible"))

    private fun file(): File = File(tmpFolder.root, "secrets.preferences_pb")

    private fun store(
        dataStore: DataStore<Preferences>,
        dispatcher: CoroutineDispatcher,
        cipher: SecretCipher = FakeSecretCipher(),
    ) = SecureSecretStoreImpl(dataStore, cipher, dispatcher)

    @Test
    fun `put then get round-trips the secret`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = store(createTestDataStore(file(), scope), dispatcher)

        assertTrue(repo.put(providerA, "sk-secret-value").let { it is OperationResult.Success })
        assertEquals(OperationResult.Success("sk-secret-value"), repo.get(providerA))
        scope.cancel()
    }

    @Test
    fun `get on an empty store returns null`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = store(createTestDataStore(file(), scope), dispatcher)

        assertEquals(OperationResult.Success(null), repo.get(providerA))
        scope.cancel()
    }

    @Test
    fun `two providers' secrets are isolated`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = store(createTestDataStore(file(), scope), dispatcher)

        repo.put(providerA, "value-A")
        repo.put(providerB, "value-B")

        assertEquals(OperationResult.Success("value-A"), repo.get(providerA))
        assertEquals(OperationResult.Success("value-B"), repo.get(providerB))
        scope.cancel()
    }

    @Test
    fun `remove of one provider leaves the other intact`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = store(createTestDataStore(file(), scope), dispatcher)

        repo.put(providerA, "value-A")
        repo.put(providerB, "value-B")

        assertTrue(repo.remove(providerA) is OperationResult.Success)

        assertEquals(OperationResult.Success(null), repo.get(providerA))
        assertEquals(OperationResult.Success("value-B"), repo.get(providerB))
        scope.cancel()
    }

    @Test
    fun `secret survives a simulated process restart`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()

        val writeScope = CoroutineScope(dispatcher + Job())
        store(createTestDataStore(target, writeScope), dispatcher).put(providerA, "persisted")
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = store(createTestDataStore(target, readScope), dispatcher)
        assertEquals(OperationResult.Success("persisted"), reopened.get(providerA))
        readScope.cancel()
    }

    @Test
    fun `key invalidation yields null and clears the entry`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val cipher = FakeSecretCipher()
        val repo = store(createTestDataStore(file(), scope), dispatcher, cipher)

        repo.put(providerA, "value-A")

        // Simulate KeyPermanentlyInvalidatedException / corrupt blob: decrypt now returns null.
        cipher.invalidate = true
        assertEquals(OperationResult.Success(null), repo.get(providerA))

        // The entry must have been cleared: a healthy cipher still sees nothing (re-enter required).
        cipher.invalidate = false
        assertEquals(OperationResult.Success(null), repo.get(providerA))
        scope.cancel()
    }

    @Test
    fun `a corrupt persisted blob yields null without crashing`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val cipher = FakeSecretCipher()
        val repo = store(createTestDataStore(file(), scope), dispatcher, cipher)

        // Persist a value, then make decrypt fail as if the stored bytes were unusable.
        repo.put(providerA, "value-A")
        cipher.invalidate = true

        val result = repo.get(providerA)
        assertNull((result as OperationResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `encrypt failure maps put to Failure`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val cipher = FakeSecretCipher().apply { failEncrypt = true }
        val repo = store(createTestDataStore(file(), scope), dispatcher, cipher)

        assertTrue(repo.put(providerA, "value-A") is OperationResult.Failure)
        scope.cancel()
    }

    @Test
    fun `put re-throws CancellationException`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val cancellingCipher = object : SecretCipher {
            override fun encrypt(plaintext: String): EncryptedBlob = throw CancellationException("cancelled")
            override fun decrypt(blob: EncryptedBlob): String? = null
        }
        val repo = store(createTestDataStore(file(), scope), dispatcher, cancellingCipher)

        assertThrows(CancellationException::class.java) {
            runBlockingPut(repo)
        }
        scope.cancel()
    }

    // Bridges the suspend put into the non-suspend assertThrows lambda on the test dispatcher.
    private fun runBlockingPut(repo: SecureSecretStoreImpl) =
        kotlinx.coroutines.runBlocking { repo.put(providerA, "value-A") }
}
