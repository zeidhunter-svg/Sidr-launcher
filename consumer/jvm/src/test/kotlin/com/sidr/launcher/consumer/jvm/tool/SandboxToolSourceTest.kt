package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three tools exist to answer Master Plan §3.1a question 1 — does `ToolDescriptor` stretch to
 * three arities — inside one real plan rather than as a registry curiosity. Each assertion below is
 * one leg of that answer.
 */
class SandboxToolSourceTest {

    private val source = SandboxToolSource()

    @Test
    fun `workspace_info is the zero-arity leg and still declares an output`() {
        val d = source.find(SandboxToolIds.WORKSPACE_INFO)!!
        assertTrue("zero arity means an empty argSchema", d.argSchema.isEmpty())
        assertEquals(listOf(SandboxKeys.ROOT), d.outputSchema.map { it.name })
        assertEquals(ActionRiskLevel.SAFE, d.risk)
        assertEquals(ToolDurability.TRANSIENT, d.durability)
        assertNull(d.permissionGate)
    }

    @Test
    fun `find_file is the typed-arguments leg with two args and one output`() {
        val d = source.find(SandboxToolIds.FIND_FILE)!!
        assertEquals(listOf(SandboxKeys.QUERY, SandboxKeys.ROOT), d.argSchema.map { it.name })
        assertTrue("both args are required", d.argSchema.all { it.required })
        assertEquals(listOf(SandboxKeys.RESOLVED_PATH), d.outputSchema.map { it.name })
        assertEquals(ActionRiskLevel.SAFE, d.risk)
        assertEquals(ToolDurability.TRANSIENT, d.durability)
    }

    /**
     * The first `DANGEROUS` and the first `DURABLE` tool this project has ever registered.
     * `ActionRiskLevel`'s own KDoc still says `DANGEROUS` is "not producible in the MVP"; on the second
     * consumer it is, and that asymmetry against `play_store_search`'s `CONFIRM` is the evidence for
     * spec §11.3 — two adapters over one core-owned scale, disagreeing about what a comparable act is
     * worth.
     */
    @Test
    fun `delete_file is DANGEROUS and DURABLE, and declares no output`() {
        val d = source.find(SandboxToolIds.DELETE_FILE)!!
        assertEquals(listOf(SandboxKeys.PATH), d.argSchema.map { it.name })
        assertTrue("a tool that produces nothing declares nothing", d.outputSchema.isEmpty())
        assertEquals(ActionRiskLevel.DANGEROUS, d.risk)
        assertEquals(ToolDurability.DURABLE, d.durability)
    }

    @Test
    fun `the registry holds exactly these three and nothing else`() {
        assertEquals(
            listOf("workspace_info", "find_file", "delete_file"),
            source.all().map { it.id.value },
        )
        assertNull(source.find(ToolId("launch_app")))
    }
}
