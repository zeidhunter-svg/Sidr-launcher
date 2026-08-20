package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A0's fail-closed argument gate, modeled on `ProposalValidator`. It runs before **every** step, not
 * once at planning time: a plan can outlive a process restart, and by the time it resumes the app may
 * be gone or a permission revoked.
 */
class InvocationValidatorTest {

    private val launchApp = ToolDescriptor(
        id = ToolIds.LAUNCH_APP,
        argSchema = listOf(ActionArg("query", description = "The app name to launch")),
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    private val registry = object : ToolRegistry {
        override fun all(): List<ToolDescriptor> = listOf(launchApp)
        override fun find(id: ToolId): ToolDescriptor? = all().firstOrNull { it.id == id }
    }

    @Test
    fun `a well-formed invocation is valid`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")),
            registry,
        )
        assertEquals(InvocationCheck.Valid, check)
    }

    @Test
    fun `an unregistered tool is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolId("not_registered"), mapOf("query" to "убер")),
            registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.UNKNOWN_TOOL), check)
    }

    @Test
    fun `an argument outside the declared schema is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер", "sudo" to "true")),
            registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.UNDECLARED_ARG), check)
    }

    @Test
    fun `a missing required argument is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, emptyMap()),
            registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.MISSING_REQUIRED_ARG), check)
    }

    @Test
    fun `a blank required argument is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "   ")),
            registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.MISSING_REQUIRED_ARG), check)
    }
}
