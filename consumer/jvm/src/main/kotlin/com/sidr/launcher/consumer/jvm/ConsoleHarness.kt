package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.consumer.jvm.bridge.SandboxFileSort
import com.sidr.launcher.consumer.jvm.bridge.SandboxMentionBinder
import com.sidr.launcher.consumer.jvm.bridge.SandboxToolDescriptions
import com.sidr.launcher.consumer.jvm.plan.FilePlanner
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionIdFactory
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionStore
import com.sidr.launcher.consumer.jvm.store.JvmLearnedPlanStore
import com.sidr.launcher.consumer.jvm.text.JdkTextFolding
import com.sidr.launcher.consumer.jvm.tool.SandboxToolInverses
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.consumer.jvm.tool.SandboxToolWorker
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.UndoAgentSessionUseCase
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.BridgeModelPlanner
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.plan
import com.sidr.launcher.domain.memory.plan.DefaultLearnedPlans
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.RecordLearnedPlanUseCase
import com.sidr.launcher.domain.memory.plan.Replay
import com.sidr.launcher.domain.memory.plan.needsCard
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ResolutionResult
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.trace.TraceEvent
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
 *
 * **The model path (A4′ phase 3b, Task 13; B-12, B-13): `ask <goal>`.** The goal after `ask` goes to the
 * SAME bridge `:domain` holds — `BridgeModelPlanner` over this consumer's offering (the sandbox level,
 * [SandboxToolDescriptions]), this platform's folding, the injected [BridgeTransport] — then the validator's
 * verdict is bound on this computer ([SandboxMentionBinder]: the sandbox root by the device, a file the user
 * named through `SandboxFileSort`, references kept), then printed as a card. Nothing runs before a `y` to
 * the card; then `startPlanned` and the same engine, with its consent prompts unchanged. **Since the D3 second
 * amendment (controller ruling K12, A4′ acceptance tail task 4) a reading that owes no pick and sends no model text
 * out is printed under the model's mark and started at once instead ([startedByModel]) — on this consumer that is
 * every bound reading today.** Every answer that is
 * not a plan, and every binding that is not one, is said and starts nothing. An unconfirmed reading is never stored
 * (a card refused or left unanswered is dropped; a confirmed one's form and template are kept in the
 * learned-plan file, below). `remove …` / `rename …` keep the deterministic `FilePlanner` — the model
 * is asked only on `ask`.
 *
 * **The learned-plan cache (A4′ phase 4, B-13): step 2c of `ask`.** Before the model is asked, the command is
 * tried against the learned forms ([DefaultLearnedPlans.replay], the same gates as a fresh reading — the same
 * registry, offering, binder and folding). A bound replay prints `Learned command: <form>` and starts the same
 * engine at once — or, when [needsCard] (a pick is owed, or an `EXTERNAL` step carries a model-written value),
 * shows the card first. **Nothing is decided on the person's behalf:** the engine's consent prompt of a risky
 * step (`delete_file`) is asked on every replay, exactly as for a model plan. A miss — and an unreadable
 * learned-plan file is a miss — goes to the model unchanged. A fresh reading is recorded only after its card's
 * `y` **and** a started session; a replay records nothing (the streak counts confirmed model readings).
 */
