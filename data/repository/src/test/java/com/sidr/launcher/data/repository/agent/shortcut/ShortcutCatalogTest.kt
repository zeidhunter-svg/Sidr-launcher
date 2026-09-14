package com.sidr.launcher.data.repository.agent.shortcut

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ShortcutCatalog] is exercised entirely through the [ShortcutQuery] seam — a plain
 * `() -> List<AppShortcut>` fun interface — so these tests need no Robolectric and no
 * `LauncherApps`: the Android call lives behind the seam, in `AndroidShortcutQuery`.
 */
class ShortcutCatalogTest {

    @Test
    fun `current is empty until a refresh has run`() {
        val catalog = ShortcutCatalog(query = { emptyList() }, ioDispatcher = UnconfinedTestDispatcher())
        assertEquals(emptyList<AppShortcut>(), catalog.current())
    }

    @Test
    fun `a refresh replaces the snapshot wholesale`() = runTest {
        var backing = listOf(shortcut("com.a", "new_chat"))
        val catalog = ShortcutCatalog(query = { backing }, ioDispatcher = UnconfinedTestDispatcher())

        catalog.refresh()
        assertEquals(listOf("new_chat"), catalog.current().map { it.shortcutId })

        backing = listOf(shortcut("com.b", "new_tab"))
        catalog.refresh()
        assertEquals(listOf("new_tab"), catalog.current().map { it.shortcutId })
    }

    @Test
    fun `a query that fails leaves an empty snapshot rather than propagating`() = runTest {
        val catalog = ShortcutCatalog(
            query = { throw SecurityException("caller is not the default launcher") },
            ioDispatcher = UnconfinedTestDispatcher(),
        )

        catalog.refresh()

        assertEquals(emptyList<AppShortcut>(), catalog.current())
    }

    private fun shortcut(packageName: String, shortcutId: String) = AppShortcut(
        packageName = packageName,
        shortcutId = shortcutId,
        appLabel = "App $packageName",
        shortcutLabel = "Shortcut $shortcutId",
    )
}
