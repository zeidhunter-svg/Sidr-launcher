package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.InvocationCheck
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * The A0 engine: **exactly one transition per [advance]**, never a loop.
 *
 * That shape is not stylistic. Cancellation is checked between transitions by the caller, so a cancel
 * that arrives mid-plan cannot be raced by the next step; and the persistence layer gets a save point
 * after every transition, which is what makes surviving `am force-stop` a consequence of the design
 * rather than a feature bolted on.
 *
 * [advance] is itself the composition of two halves, [prepare] and [perform], split at the one call
 * site to the world. Bundling "decide to call the tool" and "call the tool" into a single in-memory
 * step would mean that if the process dies during [ToolExecutor.invoke], nothing was ever persisted —
 * so on resume the step would be silently re-run and its side effect would happen twice. Splitting them
 * means the data layer can persist the session **after [prepare] returns**, i.e. after `ToolInvoked` is
 * in the trace but before the tool has been called; a resume that finds a session in that shape (see
 * [perform]'s "mid-step" check below) is exactly the "process died mid-call" signal a later task uses.
 *
 * Two invariants the split must hold on its own, because the single guard the un-split version had no
 * longer covers both halves:
 *  - **[perform] re-checks `state == Running`.** A session persisted mid-step can have its `state`
 *    changed independently (e.g. a cancel use case marks it `Cancelled` or `Paused` without touching the
 *    trace) before the runner resumes it with [advance]. [prepare] guards the first half; [perform] must
 *    guard the second half itself, or a cancelled/paused session still fires its tool on resume.
 *  - **[prepare] short-circuits when the session is already mid-step**, so [advance] is safe as the
 *    single resume entry point: `advance` on a session persisted right after [prepare] must run the
 *    pending tool call exactly once, not re-clear the same step and append a second `StepStarted` /
 *    `ToolInvoked` before [perform] ever runs.
 *
 * The engine knows nothing of `LauncherAction`, `ExecutableAction`, `Intent`, or any store's name — it
 * only knows [ToolExecutor]. `AgentVocabularyGuardTest` pins that, because it is what keeps A1' free to
 * register F-Droid, a vendor site, or an MCP tool without touching a line in here.
 */
class AgentExecutor(
    private val registry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val budget: RuntimeBudget = RuntimeBudget.Default,
) {

    /** One full transition: decide the next step, then (if one was cleared to run) perform it. */
    suspend fun advance(session: AgentSession): AgentSession = perform(prepare(session))

    /**
     * Everything up to but NOT including the call to the world: end-of-plan, budget, precondition,
     * validator rejection, and the consent checkpoint. When a step is cleared to run it records
     * `StepStarted` + `ToolInvoked` and returns — the cursor is deliberately NOT advanced yet, because
     * the step has not actually run.
     *
     * If [session] is already mid-step (see [midStepInvocation]), this returns it unchanged instead of
     * re-clearing the same step. Without this, `advance` on a session persisted right after a previous
     * [prepare] call — the exact shape a resumed session has — would append a second `StepStarted` +
     * `ToolInvoked` for the same step before [perform] ever ran, so the trace would claim the step was
     * invoked twice when [perform] only ever calls the tool once. `advance` is the only entry point the
     * resume path uses, so this is what keeps it correct there.
     */
    suspend fun prepare(session: AgentSession): AgentSession {
        if (session.state != ExecutionState.Running) return session
        if (session.midStepInvocation() != null) return session

        val next = session.plan.steps.getOrNull(session.cursor)
            ?: return session.ended(ExecutionState.Completed)

        if (session.cursor >= budget.maxSteps) return session.ended(ExecutionState.Blocked)

        if (!isSatisfied(next.precondition, session, next.index)) {
            return session
                .record(TraceEvent.StepSkipped(next.index, next.precondition))
                .copy(cursor = session.cursor + 1)
        }

        when (val check = InvocationValidator.validate(next.invocation, registry)) {
            is InvocationCheck.Rejected ->
                return session
                    .record(TraceEvent.StepRejected(next.index, check.reason))
                    .ended(ExecutionState.Failed)
            InvocationCheck.Valid -> Unit
        }

        val checkpoint = checkpointFor(session, next)
        if (checkpoint != null) {
            when (session.consents[next.index]) {
                null -> return session
                    .record(TraceEvent.ConsentRequested(next.index, checkpoint.reason))
                    .copy(state = ExecutionState.AwaitingConsent)
                false -> return session
                    .record(TraceEvent.ConsentResolved(next.index, granted = false))
                    .ended(ExecutionState.Cancelled)
                true -> Unit
            }
        }

        // Cleared to run. This is the save point: a persisted session here has `ToolInvoked` in its
        // trace with no matching `ToolObserved` yet, which is exactly the mid-step signal `perform`
        // (and, later, a restart's resume path) reads back below.
        return session
            .record(TraceEvent.StepStarted(next.index))
            .record(TraceEvent.ToolInvoked(next.index, next.invocation.id))
    }

    /**
     * If [session] is not mid-step, this is a no-op. Otherwise it makes **the one call site to the
     * world**, records `ToolObserved`, advances the cursor, stores the observation, and applies the
     * consecutive-failure budget.
     *
     * "Mid-step" is read from the trace tail rather than kept as a field: the session is mid-step
     * exactly when its last trace event is `ToolInvoked(i)` with no matching `ToolObserved(i)`. That
     * predicate is deliberately trace-derived rather than a new field — it is persisted for free, and
     * it is the same signal a later task uses to detect "the process died during a tool call" after a
     * restart.
     *
     * Two guards run before the call:
     *  - **`state == Running`.** A mid-step session whose `state` was independently moved to
     *    `Cancelled` or `Paused` (the cancel path touches `state`, not the trace) must not fire its
     *    tool just because the trace tail still looks mid-step. [prepare] only guards the half of the
     *    transition it owns; this is the other half's own guard, not a duplicate of it.
     *  - **The resolved step must match the trace.** The step is looked up by `PlanStep.index` — the
     *    same identity [ToolInvoked.index] carries — never by list position, because nothing forces a
     *    `PlanStep`'s `index` field to equal its position in [ExecutionPlan.steps]. The lookup then
     *    asserts the resolved step's `invocation.id` equals the `toolId` [ToolInvoked] recorded. Either
     *    check failing means the plan and the trace disagree about which step this is, and the only
     *    fail-closed move is to invoke nothing.
     */
    suspend fun perform(session: AgentSession): AgentSession {
        if (session.state != ExecutionState.Running) return session
        val invoked = session.midStepInvocation() ?: return session
        val step = session.plan.steps
            .firstOrNull { it.index == invoked.index }
            ?.takeIf { it.invocation.id == invoked.toolId }
            ?: return session

        // The ONE call site to the world. It is below the checkpoint by construction, and
        // ToolExecutorCallSiteGuardTest fails the build if a second one ever appears.
        val result = toolExecutor.invoke(step.invocation)

        val observed = session
            .record(TraceEvent.ToolObserved(invoked.index, result))
            .copy(
                cursor = invoked.index + 1,
                observations = session.observations + (invoked.index to result),
            )

        return if (result is ToolResult.Failed && observed.trailingFailures() >= budget.maxConsecutiveFailures) {
            observed.ended(ExecutionState.Failed)
        } else {
            observed
        }
    }

    /**
     * The [TraceEvent.ToolInvoked] event of the step whose tool call has been decided but not yet
     * performed, or `null` if [AgentSession] is not currently mid-step.
     */
    private fun AgentSession.midStepInvocation(): TraceEvent.ToolInvoked? {
        val last = trace.events.lastOrNull() as? TraceEvent.ToolInvoked ?: return null
        val alreadyObserved = trace.events.any { it is TraceEvent.ToolObserved && it.index == last.index }
        return if (alreadyObserved) null else last
    }

    private fun isSatisfied(
        precondition: StepPrecondition,
        session: AgentSession,
        index: Int,
    ): Boolean = when (precondition) {
        StepPrecondition.None -> true
        is StepPrecondition.PreviousStepObserved -> {
            val previous = session.observations[index - 1]
            previous is ToolResult.Observed && previous.fact == precondition.fact
        }
    }

    /**
     * Four triggers, all fail-safe. [ConsentReason.MISSING_PERMISSION] fires whenever a tool merely
     * *declares* a gate: consulting the real grant state would put `PermissionChecker` inside the
     * engine, and stopping unconditionally is the conservative half of that. Neither A0 tool declares
     * one, so the branch is unit-tested rather than exercised in the slice.
     */
    private fun checkpointFor(session: AgentSession, step: PlanStep): ConsentCheckpoint? {
        val descriptor = registry.find(step.invocation.id) ?: return null
        val previousRisk = session.plan.steps
            .filter { it.index < step.index && session.observations.containsKey(it.index) }
            .maxOfOrNull { it.risk }
            ?: ActionRiskLevel.SAFE

        return when {
            step.risk >= ActionRiskLevel.CONFIRM ->
                ConsentCheckpoint(step.index, ConsentReason.RISK_LEVEL)
            step.risk > previousRisk ->
                ConsentCheckpoint(step.index, ConsentReason.RISK_RAISED)
            descriptor.permissionGate != null ->
                ConsentCheckpoint(step.index, ConsentReason.MISSING_PERMISSION)
            descriptor.durability == ToolDurability.DURABLE ->
                ConsentCheckpoint(step.index, ConsentReason.DURABLE_EFFECT)
            else -> null
        }
    }

    /** Consecutive failures are derived, not stored — one less field to keep consistent across a restart. */
    private fun AgentSession.trailingFailures(): Int =
        generateSequence(cursor - 1) { it - 1 }
            .takeWhile { it >= 0 && observations[it] is ToolResult.Failed }
            .count()
}
