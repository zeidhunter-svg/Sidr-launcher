package com.sidr.launcher.doctrine

import com.sidr.launcher.agent.stripComments
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The block's doctrine test. Five properties, each mutation-proved separately — a green run of a new
 * guard proves nothing on its own.
 *
 * It deliberately builds the **production** federation rather than a fixture: a guard that checks a
 * fixture checks the fixture.
 *
 * Two of the five assertions ([`the composition root registers exactly the declared adapters`] and
 * [`the composition root's planner list matches the declared planners`]) are textual scans of
 * `AgentProvidesModule.kt`, and the surface-label assertion is a textual scan of
 * `AgentSessionPresentation.kt` — the established idiom `ToolExecutorCallSiteGuardTest` /
 * `ToolWorkerCallSiteGuardTest` use for a property one module cannot check by calling into another
 * (Kotlin's `internal` is per-module; both source files sit in modules `:app`'s test source set cannot
 * call into even though it compiles against them). [stripComments] is reused from
 * `com.sidr.launcher.agent`, the same helper the two call-site guards share, so a KDoc that merely
 * mentions a name in prose cannot satisfy any of these checks.
 */
class DoctrineGuardTest {

    private val repoRoot = File("..")

    /**
     * Defined here rather than reused: `ToolMatchPlannerTest`'s `NoopWorker` is private to
     * `:data:repository`'s test source set, and a guard that borrows across modules is a guard that
     * breaks when someone tidies the other module.
     */
    private object NoopWorker : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = ToolResult.Effected()
    }

    private fun productionFederation() = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), NoopWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), NoopWorker),
        ),
    )

    /** `DOC-ILM-2` half one — no tool is silently shadowed, so provenance names a real supplier. */
    @Test
    fun `no two registered tools share an id`() {
        val ids = productionFederation().registry.all().map { it.id.value }

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
     *  - **What it cannot see**, named rather than implied absent: the check is `String.contains`, not
     *    "is a `when` arm" — a symbol or literal appearing anywhere else in the file (a different
     *    function, or prose a looser comment-stripper would have left behind) would also satisfy it, and
     *    a substring match cannot distinguish `"set_timer"` from a hypothetical `"set_timer_v2"`. Neither
     *    is a real risk against today's small, single-purpose file, but both are named rather than
     *    implied absent — a sound guarantee needs `toolLabelFor` itself reachable from `:app`, which it
     *    is not today.
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

        productionFederation().registry.all().forEach { descriptor ->
            val id = descriptor.id.value
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
     */
    @Test
    fun `the composition root registers exactly the declared adapters`() {
        val module = File(repoRoot, "app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt").readText()
        val declared = Regex("""ToolAdapter\(\s*(ToolLevels\.\w+)""").findAll(module).map { it.groupValues[1] }.toList()

        assertEquals(
            "A new adapter is a new path to the world and needs an ADR, not a line. Found: $declared",
            listOf("ToolLevels.IN_APP", "ToolLevels.SYSTEM_INTENT"),
            declared,
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
}
