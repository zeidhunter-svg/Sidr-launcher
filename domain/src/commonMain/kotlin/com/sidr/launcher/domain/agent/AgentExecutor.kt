package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.requiresConsent
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.InvocationCheck
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ResolutionResult
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.TraceEvent
import kotlin.coroutines.cancellation.CancellationException

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
 *    `ToolInvoked` before [perform] ever runs. The predicate deciding this reads the last
 *    `ToolInvoked` rather than the last trace **event**, because the real restore path
 *    ([AgentSession.pausedForRestore] then [AgentSession.resumed]) writes two events on top of the
 *    pending one — see [midStepInvocation], where getting that wrong is review finding F1.
 *
 * The engine knows nothing of `LauncherAction`, `ExecutableAction`, `Intent`, or any store's name — it
 * only knows [ToolExecutor]. `AgentVocabularyGuardTest` pins that, because it is what keeps A1' free to
 * register F-Droid, a vendor site, or an MCP tool without touching a line in here.
 *
 * [perform]'s call site now also catches a throwing [ToolExecutor] — the floor every worker relies on,
 * not a replacement for a worker's own containment. An adapter-level catch stays worth having because
 * it can report a *specific* [ToolResult.Failed] variant; a throw caught here is always
 * [CommandFailure.Generic], the same named limitation the persisted-`Failed` gap already carries.
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
     * validator rejection, the consent checkpoint, and — since F6 — the **binding** of the step's
     * arguments. When a step is cleared to run it records `StepStarted` + `ToolInvoked` and returns —
     * the cursor is deliberately NOT advanced yet, because the step has not actually run.
     *
     * `cursor` is read below as a list position while [perform] writes it as `invoked.index + 1`. Those
     * are the same number only because [ExecutionPlan] enforces `steps[i].index == i`; the same
     * invariant is what makes `isSatisfied`'s `observations[index - 1]` genuinely "the previous step"
     * and `checkpointFor`'s `it.index < step.index` genuinely "the steps that already ran". See the
     * invariant's KDoc on [ExecutionPlan] for what went wrong when nothing enforced it.
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

        val precedingTools = session.plan.steps
            .filter { it.index < next.index }
            .map { it.invocation.id }

        when (val check = InvocationValidator.validate(next.invocation, precedingTools, registry)) {
            is InvocationCheck.Rejected ->
                return session
                    .record(TraceEvent.StepRejected(next.index, check.reason))
                    .ended(ExecutionState.Failed)
            InvocationCheck.Valid -> Unit
        }

        // The consent decision is recorded HERE and nowhere else (F4): one decision, one event,
        // whichever way the session was driven. `granted` carries the decision forward instead of
        // dropping it, so the trace reads consent -> start -> invoke in the order it happened.
        val checkpoint = checkpointFor(session, next)
        val granted = when {
            checkpoint == null -> session
            session.consents[next.index] == null -> return session
                .record(TraceEvent.ConsentRequested(next.index, checkpoint.reason))
                .copy(state = ExecutionState.AwaitingConsent)
            session.consents[next.index] == false -> return session
                .recordConsent(next.index, granted = false)
                .ended(ExecutionState.Cancelled)
            else -> session.recordConsent(next.index, granted = true)
        }

        // Binding, and it belongs HERE — after the checkpoint, before `ToolInvoked` (F6). A rejection
        // raised after `ToolInvoked` would leave the trace in the one shape that already means "the
        // process died during the call", so a plan with a bad binding would come back as "we may have
        // half-run something, please decide" instead of the honest "this step could not be bound".
        // One rejection path, not two: this ends the session exactly as a validator rejection does.
        when (val resolution = InvocationValidator.resolve(next.invocation, granted.observations)) {
            is ResolutionResult.Rejected ->
                return granted
                    .record(TraceEvent.StepRejected(next.index, resolution.reason))
                    .ended(ExecutionState.Failed)
            is ResolutionResult.Resolved -> Unit
        }

        // Cleared to run. This is the save point: a persisted session here has `ToolInvoked` in its
        // trace with no matching `ToolObserved` yet, which is exactly the mid-step signal `perform`
        // (and, later, a restart's resume path) reads back below.
        return granted
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
     *    same identity [TraceEvent.ToolInvoked.index] carries — and the lookup then asserts the
     *    resolved step's `invocation.id` equals the `toolId` [TraceEvent.ToolInvoked] recorded.
     *
     *    [ExecutionPlan] enforces `index == position`, so within one plan the two addressings agree
     *    and this lookup is not defending against them diverging. What it defends against is the
     *    **plan and the trace disagreeing** — the invariant binds an index to a position inside a
     *    single [ExecutionPlan], but nothing binds a *persisted* trace to the plan it is later
     *    replayed against. A plan swapped under a stored trace (a restore against a different build,
     *    a shorter re-plan) can leave the tail naming a step the plan no longer contains, or a
     *    different tool at that index. Either check failing means exactly that, and the only
     *    fail-closed move is to invoke nothing. Resolving by index rather than by
     *    `steps[invoked.index]` is what makes the first of those two checks possible at all: an
     *    out-of-range index resolves to nothing instead of silently addressing some other step.
     */
    suspend fun perform(session: AgentSession): AgentSession {
        if (session.state != ExecutionState.Running) return session
        val invoked = session.midStepInvocation() ?: return session
        val step = session.plan.steps
            .firstOrNull { it.index == invoked.index }
            ?.takeIf { it.invocation.id == invoked.toolId }
            ?: return session

        // Re-bind, purely to obtain the value. `resolve` is a pure function of the invocation and the
        // observations, and observations cannot change between the `prepare` and the `perform` of one
        // step, so this cannot disagree with the decision `prepare` already made. If it somehow does,
        // the failure is recorded as an OBSERVATION rather than a rejection: the trace must never be
        // left sitting mid-step, because that shape means "the process died" and nothing else.
        val resolved = when (val resolution = InvocationValidator.resolve(step.invocation, session.observations)) {
            is ResolutionResult.Resolved -> resolution.invocation
            is ResolutionResult.Rejected -> {
                val failure = ToolResult.Failed(CommandFailure.Generic)
                return session
                    .record(TraceEvent.ToolObserved(invoked.index, failure))
                    .copy(
                        cursor = invoked.index + 1,
                        observations = session.observations + (invoked.index to failure),
                    )
                    .ended(ExecutionState.Failed)
            }
        }

        // The ONE call site to the world — and the floor under every worker's own containment.
        //
        // `ToolWorker`'s contract says an invocation always yields a `ToolResult`. Until this `try`
        // that was held by convention plus three implementations with three different nets: a fourth
        // adapter whose worker threw took the home-screen process down with it (A1′ final review,
        // `356fe1a` — a device with no activity for ACTION_SET_TIMER, a SAFE one-step plan the consent
        // gate does not stop, and no `try` anywhere above here).
        //
        // Catching HERE rather than in the launcher is deliberate and was re-reviewed once already:
        // `ContextIntentLauncher.launch` returns `Unit`, so a swallowed failure there is
        // indistinguishable from success and the trace would record `Effected` for an effect that never
        // happened — `DOC-ILM-3` would be lied to. At this call site the result type is already
        // `ToolResult`, so a caught throw becomes an honest `Failed`.
        //
        // `Exception`, not `Throwable`: an `Error` (OOM, stack overflow) is not a tool failure and must
        // not be reported as one. Same line `SandboxToolWorker` already draws.
        //
        // It is below the checkpoint by construction, and ToolExecutorCallSiteGuardTest fails the build
        // if a second call site ever appears.
        val result = try {
            toolExecutor.invoke(resolved)
        } catch (e: CancellationException) {
            throw e // never swallow parent cancellation
        } catch (e: Exception) {
            ToolResult.Failed(CommandFailure.Generic)
        }

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
     *
     * **It reads the last `ToolInvoked`, not the last trace event, and that difference is the whole
     * behaviour** (review finding F1, 2026-08-23). The only path a mid-step session takes back into
     * this engine is the restart one, and that path appends [TraceEvent.SessionPaused] and
     * [TraceEvent.SessionResumed] *after* the pending `ToolInvoked` before the engine ever sees the
     * session again. Keyed on the trace **tail**, the predicate therefore answered `null` on exactly
     * the shape it exists to recognise: [prepare] did not short-circuit, re-cleared the same step,
     * appended a second `StepStarted` + `ToolInvoked`, and [perform] then ran the pending call from
     * the duplicate rather than from the original. Two `ToolInvoked` against one `ToolObserved` is
     * not a trace that is 1:1 with reality (`DOC-ILM-3`), and on a step whose consent was already
     * granted it is one confirmation authorising two executions (`DOC-HMA-1`).
     *
     * "The last `ToolInvoked` has no `ToolObserved` for its index" is the same predicate without the
     * adjacency assumption: [perform] records `ToolObserved` for every step it clears — including the
     * re-resolution failure below — so an unmatched `ToolInvoked` means the process died during the
     * call and nothing else.
     */
    private fun AgentSession.midStepInvocation(): TraceEvent.ToolInvoked? {
        val last = trace.events.filterIsInstance<TraceEvent.ToolInvoked>().lastOrNull() ?: return null
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
     * engine, and stopping unconditionally is the conservative half of that.
     *
     * **Every input is read through [effectiveRisk], not off the plan** (review finding F2,
     * 2026-08-23). `permissionGate` and `durability` were always read from the live registry while
     * `risk` came from the persisted [PlanStep] alone, and nothing re-checks risk on resume —
     * `InvocationValidator.validate` covers tool identity, argument schema, output schema and types,
     * and deliberately knows nothing about risk. A plan written when a tool was `SAFE` therefore kept
     * running it as `SAFE` after a build that declared it `CONFIRM`: no checkpoint, no
     * `ConsentRequested`, the tool straight to the world. Taking the higher of the two closes it in
     * the direction that cannot be wrong.
     *
     * **Coverage, corrected by the A0 Task 14 review (2026-08-22) — this KDoc used to say the branch
     * was "unit-tested", and it is not.** Neither A0 tool declares a gate or is `DURABLE`, and no test
     * anywhere constructs a `ToolDescriptor` that does, so [ConsentReason.MISSING_PERMISSION] and
     * [ConsentReason.DURABLE_EFFECT] have **zero** coverage: fail-safe by construction, unproven by
     * test. [ConsentReason.RISK_RAISED] is untested as well, and today unreachable — with
     * `SAFE < CONFIRM < DANGEROUS`, any upward transition lands on CONFIRM or DANGEROUS and the first
     * branch takes it. It goes live the moment a level is inserted below CONFIRM, which is A1′'s
     * business; whoever inserts it writes the case with it. Tracked as `D11` in `§HANDOFF`.
     */
    private fun checkpointFor(session: AgentSession, step: PlanStep): ConsentCheckpoint? {
        val descriptor = registry.find(step.invocation.id)
        val risk = effectiveRisk(step)
        val previousRisk = session.plan.steps
            .filter { it.index < step.index && session.observations.containsKey(it.index) }
            .maxOfOrNull { effectiveRisk(it) }
            ?: ActionRiskLevel.SAFE

        return when {
            requiresConsent(risk) ->
                ConsentCheckpoint(step.index, ConsentReason.RISK_LEVEL)
            risk > previousRisk ->
                ConsentCheckpoint(step.index, ConsentReason.RISK_RAISED)
            descriptor?.permissionGate != null ->
                ConsentCheckpoint(step.index, ConsentReason.MISSING_PERMISSION)
            descriptor?.durability == ToolDurability.DURABLE ->
                ConsentCheckpoint(step.index, ConsentReason.DURABLE_EFFECT)
            else -> null
        }
    }

    /**
     * The higher of what the plan recorded and what the registry declares **now** — the risk the gate
     * actually acts on.
     *
     * A plan outlives the build that wrote it, so its `risk` is a snapshot; the registry is the
     * current truth. Neither alone is safe: trusting the plan misses a tool whose risk was raised,
     * trusting the registry misses a tool whose risk was lowered under a plan the user has not seen
     * again. `maxOf` needs no argument about which source is more authoritative.
     *
     * A tool the registry does not know at all counts as [ActionRiskLevel.DANGEROUS]. That branch is
     * unreachable while `prepare` validates first (`UNKNOWN_TOOL` ends the session before this runs),
     * and it is written this way so the *default* is a stop rather than a pass — the previous
     * `?: return null` made "the registry has never heard of this tool" mean "no consent needed",
     * which is only safe as long as two calls stay in their current order.
     */
    private fun effectiveRisk(step: PlanStep): ActionRiskLevel =
        maxOf(step.risk, registry.find(step.invocation.id)?.risk ?: ActionRiskLevel.DANGEROUS)

    /**
     * Records a consent decision. [prepare] is the **single writer** of [TraceEvent.ConsentResolved]
     * (review finding F4, 2026-08-23) — `ResolveConsentUseCase` used to write it as well, and on a
     * refusal both wrote: one "no" from the user, two events in the trace, on precisely the path the
     * design calls the one a user would actually feel.
     *
     * **Once per step, by construction rather than by a check.** Every route out of the consent block
     * either ends the session ([ExecutionState.Cancelled], or `Failed` if the binding then fails) or
     * clears the step, and a cleared step's next visit short-circuits on [midStepInvocation] before it
     * reaches this line. So the block cannot be entered twice for one step, and there is nothing for
     * an "only if not already traced" guard to catch. A first version of this shipped that guard with
     * a test beside it; the test passed with the guard deleted, which is the whole argument against
     * keeping either (the F2/D10 family — an assertion that cannot fail, defending a branch that
     * cannot run).
     */
    private fun AgentSession.recordConsent(index: Int, granted: Boolean): AgentSession =
        record(TraceEvent.ConsentResolved(index, granted))

    /** Consecutive failures are derived, not stored — one less field to keep consistent across a restart. */
    private fun AgentSession.trailingFailures(): Int =
        generateSequence(cursor - 1) { it - 1 }
            .takeWhile { it >= 0 && observations[it] is ToolResult.Failed }
            .count()
}
