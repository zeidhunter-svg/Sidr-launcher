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

    @Test
    fun `an id survives a refresh so a persisted plan still resolves`() {
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
