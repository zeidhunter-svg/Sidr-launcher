package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionRiskLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
