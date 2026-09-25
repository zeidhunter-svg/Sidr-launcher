package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.data.repository.agent.shortcut.AppShortcut
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.data.repository.intent.RuleBasedIntentMatcher
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningRequest
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import com.sidr.launcher.domain.tool.ResolvedInvocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * **`B15` — a MEASUREMENT, not a guard.** Master Plan §3.6 row `B15`: what share of real phrasings does
 * the tool-selection path decline, and *why*, so A4′ can decide whether the multi-turn clarification
 * protocol is ornament or product. The row names the threshold — "5% declined is ornament; 40% is the
 * product" — and A4′ spec 2 of three (§3.3) is the decision it feeds.
 *
 * **It asserts almost nothing on purpose.** Pinning a decline rate would create a test that goes red when
 * the owner installs an app, which is a gate that lies about the diff. What it asserts is
 * well-formedness: every corpus line lands in exactly one category, and the categories sum to the corpus
 * size. The number itself is *reported*, and a human decides what it means.
 *
 * **Inputs are git-ignored and may be absent.** `src/test/resources/b15/` holds a dump of the owner's
 * phone; `README.md` there says how to regenerate it and why it is not committed. With any input missing
 * this reports `SKIPPED` and passes — a fresh clone has no fixture, and a gate that goes red because a
 * deliberately-uncommitted file is absent would be worse than no measurement. Being under
 * `src/test/resources` also means Gradle already treats the fixture as a task input, so editing it
 * re-runs this instead of leaving the task `UP-TO-DATE` — the trap `D3`/`D8` record for guards that read
 * files their task never declared.
 *
 * **The verdict always comes from production code; only the SUB-REASON is diagnosed here.**
 * `ToolSelector.select` returns `null` for four different reasons and does not say which. Two of the four
 * are recovered without reimplementing anything: `ToolVocabulary.isAmbiguous` is called directly, and a
 * dynamic tie is counted by asking the **production** matcher one name at a time (a selector built over a
 * single [com.sidr.launcher.data.repository.agent.DynamicToolName]), so the rule that decides a hit is
 * always production's. Only [looksLikeUnqualifiedShortcut] restates one line of that rule — the
 * contiguous-run test — and it is used as a *label* on an already-decided decline, never as a verdict.
 *
 * **FastPath shadowing is a category, not noise.** A1′ measured six of thirteen planned triggers as
 * unreachable because `LAUNCH_VERBS`/`INSTALL_VERBS` caught them first, and that was found by running the
 * real matcher rather than by reading the table. So the real [RuleBasedIntentMatcher] runs here, and
 * [isUndecided] reproduces `RouteCommandUseCase`'s private predicate **verbatim** (`Unknown ||
 * LowConfidence`, `RouteCommandUseCase.kt:188`). If that predicate ever changes, this copy is wrong and
 * nothing will say so — the one knowing drift risk in this file, recorded rather than hidden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SelectionDeclineMeasurement {

    private enum class Outcome {
        /** A registered tool matched, planned, and FastPath had not already decided. The working case. */
        PLANNED,

        /** A tool matched, but FastPath decided first, so step 2b is never reached. */
        SHADOWED_BY_FASTPATH,

        /** FastPath decided it by itself — an app launch, a web search. Not a decline at all. */
        HANDLED_BY_FASTPATH,

        /** Selector declined: two or more authored triggers claim the text. → clarification protocol. */
        DECLINE_AMBIGUOUS_AUTHORED,

        /** Selector declined: two or more shortcuts match equally. → clarification protocol. */
        DECLINE_DYNAMIC_TIE,

        /** Selector declined: a shortcut's own label is present but its app was never named. */
        DECLINE_DYNAMIC_UNQUALIFIED,

        /** Selector declined: nothing claimed the text at all. → vocabulary gap, not a protocol. */
        DECLINE_NO_MATCH,

        /** Selector matched; the planner refused. `R14-39`: this is the one that reaches the cloud. */
        DECLINE_UNRESOLVED_TARGET,

        /** Selector matched; a required argument was absent or blank. */
        DECLINE_MISSING_ARG,
    }

    private data class Row(val phrase: String, val outcome: Outcome, val acted: String, val verdict: String)

    /** Enough to judge "did it do what was asked", not more: the action family and its target. */
    /** Enough to judge "did it do what was asked", not more: the action family and its target. */
    private fun ExecutableAction.describeForReport(): String = when (this) {
        is ExecutableAction.LaunchAppAction -> "LAUNCH $packageName"
        is ExecutableAction.OpenSearchAction -> "SEARCH[$target] $query"
        is ExecutableAction.OpenUrlAction -> "OPEN_URL $url"
        is ExecutableAction.PlayStoreSearchAction -> "PLAY_STORE $query"
        is ExecutableAction.AmbiguousAppAction -> "AMBIGUOUS_APP $query -> ${candidates.size} candidates"
        is ExecutableAction.ShowMessageAction -> "MESSAGE ${message::class.java.simpleName}"
        ExecutableAction.OpenLauncherSettingsAction -> "LAUNCHER_SETTINGS"
        ExecutableAction.NoOpAction -> "NOOP"
    }

    @Test
    fun measure_selection_decline_rate() {
        val shortcuts = readLines("b15/shortcuts.jsonl")
        val apps = readLines("b15/apps.jsonl")
        val corpus = readLines("b15/corpus.txt")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }

        if (shortcuts == null || apps == null || corpus == null) {
            println(
                "B15 :: SKIPPED - inputs absent (shortcuts=${shortcuts != null}, apps=${apps != null}, " +
                    "corpus=${corpus != null}). See data/repository/src/test/resources/b15/README.md.",
            )
            return
        }

        val installed = apps.map { line ->
            val o = Json.parseToJsonElement(line).jsonObject
            InstalledApp(
                packageName = o.getValue("package").jsonPrimitive.content,
                label = o.getValue("label").jsonPrimitive.content,
            )
        }
        val dynamicNames = shortcuts.map { line ->
            val o = Json.parseToJsonElement(line).jsonObject
            val id = o.getValue("id").jsonPrimitive.content
            DynamicToolName(
                id = com.sidr.launcher.domain.tool.ToolId(id),
                qualifier = o.getValue("qualifier").jsonPrimitive.content,
                name = o.getValue("name").jsonPrimitive.content,
            )
        }

        val appsRepo = FakeInstalledAppsRepository().apply { appsToReturn = installed }
        val shortcutSource = shortcutSourceOf(dynamicNames)
        val vocabulary = ToolVocabulary()
        val selector = ToolSelector(vocabulary, shortcutSource)
        val appTargets = AppTargetResolver(appsRepo, FakeAliasStore())
        val planner = ToolMatchPlanner(selector, appTargets)
        val registry = federation(shortcutSource).registry
        val rows = corpus.map { phrase ->
            val executor = FakeActionExecutor()
            val perPhrase = HandleUserCommandUseCase(
                matcher = RuleBasedIntentMatcher(),
                resolver = IntentActionResolver(appsRepo),
                executor = executor,
                confidencePolicy = DefaultIntentConfidencePolicy(),
                recordingScope = CoroutineScope(SupervisorJob()),
            )
            val outcome = classify(phrase, perPhrase, selector, vocabulary, dynamicNames, planner, registry)
            // What FastPath actually DID, not what the category name suggests. `HANDLED_BY_FASTPATH`
            // reads as success and may be a silent truncation - "открой X и сделай Y" launching X and
            // discarding Y. Only the owner can judge which, and only if the report SHOWS the action.
            Row(
                phrase = phrase,
                outcome = outcome,
                acted = executor.executedActions.joinToString(", ") { it.describeForReport() },
                verdict = lastFastPathVerdict,
            )
        }

        report(rows, corpusSize = corpus.size, tools = registry.all().size, shortcuts = dynamicNames.size)

        assertEquals("every corpus line must land in exactly one category", corpus.size, rows.size)
        assertTrue("the fixture must not be empty, or the measurement is vacuous", dynamicNames.isNotEmpty())
    }

    private var lastFastPathVerdict: String = ""

    /** How FastPath closed the line. `HANDLED_BY_FASTPATH` covers several very different answers. */
    private fun CommandOutcome.describeForReport(): String = when (this) {
        is CommandOutcome.Message -> "Message(${message::class.java.simpleName})"
        is CommandOutcome.NeedsConfirmation -> "NeedsConfirmation(${candidates.size})"
        is CommandOutcome.Suggest -> "Suggest(${intent::class.java.simpleName}, $confidence)"
        is CommandOutcome.Unknown -> "Unknown"
        // `else` rather than the full list on purpose: this is a report renderer, and a new
        // CommandOutcome variant must not break the measurement - it must show up by its own name.
        else -> this::class.java.simpleName
    }

    private fun classify(
        phrase: String,
        fastPath: HandleUserCommandUseCase,
        selector: ToolSelector,
        vocabulary: ToolVocabulary,
        dynamicNames: List<DynamicToolName>,
        planner: ToolMatchPlanner,
        registry: ToolRegistry,
    ): Outcome = runBlocking {
        val fastPathOutcome = fastPath.handle(phrase)
        lastFastPathVerdict = fastPathOutcome.describeForReport()
        val fastPathDecided = !isUndecided(fastPathOutcome)

        val match = selector.select(phrase)
        if (match == null) {
            if (fastPathDecided) return@runBlocking Outcome.HANDLED_BY_FASTPATH
            val normalized = CommandNormalizer.normalize(phrase)
            return@runBlocking when {
                vocabulary.isAmbiguous(normalized) -> Outcome.DECLINE_AMBIGUOUS_AUTHORED
                dynamicHits(normalized, dynamicNames, vocabulary) > 1 -> Outcome.DECLINE_DYNAMIC_TIE
                looksLikeUnqualifiedShortcut(normalized, dynamicNames) -> Outcome.DECLINE_DYNAMIC_UNQUALIFIED
                else -> Outcome.DECLINE_NO_MATCH
            }
        }

        val planned = planner.plan(PlanningRequest(AgentGoal(phrase, GoalShape.Free(phrase))), registry)
        if (planned is PlanningResult.Planned) {
            return@runBlocking if (fastPathDecided) Outcome.SHADOWED_BY_FASTPATH else Outcome.PLANNED
        }

        // The selector matched but the planner refused. Which of its two refusals was it?
        val descriptor = registry.find(match.id)
        val required = descriptor?.argSchema?.filter { it.required }?.map { it.name }.orEmpty()
        if (required.any { match.args[it].isNullOrBlank() }) return@runBlocking Outcome.DECLINE_MISSING_ARG
        Outcome.DECLINE_UNRESOLVED_TARGET
    }

    /**
     * Verbatim copy of `RouteCommandUseCase.isUndecided` (`RouteCommandUseCase.kt:188`), which is private.
     * Step 2b fires on exactly this condition, so a measurement that used a different one would count a
     * different population than the product does.
     */
    private fun isUndecided(outcome: CommandOutcome): Boolean =
        outcome is CommandOutcome.Unknown || outcome is CommandOutcome.LowConfidence

    /**
     * How many shortcuts the **production** matcher accepts for this text, counted by offering it one
     * name at a time. Nothing about the matching rule is restated: a single-name selector runs the same
     * `select`, and the authored vocabulary above it has already declined (this is only reached on a
     * decline), so the dynamic branch is the branch that answers.
     */
    private fun dynamicHits(
        normalized: String,
        names: List<DynamicToolName>,
        vocabulary: ToolVocabulary,
    ): Int = names.count { one ->
        ToolSelector(vocabulary, object : DynamicToolNames { override fun names() = listOf(one) })
            .select(normalized) != null
    }

    /**
     * A *label* on an already-decided decline, never a verdict: does some shortcut's own name occur in
     * the text as a whole-word run, while its app was not named? This restates one line of
     * `ToolSelector.matches` and is the single place in this file that does. It exists because
     * "the user said the shortcut but not the app" and "nothing matched at all" feed different decisions
     * — the first is stop-words and clarification, the second is a vocabulary gap.
     */
    private fun looksLikeUnqualifiedShortcut(normalized: String, names: List<DynamicToolName>): Boolean =
        names.any { n ->
            val name = CommandNormalizer.normalize(n.name)
            name.isNotBlank() && " $normalized ".contains(" $name ")
        }

    private fun shortcutSourceOf(names: List<DynamicToolName>): ShortcutToolSource {
        val shortcuts = names.map { n ->
            val body = n.id.value.substringAfter(':')
            AppShortcut(
                packageName = body.substringBefore('/'),
                shortcutId = body.substringAfter('/'),
                appLabel = n.qualifier,
                shortcutLabel = n.name,
            )
        }
        val catalog = ShortcutCatalog({ shortcuts }, Dispatchers.Unconfined)
        runBlocking { catalog.refresh() }
        return ShortcutToolSource(catalog)
    }

    /** `AgentProvidesModule`'s adapter list, minus the workers — nothing here ever invokes a tool. */
    private fun federation(shortcutSource: ShortcutToolSource) = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), NeverRuns),
            ToolAdapter(
                ToolLevels.SYSTEM_INTENT,
                Tier0IntentToolSource(ToolPermissionCatalog()) { true },
                NeverRuns,
            ),
            ToolAdapter(ToolLevels.APP_SHORTCUT, shortcutSource, NeverRuns),
            ToolAdapter(ToolLevels.LAUNCHER_MEMORY, MemoryToolSource(), NeverRuns),
        ),
    )

    private object NeverRuns : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            error("B15 measures selection only; no tool may run: ${invocation.id.value}")
    }

    private fun readLines(resource: String): List<String>? =
        javaClass.classLoader?.getResourceAsStream(resource)?.bufferedReader()?.readLines()

    private fun report(
        rows: List<Row>,
        corpusSize: Int,
        tools: Int,
        shortcuts: Int,
    ) {
        val byOutcome = rows.groupingBy { it.outcome }.eachCount()
        val declines = rows.count { it.outcome.name.startsWith("DECLINE_") }
        val ambiguity = byOutcome.filterKeys {
            it == Outcome.DECLINE_AMBIGUOUS_AUTHORED ||
                it == Outcome.DECLINE_DYNAMIC_TIE ||
                it == Outcome.DECLINE_DYNAMIC_UNQUALIFIED
        }.values.sum()

        val out = StringBuilder()
        out.appendLine("B15 - selection decline rate")
        out.appendLine("corpus=$corpusSize  registry=$tools tools ($shortcuts of them shortcuts)")
        out.appendLine()
        Outcome.entries.forEach { o ->
            val n = byOutcome[o] ?: 0
            out.appendLine("  %-30s %4d  %5.1f%%".format(o.name, n, 100.0 * n / corpusSize))
        }
        out.appendLine()
        out.appendLine("  DECLINED (any reason)          %4d  %5.1f%%".format(declines, 100.0 * declines / corpusSize))
        out.appendLine("  of which AMBIGUITY             %4d  %5.1f%%".format(ambiguity, 100.0 * ambiguity / corpusSize))
        out.appendLine()
        out.appendLine("AMBIGUITY is the share that a multi-turn clarification protocol could convert;")
        out.appendLine("DECLINE_NO_MATCH is a vocabulary gap and a protocol would not help it.")
        out.appendLine()
        out.appendLine("PER LINE. `acted` is what FastPath actually DID. A single LAUNCH beside a")
        out.appendLine("two-clause request is a SILENT TRUNCATION, not a success - the category name")
        out.appendLine("cannot tell those apart, and only the owner can. That is the judgement this")
        out.appendLine("report exists to enable rather than to make.")
        out.appendLine()
        rows.forEach { r ->
            out.appendLine("  ${r.outcome.name.padEnd(29)} ${r.phrase}")
            out.appendLine("  ${"".padEnd(29)} -> fastpath: ${r.verdict}")
            if (r.acted.isNotBlank()) out.appendLine("  ${"".padEnd(29)} -> acted:    ${r.acted}")
        }

        println(out)
        runCatching {
            File("build/reports/b15").mkdirs()
            File("build/reports/b15/selection-decline.txt").writeText(out.toString())
        }
    }
}
