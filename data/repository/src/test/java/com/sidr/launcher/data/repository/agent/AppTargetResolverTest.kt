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
        assertEquals(AppCandidates.One(zantor, foldedOnly = true), resolver.candidates("кварце\u0301лот"))
    }

    /**
     * Owner decision 2026-11-17: whether an exact answer is exact AS TYPED or only after folding is kept, so an
     * irreversible tool can ask instead of binding. «lumora» names «Lumora+» only after folding; «lumora+» names
     * it as typed; and the rule itself — a fold-only answer offered for an irreversible tool, every other answer
     * unchanged — is [forIrreversible]'s.
     */
    @Test
    fun `a name exact only after folding is marked, and one exact as typed is not`() = runTest {
        val lumoraPlus = app("Lumora+", "net.fixture.qelvarn")
        val resolver = AppTargetResolver(FakeApps(listOf(lumoraPlus)), EmptyAliases, PlainTextFolding)

        val folded = resolver.candidates("lumora")
        val typed = resolver.candidates("lumora+")

        assertEquals(AppCandidates.One(lumoraPlus, foldedOnly = true), folded)
        assertEquals(AppCandidates.One(lumoraPlus, foldedOnly = false), typed)
        assertEquals(AppCandidates.Confirm(listOf(lumoraPlus)), folded.forIrreversible(irreversible = true))
        assertEquals(folded, folded.forIrreversible(irreversible = false))
        assertEquals(typed, typed.forIrreversible(irreversible = true))
    }

    /** Spec 3b-G: «миравину» is a form of «Миравин» — a question for the user, so `resolve` (step 2b today) binds nothing. */
    @Test
    fun `an edge label match is a confirmation, never a bind`() = runTest {
        val miravin = app("Миравин", "net.fixture.miravin")
        val resolver = AppTargetResolver(FakeApps(listOf(zantor, miravin)), EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.Confirm(listOf(miravin)), resolver.candidates("миравину"))
        assertNull(resolver.resolve("миравину"))
    }

    /**
     * A4′ acceptance tail, task 3 (K10) — a regression pin of step 2b's own slot ([AppTargetResolver.windows], what
     * `ToolMatchPlanner` reads; the tests above go through [AppTargetResolver.candidates]). An alias is read before a
     * label there too — «кварцелот» is the alias of Zantor and the label of another app, and Zantor binds — and a FORM
     * of the alias is offered beside a form of the label, never bound: 2b feeds `uninstall_app`. FastPath's launch
     * binds a form of an alias since task 3 (`IntentActionResolver`); this slot does not, and task 3 leaves it so.
     */
    @Test
    fun `in step 2b's slot an alias wins over a label, and a form of it is offered`() = runTest {
        val other = app("Кварцелот", "net.fixture.other")
        val resolver = AppTargetResolver(FakeApps(listOf(zantor, other)), aliasedZantor(), PlainTextFolding)

        assertEquals(AppCandidates.One(zantor), resolver.windows("кварцелот"))
        assertEquals(AppCandidates.Confirm(listOf(zantor, other)), resolver.windows("кварцелота"))
    }

    // --- A4′ tail fix round, task 8 (owner ruling 4: а, iii): a name the MODEL wrote, by its package -------------

    private val maps = app("Карты", "com.google.android.apps.maps")

    /**
     * The cloud model cannot see the phone: it writes «Maps» where the Russian label is «Карты». The package is the
     * stable bridge — compared here, on the device, after the model answered. When no alias and no label answers
     * [AppTargetResolver.exactly], an installed app whose package's LAST segment is the folded name (spaces, `-` and
     * `_` removed) answers, marked fold-only. A label still answers first (the control).
     */
    @Test
    fun `a model name matching no label binds by the package's last segment`() = runTest {
        val whatsApp = app("Ватсап", "com.whatsapp")
        val claude = app("Клод", "com.anthropic.claude")
        val resolver = AppTargetResolver(FakeApps(listOf(maps, whatsApp, claude)), EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.One(maps, foldedOnly = true), resolver.exactly("Maps"))
        assertEquals(AppCandidates.One(whatsApp, foldedOnly = true), resolver.exactly("WhatsApp"))
        assertEquals(AppCandidates.One(whatsApp, foldedOnly = true), resolver.exactly("Whats-App"))
        assertEquals(AppCandidates.One(claude, foldedOnly = true), resolver.exactly("Claude"))

        val labelled = app("Maps", "net.fixture.orvane")
        assertEquals(
            "a label answers before any package",
            AppCandidates.One(labelled, foldedOnly = true),
            AppTargetResolver(FakeApps(listOf(maps, labelled)), EmptyAliases, PlainTextFolding).exactly("Maps"),
        )
    }

    /** Two installed packages end in the same segment: the user picks, nothing is guessed. */
    @Test
    fun `two packages with the same last segment are a pick`() = runTest {
        val one = app("Альфа", "com.a.app")
        val two = app("Бета", "com.b.app")
        val resolver = AppTargetResolver(FakeApps(listOf(one, two)), EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.Several(listOf(one, two)), resolver.exactly("App"))
    }

    /** Only the last segment answers a one-word name: «Telegram» is a middle segment of `org.telegram.messenger`. */
    @Test
    fun `a middle segment does not match`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(app("Телеграм", "org.telegram.messenger"))), EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.None, resolver.exactly("Telegram"))
    }

    /** The package is read for the MODEL's name only: the user's own «Maps» is answered by labels and aliases alone. */
    @Test
    fun `the user's own words never match by package`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(maps)), EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.None, resolver.candidates("Maps"))
        assertEquals(AppCandidates.None, resolver.windows("Maps"))
        assertNull(resolver.resolve("Maps"))
    }

    /**
     * Controller ruling (task 8): the model is told to write the app's usual published name, so «Google Maps» rather
     * than «Maps» — every word of it a segment of the package, and its last word the package's last segment.
     */
    @Test
    fun `a published name with the vendor binds by package segments`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(maps)), EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.One(maps, foldedOnly = true), resolver.exactly("Google Maps"))
    }

    /** «Yandex Maps» ends in `maps`, but `yandex` is no segment of Google's package: not that app. */
    @Test
    fun `a vendor the package does not carry does not match`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(maps)), EmptyAliases, PlainTextFolding)

        assertEquals(AppCandidates.None, resolver.exactly("Yandex Maps"))
        assertEquals("the vendor must not stand last either", AppCandidates.None, resolver.exactly("Maps Google"))
    }
}
