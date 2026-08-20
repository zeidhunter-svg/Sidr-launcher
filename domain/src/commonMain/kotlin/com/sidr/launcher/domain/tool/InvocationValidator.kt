package com.sidr.launcher.domain.tool

/** Why an invocation was refused. Every value is traced, never swallowed. */
enum class RejectionReason { UNKNOWN_TOOL, UNDECLARED_ARG, MISSING_REQUIRED_ARG }

sealed interface InvocationCheck {
    data object Valid : InvocationCheck
    data class Rejected(val reason: RejectionReason) : InvocationCheck
}

/**
 * Pure, fail-closed argument validation — the A0 counterpart of `ProposalValidator`, which stays
 * untouched and served as the model. Stdlib only.
 */
object InvocationValidator {

    fun validate(invocation: ToolInvocation, registry: ToolRegistry): InvocationCheck {
        val descriptor = registry.find(invocation.id)
            ?: return InvocationCheck.Rejected(RejectionReason.UNKNOWN_TOOL)

        val declared = descriptor.argSchema.map { it.name }.toSet()
        if (invocation.args.keys.any { it !in declared }) {
            return InvocationCheck.Rejected(RejectionReason.UNDECLARED_ARG)
        }

        val unsatisfied = descriptor.argSchema.any { arg ->
            arg.required && invocation.args[arg.name]?.isNotBlank() != true
        }
        if (unsatisfied) return InvocationCheck.Rejected(RejectionReason.MISSING_REQUIRED_ARG)

        return InvocationCheck.Valid
    }
}
