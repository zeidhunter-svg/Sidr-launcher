package com.sidr.launcher.domain.tool

/** Why an invocation was refused. Every value is traced, never swallowed. */
enum class RejectionReason {
    UNKNOWN_TOOL,
    UNDECLARED_ARG,
    MISSING_REQUIRED_ARG,

    /** An [ArgSource.FromStep] naming this step, a later one, or an index the plan does not have. */
    FORWARD_ARG_SOURCE,

    /**
     * An [ArgSource.FromStep] whose key is not in the source tool's declared
     * [ToolDescriptor.outputSchema] — including the case where the source tool is not registered at
     * all, which promises nothing by the same reasoning — or whose declared type does not match the
     * type of the argument it feeds.
     */
    UNDECLARED_OUTPUT,

    /** The source step ran but produced no usable value under that key — e.g. it Failed. Run-time only. */
    UNRESOLVED_ARG_SOURCE,
}

sealed interface InvocationCheck {
    data object Valid : InvocationCheck
    data class Rejected(val reason: RejectionReason) : InvocationCheck
}

sealed interface ResolutionResult {
    data class Resolved(val invocation: ResolvedInvocation) : ResolutionResult
    data class Rejected(val reason: RejectionReason) : ResolutionResult
}

/**
 * Pure, fail-closed argument validation — the A0 counterpart of `ProposalValidator`, which stays
 * untouched and served as the model. Stdlib only.
 *
 * F6 split it into two phases, and the split is what keeps it fail-closed rather than merely more
 * capable: [validate] is everything decidable **without having run anything**, so it runs at plan time
 * and again before every step including after a resume; [resolve] is the binding, and is the only
 * producer of [ResolvedInvocation].
 *
 * It references no `domain/agent` type on purpose — the layering is agent → tool, and F6 must not
 * invert it. That is why [validate] takes a flat list of preceding tool ids rather than a `PlanStep`,
 * and [resolve] takes a plain map rather than an `AgentSession`.
 */
object InvocationValidator {

    /**
     * Shape check. Needs no observations.
     *
     * @param precedingTools the tool id of each earlier step, position == step index. Its size **is**
     *   this step's index (`ExecutionPlan` pins `index == position`), so an [ArgSource.FromStep] naming
     *   an index outside it is a forward or out-of-range reference and is rejected without a special
     *   case for "itself".
     */
    fun validate(
        invocation: ToolInvocation,
        precedingTools: List<ToolId>,
        registry: ToolRegistry,
    ): InvocationCheck {
        val descriptor = registry.find(invocation.id)
            ?: return InvocationCheck.Rejected(RejectionReason.UNKNOWN_TOOL)

        val declared = descriptor.argSchema.associateBy { it.name }
        if (invocation.args.keys.any { it !in declared }) {
            return InvocationCheck.Rejected(RejectionReason.UNDECLARED_ARG)
        }

        invocation.args.forEach { (name, source) ->
            if (source !is ArgSource.FromStep) return@forEach

            val sourceTool = precedingTools.getOrNull(source.stepIndex)
                ?: return InvocationCheck.Rejected(RejectionReason.FORWARD_ARG_SOURCE)

            // An unregistered source tool declares no outputs, so it promises nothing — the same
            // answer as a key it never declared, and the fail-closed one.
            val produced = registry.find(sourceTool)
                ?.outputSchema
                ?.firstOrNull { it.name == source.key }
                ?: return InvocationCheck.Rejected(RejectionReason.UNDECLARED_OUTPUT)

            // Both ends of a binding are `ActionArg`, so the type check is a comparison. Vacuous today
            // — `ArgType` has one value — and stated as such rather than implied demonstrated.
            if (produced.type != declared.getValue(name).type) {
                return InvocationCheck.Rejected(RejectionReason.UNDECLARED_OUTPUT)
            }
        }

        // A required argument fed by a binding is satisfied here: the value does not exist yet, and
        // whether it ever arrives is [resolve]'s question. A literal is checked now, because nothing
        // later will make a blank one non-blank.
        val unsatisfied = descriptor.argSchema.any { arg ->
            if (!arg.required) return@any false
            when (val source = invocation.args[arg.name]) {
                null -> true
                is ArgSource.Literal -> source.value.isBlank()
                is ArgSource.FromStep -> false
            }
        }
        if (unsatisfied) return InvocationCheck.Rejected(RejectionReason.MISSING_REQUIRED_ARG)

        return InvocationCheck.Valid
    }

    /**
     * Binding. Turns every [ArgSource] into a concrete value or fails closed — the only producer of
     * [ResolvedInvocation], and therefore the only way a value can reach [ToolExecutor].
     *
     * **Nothing is substituted, defaulted, or left blank.** A source step that ran but produced no
     * usable value under that key (it `Failed`, the adapter returned an empty output, or the value is
     * blank) is [RejectionReason.UNRESOLVED_ARG_SOURCE] and the step does not run: an agent that
     * silently searches for an empty string is worse than one that stops and says why.
     *
     * Pure in both arguments, which is what lets the engine call it twice for one step — once to decide
     * whether the step may run, once to obtain the value — without the two calls being able to disagree.
     */
    fun resolve(invocation: ToolInvocation, observations: Map<Int, ToolResult>): ResolutionResult {
        val resolved = LinkedHashMap<String, String>(invocation.args.size)

        invocation.args.forEach { (name, source) ->
            val value = when (source) {
                is ArgSource.Literal -> source.value
                is ArgSource.FromStep -> observations[source.stepIndex]
                    ?.output()
                    ?.values
                    ?.get(source.key)
                    ?.takeIf { it.isNotBlank() }
                    ?: return ResolutionResult.Rejected(RejectionReason.UNRESOLVED_ARG_SOURCE)
            }
            resolved[name] = value
        }

        return ResolutionResult.Resolved(ResolvedInvocation(invocation.id, resolved))
    }

    /** `Failed` produces no output by construction, which is why it binds to nothing. */
    private fun ToolResult.output(): ToolOutput? = when (this) {
        is ToolResult.Effected -> output
        is ToolResult.Observed -> output
        is ToolResult.Failed -> null
    }
}
