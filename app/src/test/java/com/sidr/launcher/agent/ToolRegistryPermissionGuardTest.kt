package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0ToolIds
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Permission totality keyed on **what the registry declares**, not on which files contain both a
 * `ToolWorker` and an `Intent(`.
 *
 * [ToolPermissionManifestGuardTest] answers "does every intent a scanned worker issues have a declared
 * permission". This class answers the question the 2026-09-05 defect was actually an instance of: **can
 * every registered tool run at all** — is there a row for it, and is that row's permission in the
 * manifest. Neither subsumes the other, and both are cheap.
 *
 * What this class cannot check, said rather than implied: [toolPermissions] is hand-written, so it is
 * exactly as strong as it is honest. Writing `emptyList()` for a tool that needs a permission leaves
 * every test here green and the app broken — the same weakness its sibling names about its own column.
 * What it does buy is **totality over the registry**: a tool registered by any adapter, with or
 * without a worker branch, with its intent built inline or in a helper three files away, is red until
 * someone writes its row.
 */
class ToolRegistryPermissionGuardTest {

    private val repoRoot = File("..")
    private val manifestFile = File(repoRoot, "app/src/main/AndroidManifest.xml")

    /** Registered tool → the permissions the platform requires of its caller. */
    private val toolPermissions: Map<ToolId, List<String>> = mapOf(
        ToolIds.LAUNCH_APP to emptyList(),
        ToolIds.PLAY_STORE_SEARCH to emptyList(),
        Tier0ToolIds.SET_TIMER to listOf("com.android.alarm.permission.SET_ALARM"),
        Tier0ToolIds.OPEN_SYSTEM_SETTINGS to emptyList(),
    )

    private object NoopWorker : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            ToolResult.Failed(com.sidr.launcher.domain.intent.CommandFailure.Generic)
    }

    private fun productionFederation() = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), NoopWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), NoopWorker),
        ),
    )

    private fun declaredPermissions(): Set<String> {
        assertTrue("missing app manifest: ${manifestFile.canonicalPath}", manifestFile.isFile)
        return Regex("""<uses-permission\b[^>]*?/?>""")
            .findAll(manifestFile.readText())
            .filterNot { it.value.contains("""tools:node="remove"""") }
            .mapNotNull { Regex("""android:name\s*=\s*"([^"]+)"""").find(it.value)?.groupValues?.get(1) }
            .toSet()
    }

    @Test
    fun `every registered tool has a permission row`() {
        val registered = productionFederation().registry.all().map { it.id }
        val unrowed = registered.filterNot { it in toolPermissions }
        assertEquals(
            "A registered tool with no row here can ship needing a permission nobody declared — the " +
                "2026-09-05 defect exactly. Add a row (use emptyList() to mean 'needs none'): " +
                "${unrowed.map { it.value }}",
            emptyList<ToolId>(),
            unrowed,
        )
    }

    @Test
    fun `every permission a registered tool needs is declared in the manifest`() {
        val declared = declaredPermissions()
        val registered = productionFederation().registry.all().map { it.id }.toSet()
        val missing = toolPermissions
            .filterKeys { it in registered }
            .flatMap { (id, needed) -> needed.filterNot { it in declared }.map { id.value to it } }
        assertEquals(
            "Registered, reachable, and refused by ActivityTaskManager at every invocation: $missing",
            emptyList<Pair<String, String>>(),
            missing,
        )
    }

    /**
     * Non-vacuity floor — **a floor, not an equality, and not a bare count either.**
     * `DoctrineGuardTest` (`app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt`) made and
     * fixed the identical mistake for the identical construction: `registered.size >= N` cannot tell an
     * adapter that keeps one tool and silently drops another from one that keeps both, because the count
     * survives either way. It is also about to stop meaning anything at all: a later task in this block
     * registers a third adapter over app shortcuts read from the device, whose tool count is
     * device-dependent — different on every phone. A count floor asserts nothing the day that adapter
     * lands.
     *
     * **What this actually buys, stated precisely after a round-1 review finding.** [REQUIRED_TOOL_IDS]
     * is deliberately its **own literal**, spelled independently of [toolPermissions] rather than
     * derived from its keys — a first draft of this fix read `toolPermissions.keys`, and the re-review
     * caught what that does: a developer who drops a tool from its source *and* tidies away its
     * now-unused row in `toolPermissions` in the same commit — good faith, not adversarial — moves both
     * lists together, because both were reading one literal. This test would have stayed green through
     * exactly the drop it exists to catch.
     *
     * With the ids spelled separately, dropping a tool from its source **and** tidying away its now-unused
     * `toolPermissions` row in the same commit is now **two** separately-titled edits to this file
     * (removing a line from [REQUIRED_TOOL_IDS] and removing a line from `toolPermissions`) rather than
     * one — this test catches the case where only the source drops (the row is left behind) outright, and
     * makes the case where both drop together require a second, separate edit to stay silent, rather than
     * catching that second case via some oracle independent of this file. It is exactly as strong as the
     * fact that [REQUIRED_TOOL_IDS] and `toolPermissions` are two lists a reader must edit separately, and
     * no stronger: nothing here re-derives the four ids from anywhere outside this file.
     * `every registered tool has a permission row` is what keeps `toolPermissions` itself honest against
     * the registry in the *other* direction (a tool registered with no row at all); this test is not a
     * substitute for that one, and neither is a substitute for a scan of the real adapter sources.
     *
     * Must stay containment rather than `assertEquals` on the whole set: a legitimate fifth tool (A1″'s
     * entire content) must leave this GREEN.
     */
    @Test
    fun `the registry this guard reads contains the tools this federation is known to ship`() {
        val registered = productionFederation().registry.all().map { it.id }
        val missing = REQUIRED_TOOL_IDS.filterNot { it in registered }
        assertEquals(
            "Every assertion here loops over the production registry. A tool this federation is known " +
                "to ship that silently stops being registered must turn this red — a count cannot see " +
                "that, it can only see the list shrink below some number, which stops meaning anything " +
                "once the registry has a device-dependent adapter. Missing: ${missing.map { it.value }}",
            emptyList<ToolId>(),
            missing,
        )
    }

    @Test
    fun `the manifest scan reads real permissions`() {
        assertTrue(
            "The scan found no known permission — the manifest moved or the attribute spelling changed, " +
                "and the totality test above is now vacuous.",
            "android.permission.INTERNET" in declaredPermissions(),
        )
    }

    private companion object {
        /**
         * The non-vacuity floor, never the ceiling. Spelled as its own literal — **not**
         * `toolPermissions.keys` — on purpose: see the KDoc on the test below that reads this constant
         * for the round-1 fix that derived it from [toolPermissions] instead, and the round-2 finding
         * that reverted it, because deriving the floor from [toolPermissions] means a tidy-up that
         * deletes a row there deletes the floor's own check of that same id in the same edit.
         */
        val REQUIRED_TOOL_IDS: List<ToolId> = listOf(
            ToolIds.LAUNCH_APP,
            ToolIds.PLAY_STORE_SEARCH,
            Tier0ToolIds.SET_TIMER,
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
        )
    }
}
