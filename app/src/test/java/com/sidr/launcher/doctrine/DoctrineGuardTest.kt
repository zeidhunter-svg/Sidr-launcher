package com.sidr.launcher.doctrine

import android.content.Intent
import com.sidr.launcher.agent.stripComments
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.IntentLauncher
import com.sidr.launcher.data.repository.agent.PermissionPresence
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.SystemIntentToolWorker
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolWorker
import com.sidr.launcher.data.repository.agent.Tier0ToolIds
import com.sidr.launcher.data.repository.agent.ToolPermissionCatalog
import com.sidr.launcher.data.repository.agent.shortcut.AppShortcut
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutLauncher
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolIds
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolWorker
import com.sidr.launcher.di.AgentProvidesModule
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The block's doctrine test. **Eight** properties — six since A1′, two added by A1″'s fix round 1 — and
 * a green run of a new guard proves nothing on its own, which is why what has and has not been proved
 * is spelled out below rather than summarised.
 *
 * **Verification status, stated per property rather than as a blanket claim** (fix round 2, finding A —
 * this paragraph replaced a headline that said "each mutation-proved separately" and was, for two of
 * the eight, false):
 *  - **The six A1′ properties are mutation-proved by the prover round**, which is what the mutations
 *    named throughout this file (M1, M6, the two adapter-spelling evasions, the "limit 1" finding) are.
 *    Each is recorded at the assertion it belongs to.
 *  - **The two A1″ fix-round-1 properties are not.**
 *    [`the graph's own federation reaches exactly the declared tool levels`] and
 *    [`every tool the graph's own federation reaches has its risk pinned here`] were checked by their
 *    **author**, by removing the third `ToolAdapter` from the real `AgentProvidesModule` and observing
 *    both go red. That is evidence and it is recorded as such; it is **not** the prover round, which
 *    plants mutations the author did not think of and is the only thing this repository counts as
 *    "mutation-proved". It is still outstanding — item 7(a) of
 *    `docs/superpowers/plans/2026-09-12-a1-second-tool-mass-and-selection.md`.
 *
 * It deliberately builds the **production** federation rather than a fixture: a guard that checks a
 * fixture checks the fixture. Two of the eight go further and call the real composition root itself —
 * see [graphFederation] for why a replica and a regex could not.
 *
 * Three of the eight assertions ([`the composition root registers exactly the declared adapters`] and
 * [`the composition root's planner list matches the declared planners`] are textual scans of
 * `AgentProvidesModule.kt`, and the surface-label assertion is a textual scan of
 * `AgentSessionPresentation.kt`) — the established idiom `ToolExecutorCallSiteGuardTest` /
 * `ToolWorkerCallSiteGuardTest` use for a property one module cannot check by calling into another
 * (Kotlin's `internal` is per-module; both source files sit in modules `:app`'s test source set cannot
 * call into even though it compiles against them). [stripComments] is reused from
 * `com.sidr.launcher.agent`, the same helper the two call-site guards share, so a KDoc that merely
 * mentions a name in prose cannot satisfy any of these checks.
 *
 * **A1′ fix round 1 (mutation-prover, two findings)** — the prover round referred to above; not to be
 * confused with A1″'s own fix round 1, which added the two graph tests. Both findings are the same
 * family this block keeps catching —
 * an assertion that cannot see the failure it claims to guard:
 *  1. `no two registered tools share an id` used to read `productionFederation().registry.all()`, the
 *     federation's own **already-deduplicated** output, so a planted duplicate could never reach it —
 *     see that test's own KDoc for the fix and why it now reads the adapters directly.
 *  2. Every test that loops over registered tools shares [productionAdapters], and an adapter that
 *     silently contributes zero (or fewer) tools used to make all of them pass having checked less than
 *     the real federation — the same vacuous-loop shape `ToolVocabularyLocaleGuardTest.guardedEntries`
 *     (`:data:repository`) was already fixed for. [productionAdapters] now asserts the same kind of
 *     floor that fix uses: *containment* of the ids A1′ shipped, not equality — see its own KDoc.
 */
class DoctrineGuardTest {

    /**
     * Task 2 made `Tier0IntentToolSource` filter by held permission, so every construction of it now
     * states which presence it is read under. **This one is load-bearing, not a convenience.** The
     * tests in this file quantify over the registry's *contents*; a fixture that withheld a tool would
     * let them pass by **absence** — they would loop over a list the filter had already emptied and
     * assert nothing. Granting everything is what keeps them strict. A fixture granting nothing
     * belongs only where the subject *is* the filter: `Tier0IntentToolSourceTest` and
     * `Tier0IntentToolWorkerTest`.
     */
    private val grantsEverything = PermissionPresence { true }

    private val repoRoot = File("..")

