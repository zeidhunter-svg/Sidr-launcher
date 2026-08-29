package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandFailure
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A0's fail-closed argument gate, modeled on `ProposalValidator`. It runs before **every** step, not
 * once at planning time: a plan can outlive a process restart, and by the time it resumes the app may
 * be gone or a permission revoked.
 *
 * F6 split it in two. [InvocationValidator.validate] is the **shape** check — everything decidable
 * without having run anything, so it can run at plan time and again on every resume.
 * [InvocationValidator.resolve] is the **binding**: it is the only producer of [ResolvedInvocation],
 * and therefore the only way a value can reach the world.
 */
class InvocationValidatorTest {

    private val launchApp = ToolDescriptor(
        id = ToolIds.LAUNCH_APP,
        level = ToolLevels.IN_APP,
        effect = ToolEffect.EXTERNAL,
        argSchema = listOf(ActionArg("query", description = "The app name to launch")),
        outputSchema = listOf(ActionArg("resolved_query", description = "What was searched for")),
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    private val playStore = ToolDescriptor(
        id = ToolIds.PLAY_STORE_SEARCH,
        level = ToolLevels.IN_APP,
        effect = ToolEffect.EXTERNAL,
        argSchema = listOf(ActionArg("query", description = "The app name to find")),
        risk = ActionRiskLevel.CONFIRM,
        durability = ToolDurability.TRANSIENT,
    )

    private val registry = object : ToolRegistry {
        override fun all(): List<ToolDescriptor> = listOf(launchApp, playStore)
        override fun find(id: ToolId): ToolDescriptor? = all().firstOrNull { it.id == id }
    }

    private fun literal(value: String) = mapOf("query" to ArgSource.Literal(value))

    // --- validate: the three original reasons, unchanged by F6 ----------------------------------

    @Test
    fun `a well-formed invocation is valid`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, literal("убер")),
            precedingTools = emptyList(),
            registry = registry,
        )
        assertEquals(InvocationCheck.Valid, check)
    }

    @Test
    fun `an unregistered tool is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolId("not_registered"), literal("убер")),
            precedingTools = emptyList(),
            registry = registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.UNKNOWN_TOOL), check)
    }

    @Test
    fun `an argument outside the declared schema is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(
                ToolIds.LAUNCH_APP,
                literal("убер") + ("sudo" to ArgSource.Literal("true")),
            ),
            precedingTools = emptyList(),
            registry = registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.UNDECLARED_ARG), check)
    }

    @Test
    fun `a missing required argument is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, emptyMap()),
            precedingTools = emptyList(),
            registry = registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.MISSING_REQUIRED_ARG), check)
    }

    @Test
    fun `a blank required literal is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, literal("   ")),
            precedingTools = emptyList(),
            registry = registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.MISSING_REQUIRED_ARG), check)
    }

    // --- validate: the two static F6 reasons ----------------------------------------------------

    @Test
    fun `a binding to an earlier step's declared output is valid`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            precedingTools = listOf(ToolIds.LAUNCH_APP),
            registry = registry,
        )
        assertEquals(InvocationCheck.Valid, check)
    }

    @Test
    fun `a binding to the step's own index is rejected as a forward reference`() {
        // precedingTools.size IS this step's index, so index 1 here names the step doing the binding.
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(1, "resolved_query"))),
            precedingTools = listOf(ToolIds.LAUNCH_APP),
            registry = registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.FORWARD_ARG_SOURCE), check)
    }

    @Test
    fun `a binding to a later step is rejected as a forward reference`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(2, "resolved_query"))),
            precedingTools = listOf(ToolIds.LAUNCH_APP),
            registry = registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.FORWARD_ARG_SOURCE), check)
    }

    @Test
    fun `a binding to an index the plan does not have is rejected as a forward reference`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(-1, "resolved_query"))),
            precedingTools = listOf(ToolIds.LAUNCH_APP),
            registry = registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.FORWARD_ARG_SOURCE), check)
    }

    @Test
    fun `a binding to a key the source tool never declared is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "package_name"))),
            precedingTools = listOf(ToolIds.LAUNCH_APP),
            registry = registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.UNDECLARED_OUTPUT), check)
    }

    @Test
    fun `a binding to a source tool that declares no outputs at all is rejected`() {
        // `play_store_search` promises nothing, so nothing may be bound from it — the shape a plan
        // restored against a build whose tool stopped declaring that output arrives in.
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            precedingTools = listOf(ToolIds.PLAY_STORE_SEARCH),
            registry = registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.UNDECLARED_OUTPUT), check)
    }

    @Test
    fun `a required argument satisfied by a binding is not a missing argument`() {
        // The value does not exist yet at shape-check time; `resolve` owns whether it arrives.
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            precedingTools = listOf(ToolIds.LAUNCH_APP),
            registry = registry,
        )
        assertEquals(InvocationCheck.Valid, check)
    }

    @Test
    fun `the ends of a binding must agree on type`() {
        // Vacuous today on purpose: `ArgType` has exactly one value, so the check cannot currently be
        // made to fail. The seam is laid and exercised, not demonstrated — §6.2 states that in the
        // same terms as the DURABLE gate. When a second `ArgType` arrives, this test gains a mismatch
        // case and the comparison is already in place.
        val target = playStore.argSchema.single { it.name == "query" }
        val source = launchApp.outputSchema.single { it.name == "resolved_query" }
        assertEquals(target.type, source.type)

        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            precedingTools = listOf(ToolIds.LAUNCH_APP),
            registry = registry,
        )
        assertEquals(InvocationCheck.Valid, check)
    }

    // --- resolve: the binding half --------------------------------------------------------------

    @Test
    fun `a literal resolves to itself`() {
        val result = InvocationValidator.resolve(
            ToolInvocation(ToolIds.LAUNCH_APP, literal("убер")),
            observations = emptyMap(),
        )
        assertEquals(
            ResolutionResult.Resolved(ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер"))),
            result,
        )
    }

    @Test
    fun `a binding resolves to the producing step's value`() {
        val result = InvocationValidator.resolve(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            observations = mapOf(
                0 to ToolResult.Observed(
                    ObservedFact.APP_NOT_INSTALLED,
                    ToolOutput(mapOf("resolved_query" to "убер")),
                ),
            ),
        )
        assertEquals(
            ResolutionResult.Resolved(ResolvedInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to "убер"))),
            result,
        )
    }

    @Test
    fun `a binding also resolves from an Effected result's output`() {
        // Outputs are a property of the tool, not of the branch it took.
        val result = InvocationValidator.resolve(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            observations = mapOf(0 to ToolResult.Effected(ToolOutput(mapOf("resolved_query" to "убер")))),
        )
        assertEquals(
            ResolutionResult.Resolved(ResolvedInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to "убер"))),
            result,
        )
    }

    @Test
    fun `a binding to a step that failed produces no ResolvedInvocation`() {
        val result = InvocationValidator.resolve(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            observations = mapOf(0 to ToolResult.Failed(CommandFailure.Generic)),
        )
        // No `assertTrue(result !is Resolved)` follow-up: after the assertEquals above it cannot fail,
        // and an assertion that cannot fail is the defect this file's own subject is about (the F2/D10
        // family). The equality carries the whole claim.
        assertEquals(ResolutionResult.Rejected(RejectionReason.UNRESOLVED_ARG_SOURCE), result)
    }

    @Test
    fun `a binding to a step that has not run yet is unresolved rather than blank`() {
        val result = InvocationValidator.resolve(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            observations = emptyMap(),
        )
        assertEquals(ResolutionResult.Rejected(RejectionReason.UNRESOLVED_ARG_SOURCE), result)
    }

    @Test
    fun `a binding to a key the observation does not carry is unresolved rather than blank`() {
        val result = InvocationValidator.resolve(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            observations = mapOf(0 to ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED, ToolOutput())),
        )
        assertEquals(ResolutionResult.Rejected(RejectionReason.UNRESOLVED_ARG_SOURCE), result)
    }

    @Test
    fun `a blank produced value is never substituted`() {
        // An agent that silently searches for an empty string is worse than one that stops and says why.
        val result = InvocationValidator.resolve(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            observations = mapOf(
                0 to ToolResult.Observed(
                    ObservedFact.APP_NOT_INSTALLED,
                    ToolOutput(mapOf("resolved_query" to "   ")),
                ),
            ),
        )
        assertEquals(ResolutionResult.Rejected(RejectionReason.UNRESOLVED_ARG_SOURCE), result)
    }
}
