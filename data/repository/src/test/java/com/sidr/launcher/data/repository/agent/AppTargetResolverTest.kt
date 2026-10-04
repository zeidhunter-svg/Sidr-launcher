package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun app(label: String, packageName: String) = InstalledApp(packageName = packageName, label = label)

private class FakeApps(private val apps: List<InstalledApp>) : InstalledAppsRepository {
    override suspend fun getInstalledApps(): OperationResult<List<InstalledApp>> = OperationResult.Success(apps)
}

private object FailingApps : InstalledAppsRepository {
    override suspend fun getInstalledApps(): OperationResult<List<InstalledApp>> =
        OperationResult.Failure(OperationError.UnknownError(reason = "boom"))
}

private object EmptyAliases : AliasStore {
    override suspend fun find(phrase: String): OperationResult<Alias?> = OperationResult.Success(null)
    override suspend fun upsert(alias: Alias): OperationResult<Unit> = OperationResult.Success(Unit)
    override suspend fun delete(phrase: String): OperationResult<Unit> = OperationResult.Success(Unit)
    override fun observeAll(): Flow<List<Alias>> = flowOf(emptyList())
}

/** Maps an already-normalized phrase to a package name — the test's own fixture, not production shape. */
private class FakeAliases(private val byNormalizedPhrase: Map<String, String>) : AliasStore {
    override suspend fun find(phrase: String): OperationResult<Alias?> {
        val pkg = byNormalizedPhrase[phrase] ?: return OperationResult.Success(null)
        return OperationResult.Success(Alias(phrase = phrase, target = AliasTarget.App(pkg), createdAtEpochMs = 0L))
    }
    override suspend fun upsert(alias: Alias): OperationResult<Unit> = OperationResult.Success(Unit)
    override suspend fun delete(phrase: String): OperationResult<Unit> = OperationResult.Success(Unit)
    override fun observeAll(): Flow<List<Alias>> = flowOf(emptyList())
}

/**
 * Task 6 (A1″ Phase 3a). `null` means decline, never a guess — this resolver feeds `uninstall_app`,
 * an irreversible act. See [AppTargetResolver]'s own KDoc for why learned resolutions are excluded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppTargetResolverTest {

    @Test
    fun `an exact label resolves`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(app("Telegram", "org.telegram.messenger"))), EmptyAliases)
        assertEquals("org.telegram.messenger", resolver.resolve("telegram"))
    }

    @Test
    fun `two apps with the same label decline rather than guess`() = runTest {
        val resolver = AppTargetResolver(
            FakeApps(listOf(app("Camera", "com.a.camera"), app("Camera", "com.b.camera"))),
            EmptyAliases,
        )
        assertNull(resolver.resolve("camera"))
    }

    @Test
    fun `no match declines`() = runTest {
        val resolver = AppTargetResolver(FakeApps(emptyList()), EmptyAliases)
        assertNull(resolver.resolve("telegram"))
    }

    @Test
    fun `a blank query declines`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(app("Telegram", "org.telegram.messenger"))), EmptyAliases)
        assertNull(resolver.resolve("   "))
    }

    @Test
    fun `an alias wins over a label and is matched on its normalized phrase`() = runTest {
        val resolver = AppTargetResolver(
            FakeApps(listOf(app("Telegram", "org.telegram.messenger"))),
            FakeAliases(mapOf("телега" to "org.telegram.messenger")),
        )
        assertEquals("org.telegram.messenger", resolver.resolve("  ТЕЛЕГА "))
    }

    @Test
    fun `an alias whose target is no longer installed declines`() = runTest {
        val resolver = AppTargetResolver(
            FakeApps(emptyList()),
            FakeAliases(mapOf("телега" to "org.telegram.messenger")),
        )
        assertNull(resolver.resolve("телега"))
    }

    @Test
    fun `a repository failure declines instead of throwing`() = runTest {
        val resolver = AppTargetResolver(FailingApps, EmptyAliases)
        assertNull(resolver.resolve("telegram"))
    }

    // --- candidates (A4′ phase 3a, spec §7.12.3 condition 2): the decline is no longer one shape ---

    @Test
    fun `two apps sharing a label are both candidates, never one guessed`() = runTest {
        val one = app("Tessar", "net.fixture.tessar.one")
        val two = app("Tessar", "net.fixture.tessar.two")
        val resolver = AppTargetResolver(FakeApps(listOf(one, two)), EmptyAliases)

        assertEquals(AppCandidates.Several(listOf(one, two)), resolver.candidates("tessar"))
    }

    @Test
    fun `an alias names exactly one candidate`() = runTest {
        val zantor = app("Zantor", "net.fixture.zantor")
        val resolver = AppTargetResolver(FakeApps(listOf(zantor)), FakeAliases(mapOf("кварцелот" to "net.fixture.zantor")))

        assertEquals(AppCandidates.One(zantor), resolver.candidates("Кварцелот"))
    }

    @Test
    fun `a repository failure is unavailable, not an empty answer`() = runTest {
        val resolver = AppTargetResolver(FailingApps, EmptyAliases)

        assertEquals(AppCandidates.Unavailable, resolver.candidates("tessar"))
    }
}
