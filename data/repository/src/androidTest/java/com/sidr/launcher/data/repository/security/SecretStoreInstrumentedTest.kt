package com.sidr.launcher.data.repository.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sidr.launcher.domain.ai.AiProviderId
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.security.SecretKey
import com.sidr.launcher.domain.security.SecretKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented test for [SecureSecretStoreImpl] against the **real** [KeystoreSecretCipher] on device
 * (Robolectric/JVM cannot fake the Android Keystore). Run with:
 *
 *   ./gradlew :data:repository:connectedDebugAndroidTest
 *
 * (Requires a connected device/emulator — like the Phase-4 MigrationTest, not in the JVM CI pipeline.)
 *
 * Each [DataStore] is bound to its own scope so cancelling it releases the single-instance file lock,
 * letting a fresh store open on the same file to simulate a process restart.
 */
@RunWith(AndroidJUnit4::class)
class SecretStoreInstrumentedTest {

    private lateinit var context: Context
    private val fileName = "sidr_secrets_test"

    private val providerA: SecretKey = SecretKeys.apiKey(AiProviderId("cloud-default"))
    private val providerB: SecretKey = SecretKeys.apiKey(AiProviderId("cloud-compatible"))

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        secretsFile().delete()
    }

    @After
    fun tearDown() {
        secretsFile().delete()
    }

    private fun secretsFile(): File = context.preferencesDataStoreFile(fileName)

    private fun newDataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { context.preferencesDataStoreFile(fileName) },
        )

    private fun store(dataStore: DataStore<Preferences>) =
        SecureSecretStoreImpl(dataStore, KeystoreSecretCipher(), Dispatchers.IO)

    @Test
    fun put_thenRestart_get_returnsValue() = runBlocking {
        val writeScope = CoroutineScope(Dispatchers.IO + Job())
        store(newDataStore(writeScope)).put(providerA, "sk-real-keystore-value")
        writeScope.cancel()

        val readScope = CoroutineScope(Dispatchers.IO + Job())
        val result = store(newDataStore(readScope)).get(providerA)
        readScope.cancel()

        assertEquals(OperationResult.Success("sk-real-keystore-value"), result)
    }

    @Test
    fun twoProviders_areIsolated() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val repo = store(newDataStore(scope))

        repo.put(providerA, "value-A")
        repo.put(providerB, "value-B")

        assertEquals(OperationResult.Success("value-A"), repo.get(providerA))
        assertEquals(OperationResult.Success("value-B"), repo.get(providerB))

        repo.remove(providerA)
        assertEquals(OperationResult.Success(null), repo.get(providerA))
        assertEquals(OperationResult.Success("value-B"), repo.get(providerB))
        scope.cancel()
    }

    @Test
    fun strongBoxFallback_doesNotCrash_andRoundTrips() = runBlocking {
        // Exercises getOrCreateKey() — on devices without StrongBox the cipher falls back internally.
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val repo = store(newDataStore(scope))

        assertNull((repo.get(providerA) as OperationResult.Success).value)
        repo.put(providerA, "roundtrip")
        assertEquals(OperationResult.Success("roundtrip"), repo.get(providerA))
        scope.cancel()
    }
}