class ConsoleHarness(
    root: Path,
    private val out: (String) -> Unit,
    private val ask: () -> String?,
    /**
     * Where the sandbox's file calls run (A4′ phase 2, F2-1). `Main.kt` passes `Dispatchers.IO`; the
     * default keeps them on the caller's dispatcher, which is what the tests driving this harness under
     * `runTest` need (plan §0.2, probe 3). That leaves `Main.kt`'s one line the only unproven wiring
     * here — named, not tested.
     */
    worldContext: CoroutineContext = EmptyCoroutineContext,
    /**
     * The model's transport for `ask` (B-13). `Main.kt` passes `JdkBridgeTransport` over the environment;
     * tests pass a fake. The default is no model at all: an `ask` then answers «credentials missing» and
     * nothing is sent.
     */
    transport: BridgeTransport = BridgeTransport { TransportReply.Failed(AiError.MissingCredentials) },
) {
    // This consumer reaches the world through one federation, same as the plan's Task 4 Step 2 shape
    // pulled forward by one task: SandboxToolWorker is that federation's registered worker for the
    // SANDBOX level, not a ToolExecutor of its own. AgentExecutor takes the federation's one dispatcher
    // (`federation.executor`), so no bridge and no second-guessed ToolExecutor holder is needed here.
    private val federation = ToolFederation(
        listOf(ToolAdapter(ToolLevels.SANDBOX, SandboxToolSource(), SandboxToolWorker(root))),
    )
    private val registry = federation.registry
    private val store = JvmAgentSessionStore(root.resolve(".sidr-agent/session.json"))
    private val executor = AgentExecutor(registry, federation.executor, worldContext = worldContext)

    // Named `runner`, not `run`: this class already has a `run` member, and `kotlin.run { }` is used
    // nowhere here precisely so no reader has to work out which `run` a call resolves to.
    private val runner = RunAgentSessionUseCase(executor, store)

    // A4′ phase 2 (spec §6.5 as amended): after a finished outcome, offer to undo what can be undone.
    private val undo = UndoAgentSessionUseCase(
        StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry),
        SandboxToolInverses(),
        registry,
    )

    // One offering for both users of it: the bridge planner below and the learned-plan cache (same gates by construction).
    private val descriptions = SandboxToolDescriptions()

    // The model path (`ask`): the domain's one bridge over this consumer's levels, its binder after it.
    private val modelPlanner = BridgeModelPlanner(
        registry = registry,
        descriptions = descriptions,
        transport = transport,
        levels = SandboxToolDescriptions.OFFERED_LEVELS,
        folding = JdkTextFolding,
    )
    private val binder = SandboxMentionBinder(root, SandboxFileSort(root, JdkTextFolding, worldContext), registry)

    // The learned-plan cache (A4′ phase 4, B-13): the same registry, descriptions, levels, binder and folding as
    // the bridge above, so a replay is judged by the gates a fresh reading is — and a file beside the session's.
    private val learnedStore = JvmLearnedPlanStore(root.resolve(".sidr-agent/learned-plans.json"))
    private val learnedPlans = DefaultLearnedPlans(
        store = learnedStore,
        registry = registry,
        descriptions = descriptions,
        levels = SandboxToolDescriptions.OFFERED_LEVELS,
        binder = binder,
        folding = JdkTextFolding,
    )
    private val recordLearned = RecordLearnedPlanUseCase(learnedStore)

    private var printed = 0

    suspend fun run(goalText: String): ExecutionState {
        val restored = restore()
        val session = restored ?: when (val command = modelCommandOf(goalText)) {
            null -> start(goalText) ?: return ExecutionState.Blocked
            else -> when (val read = replayOrInterpret(command)) {
                is Interpreted.Started -> read.session
                is Interpreted.NotStarted -> return read.state
            }
        }

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
            offerUndo(ran)
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
            out("No plan for: \"$goalText\". This harness understands: remove <file name> · rename <file name> to <new name> · ask <goal>")
            return null
        }
        return (store.active() as? OperationResult.Success)?.value
    }

    /** The goal after `ask `, or `null` when [goalText] is not a model goal. */
    private fun modelCommandOf(goalText: String): String? {
        val text = goalText.trim()
        if (!text.startsWith("$ASK_VERB ")) return null
        return text.removePrefix("$ASK_VERB ").trim().takeIf { it.isNotEmpty() }
    }

    /** What the model path made of a command: a started session, or the state it ended in with nothing started. */
    private sealed interface Interpreted {
        data class Started(val session: AgentSession) : Interpreted
        data class NotStarted(val state: ExecutionState) : Interpreted
    }

    private fun notStarted(line: String, state: ExecutionState = ExecutionState.Blocked): Interpreted {
        out(line)
        return Interpreted.NotStarted(state)
    }

    /** Step 2c: a learned form first, the model only on a miss (B-13). */
    private suspend fun replayOrInterpret(command: String): Interpreted =
        when (val replay = learnedPlans.replay(command)) {
            Replay.Miss -> interpret(command)
            is Replay.Bound -> {
                out("Learned command: ${replay.form.key}")
                // A replay writes nothing: no `learnable`, so the card's `y` records nothing either.
                if (replay.act.needsCard()) card(replay.act, learnable = null) else startAtOnce(replay.act)
            }
        }

    /** A bound reading that needs no card: started with no question; the engine's own prompts still apply. */
    private suspend fun startAtOnce(act: Interpretation.Act): Interpreted {
        val plan = act.plan() ?: return notStarted("A pick is owed before this reading can run, and this harness takes none. Nothing ran.")
        return startPlanned(act, plan, learnable = null)
    }

    /**
     * Model → validator (inside [BridgeModelPlanner]) → binder → the card. Exhaustive with no `else` over
     * [ModelPlanning] and [Binding]: a new answer must decide here what it means for this consumer.
     */
    private suspend fun interpret(command: String): Interpreted {
        val goal = AgentGoal(text = command, shape = GoalShape.Free(command))
        return when (val planning = modelPlanner.plan(command)) {
            is ModelPlanning.Proposed -> when (val binding = binder.bind(goal, planning.steps)) {
                // K12 (A4′ acceptance tail, task 4): D3's second amendment, as on Android — a reading that owes no pick
                // and sends no model text out starts at once, under the model's mark, and teaches nothing (I5).
                is Binding.Bound -> if (binding.interpretation.needsCard()) {
                    card(binding.interpretation, learnable = learnedPlans.learnable(command, planning.steps))
                } else {
                    startedByModel(binding.interpretation)
                }
                is Binding.NotFound -> notStarted("No file in the sandbox answers to \"${binding.mention}\". Nothing ran.")
                is Binding.NotUnderstood -> notStarted("Could not read \"${binding.words}\". Nothing ran.")
                Binding.Unavailable -> notStarted(
                    "The reading could not be bound here: the sandbox could not be read, or a file name answers to " +
                        "more than one file (this harness takes no pick). Nothing ran.",
                )
            }
            is ModelPlanning.ToAssistant -> notStarted("The model reads a question, not a plan: \"${planning.question}\". This harness has no assistant. Nothing ran.")
            is ModelPlanning.Clarify -> notStarted("The model asks first: \"${planning.question}\". Nothing ran.")
            is ModelPlanning.Navigate -> notStarted("The model names a launcher screen (${planning.destination.wire}); this harness has none. Nothing ran.")
            ModelPlanning.Declined -> notStarted("The model found no tool that fits. Nothing ran.")
            is ModelPlanning.Unavailable -> notStarted("The model gave no usable answer: ${planning.reason}. Nothing ran.")
        }
    }

    /**
     * D3, second amendment on this consumer (controller ruling K12, A4′ acceptance tail task 4): a fresh bound reading
     * that owes no pick and sends no model-written text off this computer (`needsCard()` false) is printed under the
     * model's mark — the JVM's «Предложено моделью» (K4) — and started at once, exactly as a learned command is
     * ([startAtOnce]); the engine's consent prompt of a risky step (`delete_file`) is asked as for every plan. It
     * records nothing (owner answer I5 «не засчитывать»: only a reading confirmed on the card teaches).
     *
     * **A named consequence:** every sandbox tool is `LOCAL` and [SandboxMentionBinder] owes no pick (it fails a file
     * choice closed), so no sandbox reading needs the card today — `ask` starts every bound reading here, the [card]
     * branch is reached only by a future `EXTERNAL` tool or a binder that owes a pick, and a reading can no longer
     * teach the learned-plan file through `ask` (the file is still read by [replayOrInterpret]).
     */
    private suspend fun startedByModel(act: Interpretation.Act): Interpreted {
        printReading("Suggested by the model, started at once — \"${act.goal.text}\":", act)
        return startAtOnce(act)
    }

    /** One line per step: the values it will be called with and who said each, every reference as the step it names. */
    private fun printReading(header: String, act: Interpretation.Act) {
        out(header)
        act.steps.forEach { step ->
            val origins = act.origins[step.index].orEmpty()
            val args = step.invocation.args.entries.joinToString(", ") { (name, source) ->
                when (source) {
                    is ArgSource.Literal -> "$name=${source.value} [${labelOf(origins[name])}]"
                    is ArgSource.FromStep -> "$name ← step ${source.stepIndex} ${source.key}"
                }
            }
            out("  ${step.index}. ${step.invocation.id.value}($args) — risk ${step.risk}")
        }
    }

    /**
     * The interpretation card: every step with the values it will be called with and who said each — the
     * user, this device, or the model — and every reference as the earlier step's output it names. Only a
     * `y` starts it, through `startPlanned`; anything else, or no answer at all, drops the reading
     * (`Cancelled`: the person did not say yes; nothing was started, nothing is stored). Since K12 only a reading
     * that [needsCard] reaches it ([startedByModel]).
     */
    private suspend fun card(act: Interpretation.Act, learnable: LearnedPlanShape?): Interpreted {
        // No pick is ever owed from this consumer's binder (it fails a file choice closed); were one owed,
        // nothing would start.
        val plan = act.plan() ?: return notStarted("A pick is owed before this reading can run, and this harness takes none. Nothing ran.")
        printReading("The model's reading of \"${act.goal.text}\":", act)
        out("Run this plan? [y/N]")
        val answer = ask() ?: return notStarted("No answer given — the reading is dropped; nothing was started.", ExecutionState.Cancelled)
        if (answer.trim().lowercase() != "y") return notStarted("Not run.", ExecutionState.Cancelled)

        return startPlanned(act, plan, learnable)
    }

    /** `startPlanned`, then — only once the session started — what a fresh reading teaches is recorded (B-8). */
    private suspend fun startPlanned(act: Interpretation.Act, plan: ExecutionPlan, learnable: LearnedPlanShape?): Interpreted {
        val starting = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry)
        return when (val started = starting.startPlanned(act.goal, plan)) {
            is OperationResult.Failure -> notStarted("Store failure: ${started.error}", ExecutionState.Failed)
            is OperationResult.Success -> {
                val session = started.value ?: return notStarted("The reading does not check out against this registry. Nothing ran.")
                if (learnable != null && recordLearned.record(learnable) is OperationResult.Failure) {
                    out("The learned-plan file could not be read or written; this reading is not remembered.")
                }
                Interpreted.Started(session)
            }
        }
    }

    /** Who said a value on the card; a value with no origin is shown as the model's (over-shown, never under). */
    private fun labelOf(origin: ValueOrigin?): String = when (origin) {
        is ValueOrigin.SaidBy -> if (origin.principal == PrincipalId.USER) "user" else origin.principal.value
        ValueOrigin.Ours -> "device"
        ValueOrigin.ModelGenerated, null -> "model"
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
        offerUndo(resolved)
        return resolved.state
    }

    /** Offers «undo» when the finished session has something undoable, and names what it cannot undo. */
    private suspend fun offerUndo(finished: AgentSession) {
        val review = undo.review(finished)
        if (review.none { it.undo is Undo.By }) return
        review.filter { it.undo == Undo.NotUndoable }.forEach { out("Cannot be undone: step ${it.stepIndex}") }
        out("Undo what can be undone? [y/N]")
        if (ask()?.trim()?.lowercase() != "y") return
        val undoing = (undo.undo(finished) as? OperationResult.Success)?.value ?: return
        val done = (runner.run(undoing) as? OperationResult.Success)?.value ?: return
        printed = 0
        printTrace(done)
        out("Undo outcome: ${done.state}")
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
        is TraceEvent.ConsentRequested -> "ConsentRequested(${event.index}, ${event.reasons})"
        is TraceEvent.ConsentResolved -> "ConsentResolved(${event.index}, granted=${event.granted})"
        is TraceEvent.ToolInvoked -> "ToolInvoked(${event.index}, ${event.toolId.value})"
        is TraceEvent.ToolObserved -> "ToolObserved(${event.index}, ${event.result})"
        TraceEvent.SessionPaused -> "SessionPaused"
        TraceEvent.SessionResumed -> "SessionResumed"
        is TraceEvent.SessionEnded -> "SessionEnded(${event.state})"
    }

    private companion object {
        /** The one word that sends a goal to the model (B-13); everything else stays `FilePlanner`'s. */
        const val ASK_VERB = "ask"
    }
}
