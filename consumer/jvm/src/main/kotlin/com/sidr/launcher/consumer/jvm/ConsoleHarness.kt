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
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ResolutionResult
import com.sidr.launcher.domain.trace.TraceEvent
import java.nio.file.Path

/**
 * The second consumer's driver: a console harness, not a product and not a UI.
 *
 * **It never calls `ToolExecutor.invoke` itself.** It drives [AgentExecutor], which owns the one call
 * site below the consent checkpoint — the property `ToolExecutorCallSiteGuardTest` holds mechanically
 * over this module, whose `consumer/jvm/src/main/kotlin` is one of its scanned production roots. A
 * second consumer must not become a second way to reach the world.
 *
 * [out] and [ask] are injected so the loop is testable without a terminal; `main` supplies the real
 * ones. All user-facing text here is English developer output, and that is a recorded exception rather
 * than a gap: the strings-in-the-same-commit rule governs product surfaces, and this module ships none
 * (spec §13).
 *
 * **A named limitation: a restore ignores the goal typed on the command line.** When [restore] finds a
 * persisted session, the text passed to [run] for *this* invocation is discarded and the persisted plan
 * continues — so typing `remove b.txt` while an unfinished `remove a.txt` session is on disk continues
 * the a.txt plan, and a `y` at the gate deletes a.txt. That is now **disclosed rather than silent**:
 * [restore] prints the persisted session's own goal text, and [gate] prints the concrete arguments the
 * step it is asking about will actually be called with. It is deliberately not *refused*. Whether a
 * restored session may continue under a different typed goal is a question about restore semantics, and
 * the Android consumer carries the identical question one level deeper — a resumed plan never re-checks
 * its preconditions against the world. Owned by A4', not decided here.
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
     *
     * **It prints the persisted goal.** The session that comes back off disk carries its own
     * [AgentGoal], and that — not whatever was typed for this invocation — is what continuing will act
     * on (see the limitation on the class KDoc). Printing the id and the cursor alone named neither the
     * goal nor the file, so a person answering the gate below could not tell the two apart.
     */
    private suspend fun restore(): AgentSession? {
        val active = (store.active() as? OperationResult.Success)?.value ?: return null
        val paused = active.pausedForRestore()
        out("Found an unfinished session: ${paused.id.value} — Paused at step ${paused.cursor}.")
        out("Its goal: \"${paused.goal.text}\" — continuing acts on this, not on anything typed now.")
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
        out("Goal: \"${session.goal.text}\"")
        out("Consent required for step $step: ${descriptor?.invocation?.id?.value} (risk ${descriptor?.risk}).")
        out("  ${boundArguments(session, descriptor)}")
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

    /**
     * The concrete arguments the step awaiting consent will be called with — for `delete_file`, the
     * path of the file about to be deleted.
     *
     * **This is not a lookup invented here; it is the engine's own binder.**
     * [InvocationValidator.resolve] is pure in exactly two things, the step's [PlanStep.invocation] and the
     * session's observations, and `AgentExecutor.prepare` returns at the consent checkpoint *before* it
     * binds — so at this prompt no binding has happened yet and calling `resolve` is the only way to
     * name the target. What it answers here is what the engine will bind on the way past the
     * checkpoint: nothing between this prompt and that moment adds an observation (no step runs), and
     * `ResolveConsentUseCase` re-reads the same persisted session, so the two calls cannot disagree.
     *
     * A binding that cannot be resolved is reported as such rather than papered over with a blank. It
     * is reachable: `find_file` emits its declared key blank when nothing matched, and the checkpoint
     * is decided above the binding — so this harness *does* ask for consent to delete a file that was
     * not found, and then fails the step on `UNRESOLVED_ARG_SOURCE` instead of acting. Saying so at the
     * prompt is the honest form of that.
     */
    private fun boundArguments(session: AgentSession, step: PlanStep?): String {
        if (step == null) return "Arguments: unknown — the plan has no step at index ${session.cursor}."
        return when (val resolution = InvocationValidator.resolve(step.invocation, session.observations)) {
            is ResolutionResult.Resolved ->
                if (resolution.invocation.args.isEmpty()) {
                    "Arguments: none."
                } else {
                    "Arguments: " + resolution.invocation.args.entries
                        .joinToString(", ") { (name, value) -> "$name=$value" }
                }
            is ResolutionResult.Rejected ->
                "Arguments: not bindable (${resolution.reason}) — this step would fail rather than act."
        }
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
