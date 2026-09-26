package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionRiskLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolFederationTest {

    private fun descriptor(id: String, level: ToolLevel) = ToolDescriptor(
        id = ToolId(id),
        level = level,
        effect = ToolEffect.EXTERNAL,
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    private class FixedRegistry(private val items: List<ToolDescriptor>) : ToolRegistry {
        override fun all(): List<ToolDescriptor> = items
        override fun find(id: ToolId): ToolDescriptor? = items.firstOrNull { it.id == id }
    }

    /**
     * A source whose tool set moves after construction — what every source was not until A1″.
     * [FixedRegistry] cannot express this, which is why it is a second fake rather than a parameter.
     */
    private class MutableRegistry(var tools: List<ToolDescriptor>) : ToolRegistry {
        override fun all(): List<ToolDescriptor> = tools
        override fun find(id: ToolId): ToolDescriptor? = tools.firstOrNull { it.id == id }
    }

    /** A level with no `ToolLevels` constant: nothing about dynamism is level-specific. */
    private val dynamic = ToolLevel("dynamic")

    private class NamedWorker(val name: String) : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            ToolResult.Effected(ToolOutput(mapOf("worker" to name)))
    }

    private fun federation(vararg adapters: ToolAdapter) = ToolFederation(adapters.toList())

    private fun adapter(level: ToolLevel, workerName: String, vararg ids: String) = ToolAdapter(
        level = level,
        registry = FixedRegistry(ids.map { descriptor(it, level) }),
        worker = NamedWorker(workerName),
    )

    @Test
    fun `all concatenates its sources in adapter order`() {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "set_timer"),
        )

        assertEquals(listOf("launch_app", "set_timer"), f.registry.all().map { it.id.value })
    }

    @Test
    fun `find reaches a tool in any source`() {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "set_timer"),
        )

        assertEquals(ToolLevels.SYSTEM_INTENT, f.registry.find(ToolId("set_timer"))?.level)
        assertNull(f.registry.find(ToolId("nothing")))
    }

    @Test
    fun `on a colliding id the first adapter wins and the later declaration is dropped`() {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "launch_app"),
        )

        // Not symmetric exclusion: dropping both would let a later source take a built-in down with
        // it by naming a collision. The built-in survives; the intruder is what disappears.
        assertEquals(listOf(ToolLevels.IN_APP), f.registry.all().map { it.level })
        assertEquals(ToolLevels.IN_APP, f.registry.find(ToolId("launch_app"))?.level)
    }

    @Test
    fun `the executor routes each id to the worker of the adapter that declares it`() = runTest {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "set_timer"),
        )

        val launched = f.executor.invoke(ResolvedInvocation(ToolId("launch_app")))
        val timed = f.executor.invoke(ResolvedInvocation(ToolId("set_timer")))

        assertEquals(ToolOutput(mapOf("worker" to "in_app")), (launched as ToolResult.Effected).output)
        assertEquals(ToolOutput(mapOf("worker" to "system")), (timed as ToolResult.Effected).output)
    }

    @Test
    fun `a dropped collision is not routable either - the registry and the dispatcher cannot disagree`() = runTest {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "launch_app"),
        )

        assertEquals(
            ToolOutput(mapOf("worker" to "in_app")),
            (f.executor.invoke(ResolvedInvocation(ToolId("launch_app"))) as ToolResult.Effected).output,
        )
    }

    @Test
    fun `a source that gains a tool after construction is visible through both faces`() = runTest {
        val source = MutableRegistry(listOf(descriptor("first", dynamic)))
        val f = federation(ToolAdapter(dynamic, source, NamedWorker("dynamic")))

        assertEquals(listOf("first"), f.registry.all().map { it.id.value })

        source.tools = source.tools + descriptor("second", dynamic)

        assertEquals(listOf("first", "second"), f.registry.all().map { it.id.value })
        assertNotNull(f.registry.find(ToolId("second")))

        // Routing, not just advertising: the dispatcher must see the gain through the same read.
        val routed = f.executor.invoke(ResolvedInvocation(ToolId("second")))
        assertEquals(ToolOutput(mapOf("worker" to "dynamic")), (routed as ToolResult.Effected).output)
    }

    @Test
    fun `a source that loses a tool stops advertising and stops routing it`() = runTest {
        val source = MutableRegistry(listOf(descriptor("first", dynamic), descriptor("second", dynamic)))
        val f = federation(ToolAdapter(dynamic, source, NamedWorker("dynamic")))

        source.tools = source.tools.filter { it.id.value != "second" }

        assertNull(f.registry.find(ToolId("second")))
        assertTrue(f.executor.invoke(ResolvedInvocation(ToolId("second"))) is ToolResult.Failed)
    }
}
