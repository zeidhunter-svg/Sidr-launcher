package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.domain.ai.bridge.ModelOutput
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Our vocabulary for the model (spec §7.12.1) against the tools that really exist. Read off the two
 * authored sources the bridge may offer — not off a list in this file — so a twentieth tool without a
 * description, or a description for a tool that is gone, is red here.
 */
class AuthoredToolDescriptionsTest {

    private val authored = SystemIntentToolSource(DefaultActionCatalog()).all() +
        Tier0IntentToolSource(ToolPermissionCatalog(), PermissionPresence { true }).all()

    private val descriptions = AuthoredToolDescriptions()

    @Test
    fun `every authored tool at an offered level has a description`() {
        val missing = authored.map { it.id }.filter { descriptions.describe(it) == null }

        assertEquals(emptyList<ToolId>(), missing)
        // The control: the two sources really are the nineteen (spec §7.12.3), so "nothing missing"
        // cannot mean "nothing read".
        assertEquals(19, authored.size)
    }

    @Test
    fun `every description names a tool the authored sources register`() {
        assertEquals(emptySet<ToolId>(), descriptions.rows().keys - authored.map { it.id }.toSet())
    }

    /** The binder reads ONE name; every app-sort tool must speak it, and nothing else may. */
    @Test
    fun `an app-sort tool takes exactly one mention, under the name the binder reads`() {
        val sorts = CatalogArgumentSorts()
        descriptions.rows().forEach { (id, description) ->
            val expected = if (sorts.appArgOf(id) != null) listOf(AuthoredToolDescriptions.MENTION_ARG) else emptyList()
            assertEquals(id.value, expected, description.args.filter { it.mention }.map { it.name })
        }
    }

    @Test
    fun `every other model argument is an argument the tool declares, with the same requiredness`() {
        val byId = authored.associateBy { it.id }
        descriptions.rows().forEach { (id, description) ->
            description.args.filterNot { it.mention }.forEach { arg ->
                val declared = byId.getValue(id).argSchema.singleOrNull { it.name == arg.name }
                assertTrue("${id.value}.${arg.name} is not declared by the tool", declared != null)
                assertEquals("${id.value}.${arg.name}", declared!!.required, arg.required)
            }
        }
    }

    /**
     * The other direction (spec §7.12.1: the descriptions cover the arguments). A required argument the
     * model is never asked for and the device does not bind is one nobody can supply: the tool is still
     * offered — the filter needs only a row — and dead. The device-bound set is read from
     * [CatalogArgumentSorts], not written here.
     */
    @Test
    fun `every required argument a tool declares is either asked of the model or bound by the device`() {
        val sorts = CatalogArgumentSorts()
        val uncovered = authored.flatMap { descriptor ->
            val description = descriptions.describe(descriptor.id) ?: return@flatMap emptyList()
            val asked = description.args.map { it.name }.toSet()
            val bound = sorts.appArgOf(descriptor.id)?.let { (arg, sorted) -> setOfNotNull(arg, sorted.labelArg) } ?: emptySet()
            descriptor.argSchema
                .filter { it.required && it.name !in asked && it.name !in bound }
                .map { descriptor.id.value to it.name }
        }

        assertEquals(emptyList<Pair<String, String>>(), uncovered)
    }

    /**
     * B-8 (A4′ phase 3b, Task 8): Android describes no tool output to the model, so the Android prompt never
     * mentions references — the reference sentence, the `ref` marker and the schema's reference form all
     * need an output in the offering (`BridgeRenderingTest`). Nor does any argument accept a reference: with
     * nothing to name, one would be inert today and a silent widening the day an output is added. The JVM
     * sandbox's descriptions declare theirs (Task 13).
     */
    @Test
    fun `no authored tool describes an output on Android yet`() {
        val rows = descriptions.rows()

        assertEquals(emptyMap<ToolId, List<ModelOutput>>(), rows.mapValues { it.value.outputs }.filterValues { it.isNotEmpty() })
        assertEquals(
            emptyList<String>(),
            rows.flatMap { (id, description) -> description.args.filter { it.acceptsRef }.map { "${id.value}.${it.name}" } },
        )
        // The control: the rows were read — all nineteen — so "none" cannot mean "nothing looked at".
        assertEquals(19, rows.size)
    }
}
