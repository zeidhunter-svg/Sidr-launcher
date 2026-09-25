package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * The eight states A0 can actually produce. `PartiallyCompleted` from the 2026-07-11 A4 spec is
 * deliberately absent: without re-planning A0 cannot reach it, and a state the engine cannot produce
 * is a lie in the type. A step skipped by an unsatisfied precondition is part of a normal [Completed].
 */
enum class ExecutionState {
    Planning, Running, AwaitingConsent, Paused, Completed, Cancelled, Failed, Blocked;

    val isTerminal: Boolean
        get() = this == Completed || this == Cancelled || this == Failed || this == Blocked
}

enum class ConsentReason { RISK_LEVEL, RISK_RAISED, MISSING_PERMISSION, DURABLE_EFFECT }

data class ConsentCheckpoint(val stepIndex: Int, val reason: ConsentReason)

/**
 * Loop bounds — and, since A4' phase 0, a **wall-clock** bound too.
 *
 * [maxStepWallClockMs] narrows the A0 debt this KDoc used to name as deferred ("a wall-clock limit
 * needs a clock port and is honestly deferred to A4'"). It needed no port: `withTimeout` reads the
 * dispatcher's clock, which `runTest` makes virtual, so the domain stays clock-free in the sense
 * that mattered — nothing here calls `System.currentTimeMillis()` and no test depends on real time.
 *
 * **The cut is COOPERATIVE, not preemptive — say this plainly, because "closed" would overclaim
 * it.** `withTimeout` cancels only at a *cancellable suspension point* inside the block; it interrupts
 * a tool suspended on `delay`, a future network call, or a future MCP call — the shape
 * `AgentExecutorTest`'s wall-clock tests model. A tool blocked in **non-suspending** code is not
 * interrupted, and the budget only bounds it once it returns. Every shipped Android world call is
 * exactly that shape today: `ContextIntentLauncher.launch` calls `startActivity` synchronously (a
 * plain `fun`, no suspension point), and the other workers — `SystemIntentToolWorker`,
 * `AndroidActionExecutor`, and the `:consumer:jvm` sandbox — reach the world through
 * `withContext(ioDispatcher)` around a **blocking** body, and `withContext` itself waits for that
 * body to return before the coroutine has anywhere to suspend. So a `set_timer` whose binder call
 * hangs, or a sandbox file read stuck on I/O, is bounded by nothing today, and a blocking call that
 * finishes after the deadline without ever suspending keeps its real result — not [ToolResult.HandedOff].
 * The A0 debt ("a hanging tool is bounded by nothing") is therefore **narrowed, not closed**: closed
 * for a suspending hang, latent for a synchronous one. Extending interruption to synchronous world
 * calls (a dedicated thread plus `Thread.interrupt`/cancellation, or a cooperative check inside each
 * worker) is an owner question at phase close (controller ruling R14), not decided by this task.
 *
 * The default is an order of magnitude above the slowest path measured on the SM-A325F: the A0 ADR
 * (`ai-context/decisions.md`, the Task 15 device-acceptance section) measured the `launch_app`
 * `ToolInvoked` → `ToolObserved` window at **157–170 ms warm and 1033 ms cold**. It is a bound on a
 * **hang**, not a latency policy: a tool that takes 9 seconds is not the failure this exists to
 * catch, and tightening it toward the measured numbers would start failing correct work on a cold
 * or loaded device.
 */
data class RuntimeBudget(
    val maxSteps: Int,
    val maxConsecutiveFailures: Int,
    val maxStepWallClockMs: Long = 10_000,
) {
    companion object {
        /** A0's default: two steps of headroom over the one plan shape that exists. */
        val Default = RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2)
    }
}

/** Port: the domain must not know about UUIDs, and tests must be deterministic. */
interface AgentSessionIdFactory {
    fun newId(): AgentSessionId
}

/**
 * An immutable snapshot. Keeping state as a value and behaviour as a function ([AgentExecutor]) is what
 * lets every test drive the machine without coroutine timing, and what makes persistence a plain save.
 */
data class AgentSession(
    val id: AgentSessionId,
    val goal: AgentGoal,
    val plan: ExecutionPlan,
    val cursor: Int,
    val state: ExecutionState,
    val observations: Map<Int, ToolResult>,
    val consents: Map<Int, Boolean>,
    val trace: ExecutionTrace,
) {
    internal fun record(event: TraceEvent): AgentSession =
        copy(trace = ExecutionTrace(trace.events + event))

    internal fun ended(next: ExecutionState): AgentSession =
        copy(state = next).record(TraceEvent.SessionEnded(next))

    /**
     * The restart transition (Task 12). A session that outlived its process is presented as
     * [ExecutionState.Paused] and never resumed silently — the user asked for this minutes or days
     * ago, and continuing without asking would be the system deciding on their behalf.
     *
     * It records [TraceEvent.SessionPaused] rather than only setting the state, because the trace is
     * 1:1 with reality (`DOC-ILM-3`) and "the process died and we stopped here" is part of reality. A
     * state change with no event would leave a persisted trace that reads as if the plan simply ran
     * on, and the resume below would then appear out of nowhere.
     *
     * **Public, not `internal`.** [record] and [ended] stay `internal` — arbitrary trace writing is
     * the domain's business alone — but these two named transitions are called from the feature
     * layer's `LauncherAgentSession`, and Kotlin's `internal` is per-Gradle-module, so `internal`
     * here would simply not compile there. Exposing exactly two named transitions rather than
     * [record] is what keeps the seam narrow.
     */
    fun pausedForRestore(): AgentSession =
        copy(state = ExecutionState.Paused).record(TraceEvent.SessionPaused)

    /**
     * The other half of [pausedForRestore]: the user chose to pick the plan back up. Sets
     * [ExecutionState.Running] and records [TraceEvent.SessionResumed], so the engine re-evaluates
     * from the persisted cursor — which puts a pending consent checkpoint back on screen rather than
     * stepping past it.
     *
     * **Staleness, both layers, named here because this is the function that resumes** (A4' phase 0,
     * spec §3 0.3; owner fork F0-3 — phase 0 decides and names, phase 2 builds what reacts, exactly
     * as §6.4 divides it):
     *
     *  - **Layer 1 — the engine's own observations.** This continues from the persisted cursor and
     *    nothing re-plans, so an observation taken before the pause is acted on however stale it has
     *    become (A0 acceptance §12.8: a session that already observed `APP_NOT_INSTALLED` opens the
     *    store even if the app was installed while the plan sat paused). **Addressed to phase 2**,
     *    where re-planning on partial failure is the same mechanism and where
     *    `PlanningRequest.priorObservations` is the input it needs — you cannot react to a re-check
     *    without something that re-plans.
     *  - **Layer 2 — a source's own snapshot.** `ShortcutRefreshTrigger.start()` runs once per
     *    process and observes **shortcut** changes; a grant of the `android.app.role.HOME` role is
     *    not one, so the dynamic catalog stays empty until a restart (A1″ acceptance finding (b),
     *    measured). **Named EXCLUDED from A4' with its own address**: it is a property of one
     *    Android source's refresh trigger, not of the engine, and criterion 3 §10 admits an excluded
     *    layer provided it is named rather than implied absent. It belongs to whichever block next
     *    touches `ShortcutToolSource`.
     *
     * Neither is closed by the wall-clock budget, and the budget must not be mistaken for them: it
     * bounds how long **one call** may take, not how old a **fact** may be.
     */
    fun resumed(): AgentSession =
        copy(state = ExecutionState.Running).record(TraceEvent.SessionResumed)
}
