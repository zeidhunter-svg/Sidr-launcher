package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.consumer.jvm.plan.FilePlanner
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionIdFactory
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionStore
import com.sidr.launcher.consumer.jvm.tool.SandboxToolExecutor
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.trace.TraceEvent
import java.nio.file.Path

/**
 * The second consumer's driver: a console harness, not a product and not a UI.
 *
 * **It never calls `ToolExecutor.invoke` itself.** It drives [AgentExecutor], which owns the one call
 * site below the consent checkpoint — the property `ToolExecutorCallSiteGuardTest` holds mechanically
 * once Task 9 extends it to this module. A second consumer must not become a second way to reach the
 * world.
 *
 * [out] and [ask] are injected so the loop is testable without a terminal; `main` supplies the real
 * ones. All user-facing text here is English developer output, and that is a recorded exception rather
 * than a gap: the strings-in-the-same-commit rule governs product surfaces, and this module ships none
 * (spec §13).
 */
class ConsoleHarness(
    root: Path,
    private val out: (String) -> Unit,
    private val ask: () -> String?,
) {
    private val registry = SandboxToolSource()
    private val store = JvmAgentSessionStore(root.resolve(".sidr-agent/session.json"))
    private val executor = AgentExecutor(registry, SandboxToolExecutor(root))

    // Named `runner`, not `run`: this class already has a `run` member, and `kotlin.run { }` is used
    // nowhere here precisely so no reader has to work out which `run` a call resolves to.
    private val runner = RunAgentSessionUseCase(executor, store)

    private var printed = 0

    suspend fun run(goalText: String): ExecutionState {
        val restored = restore()
        val session = restored ?: start(goalText) ?: return ExecutionState.Blocked

        val ran = when (val result = runner.run(session)) {
            is OperationResult.Failure -> {
                out("Store failure: ${result.error}")
                return ExecutionState.Failed
            }
            is OperationResult.Success -> result.value
        }

        printTrace(ran)
        if (ran.state != ExecutionState.AwaitingConsent) {
            out("Outcome: ${ran.state}")
            return ran.state
        }

        return gate(ran)
    }

    /**
     * A session that outlived its process is presented as `Paused` and **never** resumed silently —
     * `AgentSession.pausedForRestore` records `SessionPaused` so the trace stays 1:1 with reality,
     * including "the process died and we stopped here".
     */
    private suspend fun restore(): AgentSession? {
        val active = (store.active() as? OperationResult.Success)?.value ?: return null
        val paused = active.pausedForRestore()
        out("Found an unfinished session: ${paused.id.value} — Paused at step ${paused.cursor}.")
        printTrace(paused)
        store.save(paused)
        return paused.resumed()
    }

    private suspend fun start(goalText: String): AgentSession? {
        val goal = AgentGoal(text = goalText, shape = GoalShape.Free(goalText))
        val started = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry)
        val id = when (val result = started.start(goal)) {
            is OperationResult.Failure -> { out("Store failure: ${result.error}"); return null }
            is OperationResult.Success -> result.value
        }
        if (id == null) {
            out("No plan for: \"$goalText\". This harness understands: remove <file name>")
            return null
        }
        return (store.active() as? OperationResult.Success)?.value
    }

    private suspend fun gate(session: AgentSession): ExecutionState {
        val step = session.cursor
        val descriptor = session.plan.steps.getOrNull(step)
        out("")
        out("Consent required for step $step: ${descriptor?.invocation?.id?.value} (risk ${descriptor?.risk}).")
        out("Proceed? [y/N]")

        // No answer available — stdin closed, or the person walked away. This is the harness's stand-in
        // for the process dying at the gate: return with the session persisted and unresolved, so a
        // later run finds it. Anything else would be the harness deciding on the person's behalf.
        val answer = ask()
        if (answer == null) {
            out("No answer given — the session stays at the gate and survives this process.")
            return ExecutionState.AwaitingConsent
        }
        val granted = answer.trim().lowercase() == "y"

        val outcome = ResolveConsentUseCase(store, runner).resolve(session.id, step, granted)
        val resolved = when (outcome) {
            is OperationResult.Failure -> {
                out("Store failure: ${outcome.error}")
                return ExecutionState.Failed
            }
            is OperationResult.Success -> outcome.value
        }

        if (resolved == null) {
            // The decision did not apply — the step was no longer awaiting one, which is exactly what
            // a second tap looks like. Reporting it is honest; re-asking would be the harness deciding
            // on the person's behalf.
            out("That decision no longer applies — the step was not awaiting one.")
            return ExecutionState.AwaitingConsent
        }

        printTrace(resolved)
        out("Outcome: ${resolved.state}")
        return resolved.state
    }

    private fun printTrace(session: AgentSession) {
        session.trace.events.drop(printed).forEach { out("  ${render(it)}") }
        printed = session.trace.events.size
    }

    private fun render(event: TraceEvent): String = when (event) {
        is TraceEvent.PlanCreated -> "PlanCreated(stepCount=${event.stepCount})"
        is TraceEvent.StepStarted -> "StepStarted(${event.index})"
        is TraceEvent.StepSkipped -> "StepSkipped(${event.index}, ${event.precondition})"
        is TraceEvent.StepRejected -> "StepRejected(${event.index}, ${event.reason})"
        is TraceEvent.ConsentRequested -> "ConsentRequested(${event.index}, ${event.reason})"
        is TraceEvent.ConsentResolved -> "ConsentResolved(${event.index}, granted=${event.granted})"
        is TraceEvent.ToolInvoked -> "ToolInvoked(${event.index}, ${event.toolId.value})"
        is TraceEvent.ToolObserved -> "ToolObserved(${event.index}, ${event.result})"
        TraceEvent.SessionPaused -> "SessionPaused"
        TraceEvent.SessionResumed -> "SessionResumed"
        is TraceEvent.SessionEnded -> "SessionEnded(${event.state})"
    }
}
