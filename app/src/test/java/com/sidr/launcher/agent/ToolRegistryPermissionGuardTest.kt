package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.PermissionPresence
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0ToolIds
import com.sidr.launcher.data.repository.agent.ToolPermissionCatalog
import com.sidr.launcher.data.repository.agent.shortcut.AppShortcut
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolIds
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
 * **What it says about shortcut tools, and what it cannot.** It has a row for the family
 * ([shortcutToolPermissions]) rather than per id, because a shortcut id is device-dependent, and a
 * floor in [productionFederation] keeps that row from being consulted for nothing. What it therefore
 * answers is "is what we claim the `app_shortcut` family needs actually declared" — not "is the family
 * available", which is the `android.app.role.HOME` runtime role and is not answerable from a manifest
 * at all (see that row's own KDoc).
 *
 * What this class cannot check, said rather than implied — **and the address moved in Task 4.**
 * [toolPermissions] is no longer a column written out here; it reads `ToolPermissionCatalog.ROWS` from
 * production, so the old "this file's hand-written list is as strong as it is honest" no longer names
 * anything in this file. The weakness itself did not go away, it relocated: a **false `emptyList()`
 * row in `ToolPermissionCatalog.ROWS`** still leaves every test here green and the app broken, because
 * the manifest check below can only verify the permissions a row *claims*, never the ones the platform
 * will actually demand. That is an observed boundary rather than a worry — Task 4's mutation 6
 * measured exactly it, and the guard stayed green.
 *
 * What the class does buy is **totality over the registry**: a tool registered by any adapter, with or
 * without a worker branch, with its intent built inline or in a helper three files away, is red until
 * someone writes its row. Since Task 2 that totality has one hole of its own, closed elsewhere: a
 * tool whose row is *missing* is now withheld by its source and so is registered nowhere, leaving
 * nothing here to find. `Tier0IntentToolSourceTest`'s per-source floor is what turns that absence red.
 */
class ToolRegistryPermissionGuardTest {

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
    private val manifestFile = File(repoRoot, "app/src/main/AndroidManifest.xml")

    /**
     * Registered tool → the permissions the platform requires of its caller, **read from production**
     * (`ToolPermissionCatalog`) rather than written out here. The previous hand-written column is what
     * this class's own KDoc named as its weak point: it was exactly as green when wrong as when right.
     * Now a tool ships with whatever production declares, and the manifest check below is what makes
     * that declaration true or red.
     */
    private val toolPermissions: Map<ToolId, List<String>> = ToolPermissionCatalog().rows()

    /**
     * **The `app_shortcut` family needs no manifest permission, and that is a measured decision rather
     * than an omission.** A shortcut tool's id is device-dependent, so it can carry no per-id row; it is
     * excluded from the totality test above by its `shortcut:` prefix and answered here instead.
     *
     * **What this row actually holds, stated after fix round 1 found the earlier claim false.** It is
     * load-bearing in exactly one direction: change this list to name a permission the manifest does not
     * declare and `every permission a registered tool needs is declared in the manifest` goes red, for
     * every registered shortcut tool — which the floor in [productionFederation] guarantees is at least
     * one. It is **not** load-bearing in the other direction: leaving it `emptyList()` while the family
     * genuinely needed a permission is as green as being right, exactly as this class already says about
     * its hand-written [toolPermissions] column. Nothing here re-derives the answer from the platform;
     * the evidence is the measurement file, and the guard's job is to make a change to it visible.
     *
     * The list is empty because shortcut host access **is not governed by a manifest permission at
     * all**: it is the `android.app.role.HOME` runtime role, held by exactly one package at a time and
     * assigned by the user. `docs/superpowers/plans/2026-09-12-a1-device-measurements.md` §2 is the
     * evidence — no permission grant moved the answer (rows 3 and 4 could not even give the role from
     * the shell), the role flip moved it completely (rows 1 → 6), and row 10 shows `startShortcut`
     * needing nothing `getShortcuts` did not. This is the project's first tool source whose
     * availability is a role rather than an install-time permission.
     *
     * **Nothing here answers "is this source available", and nothing should be added that pretends
     * to.** This class checks that a registered tool's stated permissions are declared; a runtime role
     * is neither stated in a manifest nor answerable from one. The adapter answers it at the only place
     * it can be answered — at read time, by degrading to an empty tool set
     * (`AndroidShortcutQuery`/`ShortcutCatalog`), so a tool whose role is absent is never registered in
     * the first place and this guard has nothing to be wrong about.
     */
    private val shortcutToolPermissions: List<String> = emptyList()

    private object NoopWorker : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            ToolResult.Failed(com.sidr.launcher.domain.intent.CommandFailure.Generic)
    }

    /**
     * The federation every assertion here loops over, with **the third adapter's floor asserted in the
     * accessor** rather than in a test of its own — the placement `DoctrineGuardTest.productionAdapters`
     * uses, and for the same reason: a guard that loops over a list missing an adapter passes having
     * checked less than it claims.
     *
     * **Fix round 1, finding 2. Without this floor the whole third adapter was inert here**, and this
     * file's own KDoc said the opposite. Measured against the tree at the time: of the four tests,
     * `every registered tool has a permission row` filtered shortcut ids out explicitly,
     * `every permission a registered tool needs is declared in the manifest` flat-mapped
     * [shortcutToolPermissions] — `emptyList()`, so it yielded nothing whatever was registered —
     * `the registry this guard reads contains the tools this federation is known to ship` reads only
     * [REQUIRED_TOOL_IDS], and `the manifest scan reads real permissions` reads the manifest. Deleting
     * the adapter, [shortcutSource] and both prefix filters left every test green.
     *
     * The floor is what makes the addition load-bearing: remove the adapter, or leave it registering
     * nothing, and the three tests that read this accessor go red instead of quietly checking four
     * tools and calling it totality.
     */
    private fun productionFederation(): ToolFederation {
        val federation = ToolFederation(
            listOf(
                ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), NoopWorker),
                ToolAdapter(
                    ToolLevels.SYSTEM_INTENT,
                    Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything),
                    NoopWorker,
                ),
                ToolAdapter(ToolLevels.APP_SHORTCUT, shortcutSource(), NoopWorker),
            ),
        )

        val ids = federation.registry.all().map { it.id }
        assertEquals(
            "No app_shortcut tool reached this guard, so every assertion below skipped the whole third " +
                "adapter in silence and [shortcutToolPermissions] was consulted for nothing. " +
                "[REQUIRED_TOOL_IDS] cannot catch this — a shortcut id is device-dependent and so " +
                "cannot be written down. Registered: ${ids.map { it.value }}",
            true,
            ids.any { it.value.startsWith(ShortcutToolIds.PREFIX) },
        )

        return federation
    }

    /**
     * The third adapter, over a **non-empty** fake snapshot — an empty one would register no shortcut
     * tool, which the floor in [productionFederation] now turns red rather than letting it pass in
     * silence.
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
        val unrowed = registered
            .filterNot { it.value.startsWith(ShortcutToolIds.PREFIX) }
            .filterNot { it in toolPermissions }
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
            .flatMap { (id, needed) -> needed.filterNot { it in declared }.map { id.value to it } } +
            registered
                .filter { it.value.startsWith(ShortcutToolIds.PREFIX) }
                .flatMap { id ->
                    shortcutToolPermissions.filterNot { it in declared }.map { id.value to it }
                }
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
            Tier0ToolIds.SET_ALARM,
        )
    }
}
