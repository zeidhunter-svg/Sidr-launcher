package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.agent.shortcut.AppShortcut
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.di.AgentProvidesModule
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.feature.launcher.agent.DynamicToolLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The `:data:repository` → `:feature:launcher` projection at the composition root, held (fix round 1,
 * finding 10).
 *
 * `AgentProvidesModule.provideDynamicToolLabels` is the whole reason `:app` is in this path at all:
 * `:feature:launcher` may not depend on `:data:repository`, so the ViewModel cannot hold
 * `DynamicToolNames` and the data-layer type cannot move into `:domain` either — a shortcut's name is a
 * third-party string and `:domain` carries no user-facing copy. Before this file, **no test exercised
 * that projection**, and the module's own KDoc made a same-instance claim that nothing held.
 *
 * Three properties, each failing on its own thing: the two halves of a name survive the hop; the names
 * face and the registry face are literally the same object; and the projection re-reads rather than
 * capturing a map that would be empty forever.
 *
 * `AgentProvidesModule` is a Kotlin `object`, so each provider is an ordinary call from here — the real
 * production function, not a re-implementation of it.
 */
class DynamicToolLabelWiringTest {

    private class MutableCatalog(initial: List<AppShortcut>) {
        private var backing = initial
        val catalog = ShortcutCatalog(query = { backing }, ioDispatcher = Dispatchers.Unconfined)

        init {
            runBlocking { catalog.refresh() }
        }

        fun replaceWith(vararg next: AppShortcut) {
            backing = next.toList()
            runBlocking { catalog.refresh() }
        }
    }

    private fun shortcut(pkg: String, id: String, app: String, label: String) =
        AppShortcut(packageName = pkg, shortcutId = id, appLabel = app, shortcutLabel = label)

    @Test
    fun `the projection carries both halves of a dynamic name across the module boundary`() {
        val source = ShortcutToolSource(
            MutableCatalog(listOf(shortcut("com.a", "new_chat", "Telegram", "New message"))).catalog,
        )

        val labels = AgentProvidesModule.provideDynamicToolLabels(
            AgentProvidesModule.provideDynamicToolNames(source),
        )

        assertEquals(
            "The qualifier and the name must arrive as two halves. Joining them anywhere below the " +
                "feature layer would put copy — the punctuation between two nouns — in :data:repository " +
                "or in a ViewModel, which the hard rule forbids; the join belongs to " +
                "launcher_agent_step_shortcut.",
            mapOf(ToolId("shortcut:com.a/new_chat") to DynamicToolLabel("Telegram", "New message")),
            labels.current(),
        )
    }

    @Test
    fun `the names face and the registry face are one instance, so a name cannot label a tool that is gone`() {
        val catalog = MutableCatalog(listOf(shortcut("com.a", "new_chat", "Telegram", "New message")))
        val source = ShortcutToolSource(catalog.catalog)

        assertSame(
            "provideDynamicToolNames must hand back the SAME object the federation registers. Providing " +
                "a second construction would let a rendered name disagree with the registered tool it " +
                "labels — the F2 shape in miniature.",
            source,
            AgentProvidesModule.provideDynamicToolNames(source),
        )

        val labels = AgentProvidesModule.provideDynamicToolLabels(
            AgentProvidesModule.provideDynamicToolNames(source),
        )
        catalog.replaceWith(shortcut("com.b", "compose", "Mail", "Write"))

        assertEquals(
            "One snapshot feeds both faces, so after a refresh the labelled ids and the registered ids " +
                "must still be the same set.",
            source.all().map { it.id }.toSet(),
            labels.current().keys,
        )
    }

    @Test
    fun `the projection re-reads, so a shortcut discovered after graph construction is named`() {
        // The state the graph is actually built in: ShortcutRefreshTrigger has not run yet, so the
        // catalog is empty. A map captured here would be the empty one for the life of the process.
        val catalog = MutableCatalog(emptyList())
        val labels = AgentProvidesModule.provideDynamicToolLabels(
            AgentProvidesModule.provideDynamicToolNames(ShortcutToolSource(catalog.catalog)),
        )
        assertEquals(emptyMap<ToolId, DynamicToolLabel>(), labels.current())

        catalog.replaceWith(shortcut("com.a", "new_chat", "Telegram", "New message"))

        assertEquals(
            "Adapter #3's tool set moves while the process lives. A projection that captured its map at " +
                "graph construction would render every shortcut tool with no name at all — the same " +
                "generic-line defect DoctrineGuardTest's label rule exists to catch.",
            mapOf(ToolId("shortcut:com.a/new_chat") to DynamicToolLabel("Telegram", "New message")),
            labels.current(),
        )
    }
}
