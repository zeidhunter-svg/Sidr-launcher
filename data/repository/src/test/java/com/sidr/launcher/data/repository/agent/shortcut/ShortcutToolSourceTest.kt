package com.sidr.launcher.data.repository.agent.shortcut

import com.sidr.launcher.data.repository.agent.DynamicToolName
import com.sidr.launcher.data.repository.agent.Tier0ToolIds
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolLevels
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The third adapter's registry face. Everything here runs over the [ShortcutQuery] seam, so no
 * Robolectric and no `LauncherApps` — the Android call lives one layer down in `AndroidShortcutQuery`,
 * whose behaviour is measured on the device rather than tested here
 * (`docs/superpowers/plans/2026-09-12-a1-device-measurements.md`).
 */
class ShortcutToolSourceTest {

    @Test
    fun `every shortcut becomes one descriptor with shortcut provenance`() {
        val catalog = fakeCatalogOf(shortcut("com.a", "new_chat", "Telegram", "New message"))
        val source = ShortcutToolSource(catalog.catalog)

        val descriptor = source.all().single()

        assertEquals(ToolId("shortcut:com.a/new_chat"), descriptor.id)
        assertEquals(ToolLevels.APP_SHORTCUT, descriptor.level)
        assertEquals(ToolEffect.EXTERNAL, descriptor.effect)
        assertEquals(ActionRiskLevel.SAFE, descriptor.risk)
        assertEquals(ToolDurability.TRANSIENT, descriptor.durability)
        assertEquals(emptyList<ActionArg>(), descriptor.argSchema)
        assertEquals(emptyList<ActionArg>(), descriptor.outputSchema)
    }

    /**
     * **What this proves is id *stability*, and nothing beyond it** (fix round 1, finding 7). The id is
     * derived from the package and the shortcut id alone, so a refresh that re-labels a shortcut — a
     * locale change, an app update — leaves it byte-identical. That is the precondition for a persisted
     * plan resolving after a restart; it is **not** the same statement, and the earlier name of this
     * test said the stronger thing.
     *
     * The gap, named here because this is where its reader will be: [ShortcutCatalog]'s snapshot starts
     * **empty** and is filled asynchronously by `ShortcutRefreshTrigger`. In the window before the first
     * refresh lands, `ShortcutToolSource.find` answers `null` for a perfectly valid shortcut id, so a
     * restored session whose plan names one does not merely degrade: `InvocationValidator` rejects the
     * invocation, `AgentExecutor` ends the session `Failed`, and the row is cascade-deleted.
     *
     * **Reachable as of A1″ Task 11 (`92e8704`)**, not merely predicted: `ToolMatchPlanner` now selects
     * through `ToolSelector`, whose dynamic branch returns exactly these ids, so a persisted plan can
     * name a shortcut tool. Task 11's ruling was fail-closed rather than new machinery — a restore inside
     * the startup window dies (`Failed` + cascade delete) exactly as described above, rather than pausing
     * to wait for the refresh it needed. Choosing to wait, or to re-plan, instead of failing is a
     * deliberate widening this block's DoD does not cover, and stays addressed onward.
     */
    @Test
    fun `an id is stable across a refresh that re-labels the same shortcut`() {
        val catalog = fakeCatalogOf(shortcut("com.a", "new_chat", "Telegram", "New message"))
        val before = ShortcutToolSource(catalog.catalog).all().single().id

        catalog.replaceWith(shortcut("com.a", "new_chat", "Telegram", "Новое сообщение"))

        assertEquals(before, ShortcutToolSource(catalog.catalog).all().single().id)
    }

    @Test
    fun `no shortcut id can collide with an authored id`() {
        val authored = listOf(
            ToolIds.LAUNCH_APP,
            ToolIds.PLAY_STORE_SEARCH,
            Tier0ToolIds.SET_TIMER,
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
        )
        assertTrue(authored.none { it.value.startsWith(ShortcutToolIds.PREFIX) })
    }

