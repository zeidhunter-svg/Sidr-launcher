package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
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
}