    /**
     * Defined here rather than reused: `ToolMatchPlannerTest`'s `NoopWorker` is private to
     * `:data:repository`'s test source set, and a guard that borrows across modules is a guard that
     * breaks when someone tidies the other module.
     */
    private object NoopWorker : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = ToolResult.Effected()
    }

    /**
     * The production adapter list, read fresh on every call. **Every test in this class that loops over
     * registered tools reads from here** — directly (`no two registered tools share an id` and
     * `every registered tool's declared risk is pinned here`) or transitively through
     * [productionFederation] (the surface-label test) — which is what makes the floor below
     * load-bearing for all three instead of one extra `@Test` bolted on beside them.
     *
     * Both direct readers want what the adapters **declare**, before [ToolFederation] deduplicates:
     * the id test because a duplicate is resolved away before `.all()` returns (see its own KDoc), and
     * the risk test because a second adapter redeclaring an existing id at a different risk would
     * vanish the same way. Read post-dedup, both would be checks of the federation's own output rather
     * than of what the sources say.
     *
     * The floor is asserted **here**, in the shared accessor, not in its own test method — same
     * placement `ToolVocabularyLocaleGuardTest.guardedEntries()` uses and for the same stated reason: a
     * guard that loops over an empty or shrunk table passes having checked nothing, which is this
     * repository's own recorded failure mode (that guard's KDoc calls out the identical shape). Proved
     * directly here: the mutation-prover's M6 zeroed `Tier0IntentToolSource.all()`, and against the old
     * (floorless) body every test in this class kept passing while `set_timer`/`open_system_settings`
     * silently stopped being checked by anything.
     *
     * The floor is **containment** of the four ids A1′ actually shipped — not equality, and not bare
     * non-emptiness — mirroring `ToolVocabularyLocaleGuardTest`'s own choice for the same reason: bare
     * non-emptiness under-pins (an adapter that keeps one tool and silently drops `set_timer` still
     * passes), while equality would redden the moment a legitimate tool is added, which `A1"` is
     * expected to do. [REQUIRED_TOOL_IDS] names nothing about a tool beyond its id — not risk, schema,
     * or label — only that the id is still present somewhere in the federation.
     */
    private fun productionAdapters(): List<ToolAdapter> {
        val adapters = listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), NoopWorker),
            ToolAdapter(
                ToolLevels.SYSTEM_INTENT,
                Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything),
                NoopWorker,
            ),
            ToolAdapter(ToolLevels.APP_SHORTCUT, shortcutSource(), NoopWorker),
        )

        val ids = adapters.flatMap { it.registry.all() }.map { it.id }
        val missing = REQUIRED_TOOL_IDS.filterNot { it in ids }
        assertEquals(
            "An adapter that contributes fewer tools than it ships makes every test in this class pass " +
                "having checked less than the real federation. Missing: ${missing.map { it.value }} " +
                "(adapters currently declare: ${ids.map { it.value }})",
            emptyList<ToolId>(),
            missing,
        )

        assertEquals(
            "The app_shortcut adapter declared no tool, so every loop in this class skipped the whole " +
                "third adapter in silence — the same vacuous-loop false GREEN [REQUIRED_TOOL_IDS] " +
                "exists to prevent, which cannot see this one because a shortcut id is device-" +
                "dependent and therefore un-nameable. Adapters currently declare: ${ids.map { it.value }}",
            true,
            ids.any { it.value.startsWith(ShortcutToolIds.PREFIX) },
        )

        return adapters
    }

    /**
     * **The third adapter, over a deliberately NON-EMPTY fake snapshot** (controller ruling).
     *
     * A shortcut source built over `query = { emptyList() }` advertises zero descriptors, every loop in
     * this class skips it in silence, and the guard reports GREEN while blind to the entire adapter. That
     * is the identical false GREEN this file's own mutation rounds caught twice before (see
     * [productionAdapters] and the risk test's "limit 1"), pointed at a new adapter — and the floor
     * [REQUIRED_TOOL_IDS] provides for the other two cannot catch it, because a real shortcut id depends
     * on which apps the device has installed and so cannot be written down.
     *
     * The catalog is the **real** [ShortcutCatalog] over a fake [com.sidr.launcher.data.repository.agent.shortcut.ShortcutQuery]
     * seam, not a stub registry: it is the same object production wires, so the id derivation, the
     * round-trip filter and the descriptor fields under test here are the production ones.
     */
    private fun shortcutSource(): ShortcutToolSource {
        val catalog = ShortcutCatalog(
            query = {
                listOf(
                    AppShortcut(
                        packageName = "com.example.chat",
                        shortcutId = "new_message",
                        appLabel = "Example Chat",
                        shortcutLabel = "New message",
                    ),
                )
            },
            ioDispatcher = Dispatchers.Unconfined,
        )
        runBlocking { catalog.refresh() }
        return ShortcutToolSource(catalog)
    }

    /**
     * **The real graph's own federation — `AgentProvidesModule.provideToolFederation` itself, not a
     * replica of it.**
     *
     * This is the repair two KDocs in this file addressed to "the task that adds adapter #3" and that
     * the adapter-#3 commit did not make (fix round 1, finding 1). [productionAdapters] is a
     * hand-maintained copy of the composition root's list; the only thing coupling it to the real
     * module is a **regex over one file**, and that scan was measured evading two different legal
     * spellings before it was tightened. A regex can always be evaded — by `copy(registry = …)`, by
     * `listOf(…) + adaptersBuiltElsewhere`, by a second `@Module` in `:app`. Calling the provider
     * cannot: whatever the module returns is what the graph gets.
     *
     * `AgentProvidesModule` is a Kotlin `object`, so the provider is an ordinary function call from
     * here. Its six parameters are the concrete source and worker types, not ports, so each one is
     * really constructed — the sources with the same real inputs [productionAdapters] uses, the workers
     * over trivial leaf fakes ([NoopIntentLauncher] and friends). **The workers are irrelevant to every
     * assertion below, and that is not a weakness:** what is read is `registry.all()`, whose value is
     * decided entirely by the three sources. A worker fake cannot make a level appear or a risk change.
     *
     * What this reads is the federation's **post-dedup** output, which is the opposite reading from
     * [productionAdapters] and the reason both survive rather than one replacing the other:
     *  - post-dedup (here) is the only reading that can see **a whole adapter arriving or vanishing**,
     *    because it is the graph's own list;
     *  - pre-dedup ([productionAdapters]) is the only reading that can see **a duplicate id declared by
     *    two adapters**, because `ToolFederation` resolves those away before `all()` returns.
     */
    private fun graphFederation(): ToolFederation = AgentProvidesModule.provideToolFederation(
        inAppRegistry = SystemIntentToolSource(DefaultActionCatalog()),
        inAppWorker = SystemIntentToolWorker(
            ExecuteActionUseCase(
                resolver = IntentActionResolver(NoAppsRepository),
                executor = NoopActionExecutor,
            ),
        ),
        tier0Registry = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything),
        tier0Worker = Tier0IntentToolWorker(NoopIntentLauncher, ToolPermissionCatalog(), grantsEverything),
        shortcutRegistry = shortcutSource(),
        shortcutWorker = ShortcutToolWorker(ShortcutLauncher { _, _ -> }),
    )

    private object NoAppsRepository : InstalledAppsRepository {
        override suspend fun getInstalledApps(): OperationResult<List<InstalledApp>> =
            OperationResult.Success(emptyList())
    }

    private object NoopActionExecutor : ActionExecutor {
        override suspend fun execute(action: ExecutableAction): ActionExecutionResult =
            ActionExecutionResult.Success
    }

    private object NoopIntentLauncher : IntentLauncher {
        override fun launch(intent: Intent) = Unit
    }

    /**
     * **Adapter arrival and disappearance, pinned where no spelling can evade it.**
     *
     * `the composition root registers exactly the declared adapters` pins the same list textually, and
     * both are kept because they fail on different things. The scan sees the **order** — first-adapter-
     * wins precedence is an ordering decision, and a post-dedup set cannot express it — and it sees an
     * adapter written in a spelling its own regex cannot parse (it reddens on the count mismatch). This
     * test sees what the graph actually **reaches**: a level that arrives or stops arriving, however it
     * was wired, and an adapter that is wired but whose registry contributes nothing.
     *
     * The distinct-level reading is what makes this assertable at all — and it is also this test's
     * boundary, stated rather than implied (fix round 2, finding D). A shortcut tool's id is
     * device-dependent and cannot be written down, so the tools themselves cannot be listed; the
     * **levels** can, and each adapter today contributes exactly one. The cost of reading them
     * `distinct()` is that **a fourth adapter wired at a level already present is invisible here** —
     * that case belongs to the count-pin in
     * [`the composition root registers exactly the declared adapters`], which is one more reason the
     * two tests are kept side by side rather than one replacing the other.
     */
    @Test
    fun `the graph's own federation reaches exactly the declared tool levels`() {
        val levels: List<ToolLevel> = graphFederation().registry.all().map { it.level }.distinct()

        assertEquals(
            "This reads AgentProvidesModule.provideToolFederation itself, so an adapter that brings a " +
                "NEW level, or stops reaching one it used to, shows up here whatever spelling wired it " +
                "— which a regex over one file cannot promise. What it does NOT see, because the read " +
                "is distinct levels: a further adapter wired at a level already present. That case is " +
                "the count-pin's, in `the composition root registers exactly the declared adapters`. " +
                "A new adapter is a new path to the world and needs an ADR, not a line. " +
                "Reached: ${levels.map { it.value }}",
            listOf(ToolLevels.IN_APP, ToolLevels.SYSTEM_INTENT, ToolLevels.APP_SHORTCUT),
            levels,
        )
    }

    /**
     * Risk totality over **the graph**, which is the half [declaredRisk]'s own KDoc names as missing:
     * pinning over [productionAdapters] cannot detect a new adapter's arrival at all, because that list
     * is a literal in this file. Measured before this test existed — a third adapter declaring an
     * `EXTERNAL`/`CONFIRM`/`DURABLE` tool with no row here left all 54 `:app` tests green.
     *
     * Same two pins as the per-adapter test, over the graph's own output: an authored tool must have a
     * row in [declaredRisk] and must still declare it; a dynamic tool must sit at [DYNAMIC_TOOL_RISK].
     * The non-vacuity floor is asserted here too rather than borrowed — a federation reaching no
     * shortcut tool would make the family half of this quantify over nothing.
     */
    @Test
    fun `every tool the graph's own federation reaches has its risk pinned here`() {
        val reached = graphFederation().registry.all()
        val (dynamic, authored) = reached.partition { it.id.value.startsWith(ShortcutToolIds.PREFIX) }

        val unpinned = authored.map { it.id }.filterNot { it in declaredRisk }
        assertEquals(
            "A tool the REAL graph registers, whose risk is pinned by nothing here, can change gate " +
                "behaviour silently — and a whole adapter arriving is exactly how that happens without " +
                "anyone touching this file: ${unpinned.map { it.value }}",
            emptyList<ToolId>(),
            unpinned,
        )

        val drifted = authored
            .filter { declaredRisk[it.id] != it.risk }
            .map { "${it.id.value}: pinned ${declaredRisk[it.id]}, declared ${it.risk}" }
        assertEquals(
            "Declared risk changed without this pin changing with it: $drifted",
            emptyList<String>(),
            drifted,
        )

        assertEquals(
            "The graph's federation reached no app_shortcut tool, so the family pin below checked " +
                "nothing — the same vacuous-loop false GREEN this file has caught twice.",
            true,
            dynamic.isNotEmpty(),
        )
        val dynamicDrift = dynamic
            .filterNot { it.risk == DYNAMIC_TOOL_RISK }
            .map { "${it.id.value}: family-pinned $DYNAMIC_TOOL_RISK, declared ${it.risk}" }
        assertEquals(
            "One rating for the whole app_shortcut family, and it must hold for every member the graph " +
                "actually reaches. Drifted: $dynamicDrift",
            emptyList<String>(),
            dynamicDrift,
        )
    }

    private fun productionFederation() = ToolFederation(productionAdapters())

    /**
     * `DOC-ILM-2` half one — no tool is silently shadowed, so provenance names a real supplier.
     *
     * **Fix round 1 (mutation-prover finding 1).** This used to read
     * `productionFederation().registry.all()` — the federation's own output — which cannot see this
     * property: `ToolFederation` resolves a duplicate id first-adapter-wins **before** `.all()` returns
     * (its own KDoc: "Collision policy: first adapter wins... dropped from the later adapter, in both
     * faces"), so a planted duplicate is removed by the object under test before the assertion ever
     * gets a look at it — the very property being checked was guaranteed by construction on the read
     * path used to check it. Confirmed: the mutation-prover's M1 (`Tier0ToolIds.SET_TIMER` renamed to
     * claim `"launch_app"`, a projected id already in the registry) left the old body GREEN.
     *
     * The fix reads each adapter's own `registry.all()` — what the sources actually **declare**, before
     * [ToolFederation] ever sees them — via [productionAdapters], and flat-maps across adapters with no
     * dedup step of its own. A duplicate declared by two adapters now survives into [ids]: under M1,
     * `"launch_app"` appears twice (once from `SystemIntentToolSource`, once from the mutated
     * `Tier0IntentToolSource`), `ids.distinct() != ids`, and this assertion goes red.
     */
    @Test
    fun `no two registered tools share an id`() {
        val ids = productionAdapters().flatMap { it.registry.all() }.map { it.id.value }

        assertEquals(
            "A shadowed tool is a capability that exists and can never run. Duplicates: " +
                ids.groupingBy { it }.eachCount().filterValues { it > 1 },
            ids.distinct(),
            ids,
        )
    }

    /**
     * `DOC-ILM-2` half two. **Controller ruling R20 replaces the brief's original assertion here.** The
     * brief's text asserted `descriptor.level.value.isNotBlank()`, and that cannot fail: [ToolLevel][
     * com.sidr.launcher.domain.tool.ToolLevel] is a required non-null field, every production adapter
     * builds it from a string literal, and a value class over [String] cannot hold blank without a
     * `.value = ""` literal someone would have to write on purpose. A test named "…and an EXTERNAL one
     * is renderable" that only checks "not blank" is exactly the class of unfalsifiable guard spec
     * §6.3 warns against (F2/D10) — shipping it would put a passing test's name on a property the test
     * cannot see.
     *
     * What actually can drift silently, and what this checks instead:
     * [com.sidr.launcher.feature.launcher.agent.AgentSessionPresentation.toolLabelFor]
     * (`:feature:launcher`) maps a [com.sidr.launcher.domain.tool.ToolId] to a string resource and falls
     * back to `launcher_agent_step_generic` for anything it does not recognise — **deliberately**, so an
     * unmapped id degrades to a vaguer sentence rather than a crash (that function's own KDoc). That
     * fail-soft design is precisely why nothing else catches a tool registered with no surface arm: the
     * type system stays green ([com.sidr.launcher.domain.tool.ToolId] is open over [String], so any
     * value type-checks), every existing test stays green, and the tool simply renders as "Run this
     * step for …" forever. This is the drift hole carried over from Task 10's review, and this guard is
     * its right home.
     *
     * `toolLabelFor` is `internal` to `:feature:launcher`. Kotlin's `internal` is per **module**, not
     * per package, and `:app`'s test source set is a different module even though it compiles against
     * `:feature:launcher` — so `:app` cannot call it, and this is a **textual scan** instead, over
     * [stripComments]'d text so a KDoc that only mentions an id cannot satisfy the check.
     *
     * **What the scan can see, and what it cannot — stated plainly, as the ruling asked:**
     *  - The two `:domain`-level ids (`launch_app`, `play_store_search`) are referenced in
     *    `AgentSessionPresentation.kt` *symbolically* — `ToolIds.LAUNCH_APP.value`, never the raw
     *    string — because `ToolIds` is the shared `domain/tool` vocabulary and the literal itself lives
     *    in `ToolId.kt`, not in the presentation file (that file's own KDoc explains why: Tier-0 ids are
     *    duplicated as raw literals for exactly the opposite reason — see below). So this scan resolves
     *    the symbol by reading `domain/tool/ToolId.kt` — the very file `ToolIds` is declared in — rather
     *    than hard-coding today's two names. A rename of the *declaration* there is picked up
     *    automatically; it would only miss a reference that names the wrong symbol while the module
     *    still compiles, which Kotlin's own name resolution does not allow here (`ToolIds.LAUNCH_APP`
     *    either resolves to the real constant or `:feature:launcher` fails to build).
     *  - Every other id (today: the two Tier-0 ids) is required by `AgentSessionPresentation.kt`'s own
     *    KDoc to be duplicated as a raw string literal, because `:feature:launcher` may not depend on
     *    `:data:repository`, where the real constant (`Tier0ToolIds`) actually lives. For these the scan
     *    looks for the literal quoted value anywhere in the stripped text. This is precisely the drift
     *    the finding names: if `Tier0IntentToolSource` renames `SET_TIMER`'s value, the *old* literal
     *    stranded in `AgentSessionPresentation.kt` no longer matches anything `productionFederation()`
     *    actually returns, and the *new* value appears nowhere in that file — this test goes red.
     *  - **A tool whose id starts with `ShortcutToolIds.PREFIX` is answered by a different rule** (A1″
     *    Task 8), because its name is **data**: it was authored by another app, in that app's language,
     *    and no string resource exists or should. The rule is written as a **positive requirement rather
     *    than an exemption** — such a tool must appear in `DynamicToolNames.names()` with both halves of
     *    its name non-blank, and the surface must carry a sentence to put that name into
     *    (`launcher_agent_step_shortcut`). A dynamic tool with no name renders as exactly the same generic
     *    line this rule exists to catch; filtering it out instead of requiring the name would have made
     *    the guard blind to the whole third adapter.
     *  - **What it cannot see**, named rather than implied absent: the check is `String.contains`, not
     *    "is a `when` arm" — a symbol or literal appearing anywhere else in the file (a different
     *    function, or prose a looser comment-stripper would have left behind) would also satisfy it, and
     *    a substring match cannot distinguish `"set_timer"` from a hypothetical `"set_timer_v2"`. Neither
     *    is a real risk against today's small, single-purpose file, but both are named rather than
     *    implied absent.
     *  - **For the dynamic-label branch specifically, that blindness is measured, not hypothetical**
     *    (`docs/superpowers/plans/2026-09-12-a1-mutation-round-adapter-3.md`, finding F1, mutation M13):
     *    making `AgentSessionPresentation`'s `PlanStep.line` ignore `dynamicLabels` entirely — so every
     *    shortcut step renders the generic line, exactly the defect this bullet exists to catch — left
     *    `:app` green at 59/0. The `launcher_agent_step_shortcut` resource id is still present in the
     *    file, in a branch the mutated code can no longer reach, and `String.contains` cannot tell the
     *    difference. The authored-tool branch above is not weakened by this finding: M16 (a
     *    registered-but-unnamed dynamic tool) and the shared floor (M1/M2/M3) do turn this test red, so
     *    the blindness is specific to the dynamic-label check, not to the whole test. For that branch the
     *    property **is** held — but only in `:feature:launcher`, by
     *    `LauncherScreenAgentProvenanceTest > a shortcut tool renders its own name and its app_shortcut
     *    provenance`, which asserts on the rendered text and went red under M13. A sound `:app`-level
     *    guarantee for the dynamic-label branch needs `line`/`toolLabelFor` themselves reachable from
     *    `:app` — they are `internal` to `:feature:launcher` today, not merely unread — or this
     *    assertion relocated beside that behavioural test.
     */
    @Test
    fun `every tool in the production federation has a non-generic label on the surface`() {
        val presentationFile = File(
            repoRoot,
            "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/" +
                "AgentSessionPresentation.kt",
        )
        assertEquals("missing presentation file: $presentationFile", true, presentationFile.isFile)
        val presentationText = stripComments(presentationFile.readText())

        val toolIdFile = File(repoRoot, "domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolId.kt")
        assertEquals("missing ToolId.kt: $toolIdFile", true, toolIdFile.isFile)
        val domainIdNames: Map<String, String> = Regex("""val (\w+) = ToolId\("([^"]+)"\)""")
            .findAll(stripComments(toolIdFile.readText()))
            .associate { it.groupValues[2] to it.groupValues[1] }
        assertEquals(
            "no `val NAME = ToolId(\"value\")` entries found in ToolId.kt — the extraction regex or " +
                "the file moved, and this guard went blind to the domain-level half of the vocabulary",
            true,
            domainIdNames.isNotEmpty(),
        )

        val dynamicNames = shortcutSource().names().associateBy { it.id }

        productionFederation().registry.all().forEach { descriptor ->
            val id = descriptor.id.value
            if (id.startsWith(ShortcutToolIds.PREFIX)) {
                // The positive requirement, not an exemption (see this test's KDoc). A dynamic tool
                // must be NAMED by the port that carries data-authored names; one that is registered
                // and unnamed renders as the same generic line, which is the very defect the authored
                // half of this rule exists to catch, pointing the other way.
                val name = dynamicNames[descriptor.id]
                assertEquals(
                    "'$id' is registered but absent from DynamicToolNames.names(), so the surface has " +
                        "no name for it and it will render as the generic step line ('Run this step " +
                        "for …') forever — the same defect as a missing toolLabelFor arm",
                    true,
                    name != null,
                )
                assertEquals(
                    "'$id' is named by DynamicToolNames but one half of that name is blank, so the " +
                        "rendered line would read as punctuation around nothing",
                    true,
                    name != null && name.qualifier.isNotBlank() && name.name.isNotBlank(),
                )
                // And the surface must have a sentence to put that name INTO. `:app` cannot call
                // `line` (it is `internal` to `:feature:launcher`), so this is the same textual scan
                // the authored half uses.
                assertEquals(
                    "AgentSessionPresentation.kt has no launcher_agent_step_shortcut arm, so a tool " +
                        "whose name is data has nowhere to be rendered",
                    true,
                    presentationText.contains("R.string.launcher_agent_step_shortcut"),
                )
                return@forEach
            }
            val symbolicName = domainIdNames[id]
            val handled = if (symbolicName != null) {
                presentationText.contains("ToolIds.$symbolicName")
            } else {
                presentationText.contains("\"$id\"")
            }
            assertEquals(
                "'$id' has no non-generic arm in AgentSessionPresentation.kt's toolLabelFor — it will " +
                    "silently render as the generic step line ('Run this step for …') forever",
                true,
                handled,
            )
        }
    }

    /**
     * `DOC-ADL-3`, the A1′ half. `AgentVocabularyGuardTest` holds mechanically that `domain/agent` and
     * `domain/tool` cannot NAME a network type; beyond those two packages "nothing leaves the device"
     * was a fact about the single source, not a check. Federation is where that stops being enough:
     * every registered adapter is scanned for the same forbidden names.
     *
     * Honest about its own reach: this is a textual scan of the adapter files, so a source reaching the
     * network through a helper in another file is outside it. That direction is a false GREEN and is
     * why the adapter set itself is pinned below — a new source must be added deliberately, and an ADR
     * is where its egress is argued.
     */
    @Test
    fun `no registered adapter names a network type`() {
        val forbidden = listOf("HttpClient", "OkHttp", "Retrofit", "java.net", "Ktor", "URLConnection")
        val adapterFiles = listOf(
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSource.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolWorker.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSource.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt",
            // A1″ adapter #3. Nine files rather than two: unlike the other adapters, this source reads
            // its tools from somewhere, so the catalog, the `LauncherApps` seams it reads them through,
            // the trigger that drives the refresh and the types that carry a shortcut's identity and
            // name are all as much part of the adapter's reach as the source itself.
            //
            // Fix round 1 (finding 9) added the last five. `ShortcutRefreshTrigger.kt` was the glaring
            // one — at the time it held a `Context` and reached a system service, and it was the only
            // file of adapter #3 that this scan did not see. Its `Context` has since moved behind
            // `ShortcutChangeObserver`, which is scanned here for exactly the same reason.
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutToolSource.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutToolWorker.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutCatalog.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/AndroidShortcutQuery.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutChangeObserver.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutRefreshTrigger.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutToolIds.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/AppShortcut.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/DynamicToolNames.kt",
        ).map { File(repoRoot, it) }

        adapterFiles.forEach { file ->
            assertEquals("missing adapter file: $file", true, file.isFile)
            val text = stripComments(file.readText())
            forbidden.forEach { term ->
                assertEquals(
                    "${file.name} names $term — a tool source may not reach the network",
                    false,
                    text.contains(term),
                )
            }
        }
    }

    /**
     * The adapter set is a **declared** list, so a source added without an ADR turns this red. This is
     * the change-control §5 line "give a tool a path to the world outside ToolRegistry/ToolExecutor"
     * made mechanical at the composition root.
     *
     * **It is also the only thing that couples [productionAdapters] to the real graph**, which is why
     * the shape below is three assertions rather than one. A mutation round measured the old
     * single-assertion version going blind: the level regex required `ToolLevels.` immediately after the
     * open paren, so a third adapter written `ToolAdapter(level = ToolLevels.SANDBOX, …)` — legal,
     * idiomatic Kotlin, and only positional here by convention — was matched by nothing. With a registry
     * declaring an `EXTERNAL`/`CONFIRM`/`DURABLE` tool behind it, **all 54 `:app` tests stayed green.**
     *
     * So the count is asserted independently of the argument spelling: any `ToolAdapter` construction in
     * this module is counted, whatever its arguments look like. And [levels] is asserted to be as long as
     * that count, which is what stops the extraction from silently skipping a construction it cannot
     * parse — a named-argument order this regex does not anticipate reddens as a mismatch instead of
     * disappearing.
     *
     * **The assertion order is load-bearing and must not be rearranged.** The count-equals-levels check
     * is vacuous over nothing — measured: an emptied production list yields `0 == 0` and satisfies it.
     * What makes the trio non-vacuous is the count assertion pinning to a literal, which fires on an
     * empty input before the third is reached.
     *
     * **And this scan has a floor it cannot rise above, measured rather than assumed.** A second round
     * evaded the repaired version with one character — `ToolAdapter (…)`, a space before the argument
     * list, which Kotlin permits and Hilt accepts — and a third adapter declaring an
     * `EXTERNAL`/`CONFIRM`/`DURABLE` tool reached the production graph with all of `:app` green. That
     * spelling is closed now (`ToolAdapter\s*\(`), but the class of evasion is not: a regex over one
     * hard-coded file cannot see `someAdapter.copy(registry = …)`, a `listOf(…) + adaptersBuiltElsewhere`,
     * or a second `@Module` in `:app` providing adapters of its own. Nothing couples this path to Hilt's
     * actual set of `ToolAdapter` providers.
     *
     * The real repair is therefore not a wider regex: it is asserting the federation's **own** output —
     * the distinct `ToolLevel`s reachable through `registry.all()` from the real `provideToolFederation`
     * — which no spelling can evade. **That test now exists**
     * ([`the graph's own federation reaches exactly the declared tool levels`], added in fix round 1,
     * together with the risk-totality half [declaredRisk]'s KDoc asked for), and this scan is kept
     * beside it rather than replaced by it, because the two fail on different things: only the scan can
     * see the **order**, which is first-adapter-wins collision precedence and is not expressible in a
     * post-dedup set, and only the scan reddens when an adapter is written in a spelling the regex
     * cannot parse. What is no longer this scan's burden is being the *only* thing that couples this
     * file to the real graph.
     */
    @Test
    fun `the composition root registers exactly the declared adapters`() {
        val moduleFile = File(repoRoot, "app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt")
        assertEquals(
            "missing composition root: ${moduleFile.canonicalPath} — this guard reads one hard-coded " +
                "path, so a moved or renamed module makes it blind rather than red",
            true,
            moduleFile.isFile,
        )
        val module = moduleFile.readText()
        val constructions = Regex("""ToolAdapter\s*\(""").findAll(module).count()
        val levels = Regex("""ToolAdapter\s*\(\s*(?:level\s*=\s*)?(ToolLevels\.\w+)""")
            .findAll(module)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(
            "A new adapter is a new path to the world and needs an ADR, not a line. " +
                "ToolAdapter( constructions found in AgentProvidesModule.kt: $constructions",
            3,
            constructions,
        )

        assertEquals(
            "A new adapter is a new path to the world and needs an ADR, not a line. Found: $levels",
            listOf("ToolLevels.IN_APP", "ToolLevels.SYSTEM_INTENT", "ToolLevels.APP_SHORTCUT"),
            levels,
        )

        assertEquals(
            "This scan read $constructions ToolAdapter( constructions but could only extract " +
                "${levels.size} levels from them, so at least one adapter is written in a spelling " +
                "this regex does not parse and the assertion above is blind to it. Widen the regex — " +
                "do not relax this check: it is what keeps the guard from going silently blind.",
            constructions,
            levels.size,
        )
    }

    /**
     * **Controller ruling R21.** `AgentProvidesModule.providePlanner` composes
     * `CompositePlanner(listOf(TemplatePlanner(), toolMatchPlanner))`, and nothing held that list before
     * this test: drop `toolMatchPlanner` and the whole `ToolMatchPlanner` capability goes silently
     * inert in production — `ToolMatchPlannerTest` keeps testing the planner directly, in isolation,
     * never through the wired graph, so routing step 2b (a free-text goal reaching a registered tool)
     * is never actually reachable in the shipped app while every test in the suite stays green. That is
     * the same "pinned by nothing" shape `the composition root registers exactly the declared adapters`
     * already closes for the tool-adapter list, and the same class of defect Task 10's review found had
     * already made this block's headline capability dead in production once.
     *
     * A textual scan, same idiom as the adapter-list test above: read the arguments passed to
     * `CompositePlanner(listOf(...))` inside `providePlanner` and assert membership. The regex's lazy
     * `[\s\S]*?` stops at the first `))` it finds after `listOf(`, which is the call's own closing pair
     * — `TemplatePlanner()`'s single closing paren is never followed by a second one, so it cannot end
     * the match early.
     */
    @Test
    fun `the composition root's planner list matches the declared planners`() {
        val module = File(repoRoot, "app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt").readText()
        val match = Regex("""CompositePlanner\(\s*listOf\(([\s\S]*?)\)\)""").find(module)
        assertEquals(
            "CompositePlanner(listOf(...)) not found in providePlanner — AgentProvidesModule.kt's " +
                "planner wiring moved and this guard went blind to it",
            true,
            match != null,
        )

        val declared = match!!.groupValues[1]
            .split(",")
            .map { it.trim().removeSuffix("()") }
            .filter { it.isNotEmpty() }

        assertEquals(
            "The production planner list must compose both the deterministic template planner and the " +
                "tool matcher — dropping either silently kills a whole routing path while every test " +
                "stays green. A third planner (A4′'s model planner) is a legitimate future addition and " +
                "must update this list deliberately, the same as a new tool adapter. Found: $declared",
            listOf("TemplatePlanner", "toolMatchPlanner"),
            declared,
        )
    }

    /**
     * Declared risk, pinned where totality over the registry can be asserted.
     *
     * Each source's own test pins its own tools — `Tier0IntentToolSourceTest`'s
     * `none { requiresConsent(it.risk) }` for the two Tier-0 tools, and `SystemIntentToolContractTest`'s
     * field-for-field parity test for the two projected in-app ones — which is why a `SAFE → CONFIRM`
     * mutation on a Tier-0 tool reddens `:data:repository` while the whole `:app` suite stays green.
     * That arrangement covers today's four tools and nothing a future adapter registers, which is why
     * the assertion moved here.
     *
     * **This map is hand-written, so a wrong value is exactly as green as a right one** — the same
     * weakness `ToolRegistryPermissionGuardTest` states about its own column. What it buys is totality
     * and drift detection, never correctness of the risk decision itself. **The source is
     * authoritative:** when the drift assertion reddens, the pin is a risk decision to re-make and
     * re-justify, not a number to copy across from the descriptor. The plan that prescribed this map
     * got one of its four values wrong — it pinned `PLAY_STORE_SEARCH` at `SAFE` where
     * `DefaultActionCatalog` declares `CONFIRM` — which is the empirical argument for writing this
     * paragraph instead of assuming it.
     *
     * **Two limits, measured by the mutation round rather than implied absent:**
     *  1. An adapter whose `registry.all()` returns an **empty** list is invisible here. The assertion
     *     quantifies over declared tools, so an adapter declaring none has nothing to pin, and the floor
     *     in [productionAdapters] cannot see it either — that floor is *containment* of
     *     [REQUIRED_TOOL_IDS], which the other adapters satisfy on their own. This class answers "is
     *     every tool that **is** registered pinned and un-drifted?", never "did every wired adapter
     *     register anything?".
     *  2. **The test below does not detect a new adapter's arrival at all**, because its totality is
     *     over [productionAdapters] — a hand-maintained replica of
     *     `AgentProvidesModule.provideToolFederation`. Measured: a third adapter added to the real
     *     module, declaring an `EXTERNAL`/`CONFIRM`/`DURABLE` tool with no row here, left all 54 `:app`
     *     tests green. **Fix round 1 closed that half rather than leaving it named:** the same two pins
     *     are now also asserted over the graph's own federation by
     *     [`every tool the graph's own federation reaches has its risk pinned here`], which calls the
     *     provider instead of copying it. The per-adapter test keeps its own reason to exist — it reads
     *     **pre-dedup**, so it is the only one that can see a second adapter redeclaring an existing id
     *     at a different risk, which `ToolFederation` resolves away before `all()` returns.
     */
    private val declaredRisk: Map<ToolId, ActionRiskLevel> = mapOf(
        ToolIds.LAUNCH_APP to ActionRiskLevel.SAFE,
        ToolIds.PLAY_STORE_SEARCH to ActionRiskLevel.CONFIRM,
        Tier0ToolIds.SET_TIMER to ActionRiskLevel.SAFE,
        Tier0ToolIds.OPEN_SYSTEM_SETTINGS to ActionRiskLevel.SAFE,
        Tier0ToolIds.SET_ALARM to ActionRiskLevel.SAFE,
    )

    /**
     * **A1″ adapter #3 is pinned as a FAMILY, because it cannot be pinned by id.** A shortcut tool's id
     * is derived from a package and a shortcut another app published, so the set differs on every
     * device (205 tools from 65 packages on the measured SM-A325F) and no literal can name them. What
     * *is* fixed is that `ShortcutToolSource` declares one rating for all of them, from a single
     * expression — so the family is the honest unit of pinning, and the drift assertion over it catches
     * exactly what the per-id map catches for the authored four: an edit to that expression that no one
     * re-justified.
     *
     * What this does **not** buy, said rather than implied: it cannot see a source that starts varying
     * risk per shortcut, since such a source would simply have members at more than one level and the
     * assertion would redden — which is the correct outcome, but as a *failure to parse the new design*,
     * not as a judgement about it. Whoever introduces per-shortcut risk replaces this pin rather than
     * widening it.
     */
    private val DYNAMIC_TOOL_RISK: ActionRiskLevel = ActionRiskLevel.SAFE

    /**
     * The input to `DOC-ADL-1`'s one risk-to-gate predicate, pinned.
     *
     * "Registered" here means **declared by a registered source**: the read is [productionAdapters],
     * before [ToolFederation] deduplicates. That is the wider of the two readings and the only one that
     * can see a second adapter redeclaring an existing id at a different risk.
     *
     * Two assertions, separately load-bearing and separately proved — the mutation round fired them
     * independently: a removed row reddens totality, a changed declaration reddens drift.
     *
     * The drift filter deliberately does **not** re-check presence. The totality assertion above throws
     * unless every registered id is in [declaredRisk], and the map's value type is non-nullable, so a
     * `!= null` conjunct in that filter would be a branch that cannot fail — the F2/D10 shape this
     * repository removes rather than keeps as decoration.
     */
    @Test
    fun `every registered tool's declared risk is pinned here`() {
        val registered = productionAdapters().flatMap { it.registry.all() }
        val (dynamic, authored) = registered.partition { it.id.value.startsWith(ShortcutToolIds.PREFIX) }

        val unpinned = authored.map { it.id }.filterNot { it in declaredRisk }
        assertEquals(
            "A tool whose risk is pinned by nothing can change gate behaviour silently: " +
                "${unpinned.map { it.value }}",
            emptyList<ToolId>(),
            unpinned,
        )

        val drifted = authored
            .filter { declaredRisk[it.id] != it.risk }
            .map { "${it.id.value}: pinned ${declaredRisk[it.id]}, declared ${it.risk}" }
        assertEquals(
            "Declared risk changed without this pin changing with it: $drifted",
            emptyList<String>(),
            drifted,
        )

        // The dynamic family, pinned as a family — see [DYNAMIC_TOOL_RISK]. Two assertions, and the
        // first is the one that keeps the second from being vacuous: with no shortcut tool registered
        // this whole block would quantify over nothing, which is the shape [productionAdapters]'
        // own floor exists to catch one level up. Asserted again here so the pin is non-vacuous on
        // its own terms rather than only by that floor's grace.
        assertEquals(
            "No app_shortcut tool reached this pin, so the family rule below checked nothing.",
            true,
            dynamic.isNotEmpty(),
        )
        val dynamicDrift = dynamic
            .filterNot { it.risk == DYNAMIC_TOOL_RISK }
            .map { "${it.id.value}: family-pinned $DYNAMIC_TOOL_RISK, declared ${it.risk}" }
        assertEquals(
            "A dynamic tool's risk is decided by its adapter, not by a human reading a diff: there is " +
                "one rating for the whole family and it must hold for every member. A shortcut launch " +
                "raised above SAFE would put every one of a device's hundreds of shortcuts behind the " +
                "consent gate; lowering the family rating would take the gate off all of them at once. " +
                "Drifted: $dynamicDrift",
            emptyList<String>(),
            dynamicDrift,
        )
    }

    private companion object {
        /**
         * The floor, never the ceiling — see [productionAdapters].
         *
         * **Deliberately a second literal, not `declaredRisk.keys`.** Deriving the floor from the map it
         * corroborates is what defeated this block's sibling guard on its first pass: a paired "drop the
         * tool and tidy away its row" edit satisfied both halves at once and passed silently. Keeping
         * the duplication is the price of the floor meaning anything — do not tidy these two lists into
         * one, in either direction.
         */
        val REQUIRED_TOOL_IDS: List<ToolId> = listOf(
            ToolIds.LAUNCH_APP,
            ToolIds.PLAY_STORE_SEARCH,
            Tier0ToolIds.SET_TIMER,
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
            Tier0ToolIds.SET_ALARM,
        )
    }
}