    @Test
    fun `names carry the app as qualifier and the shortcut as name`() {
        val source = ShortcutToolSource(
            fakeCatalogOf(shortcut("com.a", "new_chat", "Telegram", "New message")).catalog,
        )
        assertEquals(
            listOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "New message")),
            source.names(),
        )
    }

    @Test
    fun `find resolves a derived id and refuses an unknown one`() {
        val source = ShortcutToolSource(
            fakeCatalogOf(shortcut("com.a", "new_chat", "Telegram", "New message")).catalog,
        )
        assertEquals(ToolId("shortcut:com.a/new_chat"), source.find(ToolId("shortcut:com.a/new_chat"))?.id)
        assertNull(source.find(ToolId("shortcut:com.a/gone")))
        assertNull(source.find(Tier0ToolIds.SET_TIMER))
    }

    /**
     * Fail-closed, the direction the catalog's own KDoc argues for: a tool that is advertised and
     * cannot be run is the failure that cost A1′ its first acceptance. A blank shortcut id does not
     * round-trip through [ShortcutToolIds.parse], so the worker could never route it — it is therefore
     * never offered, in either face.
     */
    @Test
    fun `a shortcut whose id cannot round-trip is advertised in neither face`() {
        val source = ShortcutToolSource(
            fakeCatalogOf(
                shortcut("com.a", "", "Telegram", "New message"),
                shortcut("com.a", "new_chat", "Telegram", "New message"),
            ).catalog,
        )

        assertEquals(listOf(ToolId("shortcut:com.a/new_chat")), source.all().map { it.id })
        assertEquals(listOf(ToolId("shortcut:com.a/new_chat")), source.names().map { it.id })
    }

    /**
     * The other half of the round-trip filter, and the one a `!= null` check cannot see (fix round 1,
     * finding 8). [AppShortcut.packageName] is a plain `String` with no validation behind it, so a value
     * carrying a `/` re-parses into a **different** pair: `of("com.a/b", "x")` is `shortcut:com.a/b/x`,
     * which [ShortcutToolIds.parse] reads back as `("com.a", "b/x")`. That is not `null`, so a
     * null-check admits it — and `ShortcutToolWorker` would then ask `LauncherApps` to start shortcut
     * `b/x` in package `com.a`: a different shortcut, or none.
     */
    @Test
    fun `a shortcut whose id round-trips to a different pair is advertised in neither face`() {
        val source = ShortcutToolSource(
            fakeCatalogOf(
                shortcut("com.a/b", "x", "Telegram", "New message"),
                shortcut("com.a", "new_chat", "Telegram", "New message"),
            ).catalog,
        )

        assertEquals(listOf(ToolId("shortcut:com.a/new_chat")), source.all().map { it.id })
        assertEquals(listOf(ToolId("shortcut:com.a/new_chat")), source.names().map { it.id })
    }

    @Test
    fun `an empty catalog advertises nothing rather than failing`() {
        val source = ShortcutToolSource(fakeCatalogOf().catalog)
        assertEquals(emptyList<ToolId>(), source.all().map { it.id })
        assertEquals(emptyList<DynamicToolName>(), source.names())
    }
}

/** A real [ShortcutCatalog] over a mutable backing list, so a refresh can be observed end to end. */
internal class FakeShortcutCatalog(initial: List<AppShortcut>) {
    private var backing: List<AppShortcut> = initial
    val catalog = ShortcutCatalog(query = { backing }, ioDispatcher = UnconfinedTestDispatcher())

    init {
        runBlocking { catalog.refresh() }
    }

    fun replaceWith(vararg next: AppShortcut) {
        backing = next.toList()
        runBlocking { catalog.refresh() }
    }
}

internal fun fakeCatalogOf(vararg shortcuts: AppShortcut) = FakeShortcutCatalog(shortcuts.toList())

internal fun shortcut(
    packageName: String,
    shortcutId: String,
    appLabel: String,
    shortcutLabel: String,
) = AppShortcut(
    packageName = packageName,
    shortcutId = shortcutId,
    appLabel = appLabel,
    shortcutLabel = shortcutLabel,
)
