package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.text.PlainTextFolding
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
        val resolver = AppTargetResolver(FakeApps(listOf(app("Telegram", "org.telegram.messenger"))), EmptyAliases, PlainTextFolding)
        assertEquals("org.telegram.messenger", resolver.resolve("telegram"))
    }

    @Test
    fun `two apps with the same label decline rather than guess`() = runTest {
        val resolver = AppTargetResolver(
            FakeApps(listOf(app("Camera", "com.a.camera"), app("Camera", "com.b.camera"))),
            EmptyAliases,
            PlainTextFolding,
        )
        assertNull(resolver.resolve("camera"))
    }

    @Test
    fun `no match declines`() = runTest {
        val resolver = AppTargetResolver(FakeApps(emptyList()), EmptyAliases, PlainTextFolding)
        assertNull(resolver.resolve("telegram"))
    }

    @Test
    fun `a blank query declines`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(app("Telegram", "org.telegram.messenger"))), EmptyAliases, PlainTextFolding)
        assertNull(resolver.resolve("   "))
    }

    @Test
    fun `an alias wins over a label and is matched on its normalized phrase`() = runTest {
        val resolver = AppTargetResolver(
            FakeApps(listOf(app("Telegram", "org.telegram.messenger"))),
            FakeAliases(mapOf("телега" to "org.telegram.messenger")),
            PlainTextFolding,
        )
        assertEquals("org.telegram.messenger", resolver.resolve("  ТЕЛЕГА "))
    }

    @Test
    fun `an alias whose target is no longer installed declines`() = runTest {
        val resolver = AppTargetResolver(
            FakeApps(emptyList()),
            FakeAliases(mapOf("телега" to "org.telegram.messenger")),
            PlainTextFolding,
        )
        assertNull(resolver.resolve("телега"))
    }

    @Test
    fun `a repository failure declines instead of throwing`() = runTest {
        val resolver = AppTargetResolver(FailingApps, EmptyAliases, PlainTextFolding)
        assertNull(resolver.resolve("telegram"))
    }

    // --- candidates (A4′ phase 3a, spec §7.12.3 condition 2): the decline is no longer one shape ---

    @Test
    fun `two apps sharing a label are both candidates, never one guessed`() = runTest {
        val one = app("Tessar", "net.fixture.tessar.one")
        val two = app("Tessar", "net.fixture.tessar.two")
        val resolver = AppTargetResolver(FakeApps(listOf(one, two)), EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.Several(listOf(one, two)), resolver.candidates("tessar"))
    }

    @Test
    fun `an alias names exactly one candidate`() = runTest {
        val zantor = app("Zantor", "net.fixture.zantor")
        val resolver = AppTargetResolver(FakeApps(listOf(zantor)), FakeAliases(mapOf("кварцелот" to "net.fixture.zantor")), PlainTextFolding)

        assertEquals(AppCandidates.One(zantor), resolver.candidates("Кварцелот"))
    }

    /**
     * Both reads the answer rests on. The app list failing is the first half. The second half is the
     * alias read failing while the apps read fine — task 4 review I1, ruling E4-F1. An alias may name a
     * different app than the label does, so answering from the label would be a guess on an irreversible
     * `uninstall_app`. The pre-phase-3b fall-through would answer `One(zantor)` here through the label, so
     * this half tells the two shapes apart. `FakeAliasStore.failReads` fails `find` only; its `observeAll`
     * still answers, so the read that decides is the one under test.
     */
    @Test
    fun `a repository failure is unavailable, not an empty answer`() = runTest {
        val resolver = AppTargetResolver(FailingApps, EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.Unavailable, resolver.candidates("tessar"))

        val aliasesFail = AppTargetResolver(FakeApps(listOf(zantor)), FakeAliasStore(failReads = true), PlainTextFolding)

        assertEquals(AppCandidates.Unavailable, aliasesFail.candidates("zantor"))
        assertNull(aliasesFail.resolve("zantor"))
    }

    // --- word forms (A4′ phase 3b, fork 3b-G; plan §0.2 B-2): a form is offered, never bound ---

    private val zantor = app("Zantor", "net.fixture.zantor")

    /** The store the production path reads: `find` for the stored phrase, `observeAll` for its forms. */
    private fun aliasedZantor(): FakeAliasStore = FakeAliasStore().apply {
        runBlocking { upsert(Alias("кварцелот", AliasTarget.App("net.fixture.zantor"), createdAtEpochMs = 0L)) }
    }

    @Test
    fun `an alias in another form is offered for confirmation`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(zantor)), aliasedZantor(), PlainTextFolding)

        assertEquals(AppCandidates.Confirm(listOf(zantor)), resolver.candidates("кварцелота"))
        assertNull(resolver.resolve("кварцелота"))
    }

    /**
     * The stored phrase binds at once, and so does the same phrase under folding: a stress mark (U+0301)
     * is not in the stored key, so `find` misses it, and the folded comparison over every alias is exact.
     */
    @Test
    fun `an exact alias still binds without asking`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(zantor)), aliasedZantor(), PlainTextFolding)

        assertEquals(AppCandidates.One(zantor), resolver.candidates("кварцелот"))
        assertEquals(AppCandidates.One(zantor), resolver.candidates("кварце\u0301лот"))
    }

    /** Spec 3b-G: «миравину» is a form of «Миравин» — a question for the user, so `resolve` (step 2b today) binds nothing. */
    @Test
    fun `an edge label match is a confirmation, never a bind`() = runTest {
        val miravin = app("Миравин", "net.fixture.miravin")
        val resolver = AppTargetResolver(FakeApps(listOf(zantor, miravin)), EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.Confirm(listOf(miravin)), resolver.candidates("миравину"))
        assertNull(resolver.resolve("миравину"))
    }
}
