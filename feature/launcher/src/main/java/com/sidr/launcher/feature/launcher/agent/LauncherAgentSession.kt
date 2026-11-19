package com.sidr.launcher.feature.launcher.agent

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase
import com.sidr.launcher.domain.agent.Resumption
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepUndo
import com.sidr.launcher.domain.agent.UndoAgentSessionUseCase
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.ForgetLearnedPlanUseCase
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The seventh collaborator. It exists *because* Phase 1 happened: the agent runtime is a peer of the
 * command session and the app list, not a tenth responsibility inside an 886-line ViewModel.
 *
 * A session that outlived its process is presented as `ExecutionState.Paused` and never resumed
 * silently: the user asked for a two-step action minutes or days ago, and continuing without asking
 * would be the system deciding on their behalf. [continueSession]'s behaviour since Task 4 (A4′ phase
 * 2, spec §6.4, owner fork F2-3.1) depends on [AgentSession.replansOnResume]: if nothing in the plan
 * can have acted yet, Continue starts a NEW session that plans the goal afresh — no `SessionResumed`,
 * a different id — and replaces the paused one in the store (if the goal no longer plans, the stale
 * session instead ends `Failed` and nothing is performed — owner decision 2026-10-03, M1(b));
 * otherwise it resumes from the cursor as before, recording `SessionResumed`, setting `Running`, and letting the engine re-evaluate — which
 * puts a pending consent checkpoint back on screen rather than stepping past it. A paused plan the MODEL
 * wrote (A4′ phase 3a, spec §7.12.3 condition 4) is neither: the engine hands it back, this collaborator
 * closes the stale session and passes its goal text to [onReroute] — back through the whole command route.
 * One the model wrote in which a step already ACTED (A4′ phase 3b, Task 11; fork 3b-A, B-10) comes back
 * as `Resumption.NeedsCard`: the stale session is closed and its remainder — the steps after the last act
 * — goes to [onRemainder], the shape of [onReroute], where the command session binds its apps again on the
 * device and shows the interpretation card; no model is asked. A finished `Failed` or `Blocked` card of a
 * plan the model wrote offers the same remainder ([remainderOffered], [retryRemainder] — «Повторить
 * остаток»), built from the session on screen by the one domain entry
 * (`ResumeAgentSessionUseCase.remainder`, controller ruling E11-P3), never when nothing is left
 * (ruling E11-P6). A «Продолжить» the store cannot carry out is SAID ([resumeFailed], fork 3b-F) — it used
 * to take the card down and put nothing in its place.
 *
 * The session a learned command started carries that command's form ([learned], A4′ phase 4, B-10) while it is
 * the one on screen, and «Забыть» forgets the form ([forgetLearned]).
 *
 * Everything it touches is a `:domain` port, so it is unit-testable without Android or Compose.
 */
internal class LauncherAgentSession(
    private val runSession: RunAgentSessionUseCase,
    private val resolveConsent: ResolveConsentUseCase,
    private val cancelSession: CancelAgentSessionUseCase,
    private val store: AgentSessionStore,
    private val scope: CoroutineScope,
    private val resumeSession: ResumeAgentSessionUseCase,
    private val undoSession: UndoAgentSessionUseCase,
    /** Where a paused plan the model wrote goes: its words, back through the command route (spec §7.12.3 condition 4). */
    private val onReroute: (String) -> Unit,
    /** Where the remainder of a model plan that acted goes: the interpretation card, bound again on the device (B-10). */
    private val onRemainder: (AgentGoal, ExecutionPlan) -> Unit,
    /** «Забыть» on the card of a learned command's session (A4′ phase 4, B-10): deletes that command's form. */
    private val forgetLearnedPlan: ForgetLearnedPlanUseCase,
) {
    private val _session = MutableStateFlow<AgentSession?>(null)
    val session: StateFlow<AgentSession?> = _session.asStateFlow()

    /**
     * Whether a consent decision is in flight. Bound to `SidrActionGate`'s `confirming`, which
     * disables BOTH controls — so the window between the tap and the engine's answer cannot be used
     * to tap again. The conditional write in [AgentSessionStore.recordConsentIfPending] is what makes
     * a double tap harmless even if it happens; this flag is what stops it looking like it worked.
     */
    private val _confirming = MutableStateFlow(false)
    val confirming: StateFlow<Boolean> = _confirming.asStateFlow()

    /**
     * What «Отменить» would undo for the session on screen, and what it could not (A4′ phase 2, spec
     * §6.6). Recomputed every time the session on screen changes — through [show], never by a
     * collector that would outlive a test's scope.
     */
    private val _undoReview = MutableStateFlow<List<StepUndo>>(emptyList())
    val undoReview: StateFlow<List<StepUndo>> = _undoReview.asStateFlow()

    /**
     * «Продолжить» could not be carried out — the store failed (fork 3b-F). Typed, never text: the surface
     * says «Не удалось продолжить план.» where the paused card stood. Cleared by the next [show].
     */
    private val _resumeFailed = MutableStateFlow(false)
    val resumeFailed: StateFlow<Boolean> = _resumeFailed.asStateFlow()

    /**
     * Whether the finished card on screen offers «Повторить остаток» (A4′ phase 3b, Task 11): a `Failed` or
     * `Blocked` session whose plan has a remainder — `ResumeAgentSessionUseCase.remainder`, the same entry
     * [retryRemainder] reads, so the button cannot promise what the tap does not do. Never on a plan the
     * clock stopped on a call that may still be acting — that entry withholds it on the evidence that
     * withholds «Отменить» (owner fork F2-1b, ruling E11-R1). Recomputed by [show].
     */
    private val _remainderOffered = MutableStateFlow(false)
    val remainderOffered: StateFlow<Boolean> = _remainderOffered.asStateFlow()

    /**
     * The form of the learned command whose session is on screen, or `null` (A4′ phase 4, plan §0.2 B-10): the card
     * says «Выученная команда» and offers «Забыть» while it is non-null. Held as [learnedMark] — the session id
     * beside the form — and set only by [attach], for the session a learned command started: step 2c's at once, or
     * a learned card's «Выполнить» (B-7). [show] drops it whenever the session on screen is not the one it was set
     * for — another session, or none ([cancel], a resume closing it); [forgetLearned] drops it once the form is
     * deleted. [restoreOnStart] drops it outright: the mark lives here, in memory, never in the store, so a session
     * restored at start comes back without it, even the very session that carried it (fork 4-5's named limit).
     */
    private val _learned = MutableStateFlow<CommandForm?>(null)
    val learned: StateFlow<CommandForm?> = _learned.asStateFlow()
    private var learnedMark: Pair<AgentSessionId, CommandForm>? = null

    private fun show(next: AgentSession?) {
        _session.value = next
        _undoReview.value = next?.let(undoSession::review).orEmpty()
        _resumeFailed.value = false
        _remainderOffered.value = next?.takeIf { it.state == ExecutionState.Failed || it.state == ExecutionState.Blocked }
            ?.let(resumeSession::remainder) != null
        learnedMark = learnedMark?.takeIf { (id, _) -> id == next?.id }
        _learned.value = learnedMark?.second
    }

    /**
     * Shows the session [id] — the one just started and persisted — after running it. [learned] is the form of the
     * learned command that started it, or `null` for every other start (B-7, B-10): the mark is set here for that
     * session only, and never cleared here — a session that is not the marked one drops it in [show].
     *
     * **Set once the run has returned, immediately before [show]** (Task 7 review, fix round 1). While the run is
     * in flight the screen still shows the previous card and that card's mark: a mark set earlier would belong to
     * a session not yet on screen, and «Забыть» under the old card ([forgetLearned]) would forget the command still
     * running — or «Закрыть» on it would drop the new session's mark before it arrived.
     */
    fun attach(id: AgentSessionId, learned: CommandForm? = null) = scope.launch {
        val active = (store.active() as? OperationResult.Success)?.value ?: return@launch
        if (active.id != id) return@launch
        val ran = (runSession.run(active) as? OperationResult.Success)?.value
        if (learned != null) learnedMark = id to learned
        show(ran)
    }

    /**
     * «Забыть» on a learned command's card (B-10, `DOC-AML-5`): its form is deleted from the learned store, on
     * [scope] like every other write this card makes. The mark goes only when the store says the form is gone
     * ([OperationResult.Success]); on a failure it stays — the command is still learned, and the card says so.
     * A mark that moved on to another session meanwhile is not touched.
     */
    fun forgetLearned() {
        val mark = learnedMark ?: return
        scope.launch {
            if (forgetLearnedPlan.forget(mark.second) is OperationResult.Success && learnedMark == mark) {
                learnedMark = null
                _learned.value = null
            }
        }
    }

    /**
     * Called once from the ViewModel's init. Anything genuinely interrupted is presented as Paused,
     * with an honest offer — but two rows on disk are **not** that, and each is skipped for its own
     * reason.
     *
     * **A terminal session is finished, not interrupted.** `RunAgentSessionUseCase.persist` saves a
     * terminal state and then deletes it; a process death between those two leaves a `Completed` (or
     * `Failed`/`Cancelled`/`Blocked`) row behind. Pausing it would tell the user "you left before this
     * plan finished" about a plan that did finish. Finish the delete the dead process owed instead.
     *
     * **A session already Paused is not paused a second time.** [AgentSession.pausedForRestore]
     * records `SessionPaused`, and the trace is 1:1 with reality (`DOC-ILM-3`): a session that paused
     * once carries one pause, not one per process death. This launcher is the home screen, so without
     * this branch the trace grows by a row on every start for as long as the plan sits unanswered.
     */
    fun restoreOnStart() = scope.launch {
        // Fork 4-5's named limit (B-10): no mark survives into a restored session — see [learned].
        learnedMark = null
        _learned.value = null
        val active = (store.active() as? OperationResult.Success)?.value ?: return@launch

        if (active.state.isTerminal) {
            store.delete(active.id)
            return@launch
        }

        if (active.state == ExecutionState.Paused) {
            show(active)
            return@launch
        }

        val paused = active.pausedForRestore()
        store.save(paused)
        show(paused)
    }

    /**
     * A tap that lands while a previous «Продолжить» (or, from Task 6, «Отменить») is still running
     * does nothing. Before A4′ phase 2 two taps drove the same session twice; since resume can
     * re-plan into a NEW session id (F2-3.1), the store's conditional write no longer de-duplicates
     * them, and each tap would perform step 0 (plan §0.3 P6). Confined to the main thread like every
     * caller here, so the check and the set cannot interleave.
     */
    private var busy = false

    fun continueSession() {
        if (busy) return
        val paused = _session.value ?: return
        busy = true
        scope.launch {
            try {
                when (val resumed = (resumeSession.resume(paused) as? OperationResult.Success)?.value) {
                    is Resumption.Ran -> show(resumed.session)
                    is Resumption.NeedsRoute -> {
                        // Condition 4: the model's old reading is not acted on. Close it, then take the
                        // user's own words through the whole command route again — local-only, the
                        // provider, the network and the interpretation card are all met afresh.
                        cancelSession.cancel(paused.id)
                        show(null)
                        onReroute(resumed.goal.text)
                    }
                    // Fork 3b-A (B-10): a model plan that acted is not continued on what it saw before the
                    // pause, and the model is not asked. Close it; its remainder goes to the card, re-bound.
                    is Resumption.NeedsCard -> {
                        cancelSession.cancel(paused.id)
                        show(null)
                        onRemainder(resumed.goal, resumed.plan)
                    }
                    // Fork 3b-F: the store failed. Said, not silent. The stale card does not stay with its
                    // «Продолжить» — had the save failed after a step acted, it would offer that step again;
                    // the next start restores what the store actually holds.
                    null -> {
                        show(null)
                        _resumeFailed.value = true
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    fun confirm(stepIndex: Int) = resolve(stepIndex, granted = true)

    fun deny(stepIndex: Int) = resolve(stepIndex, granted = false)

    private fun resolve(stepIndex: Int, granted: Boolean) = scope.launch {
        // No session on screen means no button was on screen either; nothing to resolve. The plan
        // wrote `requireNotNull` here — a throw inside viewModelScope would reach the user as a crash,
        // which the "never throw to UI" rule forbids, so the impossible case simply does nothing.
        val current = _session.value ?: return@launch
        _confirming.value = true
        try {
            // A second tap resolves to null: the conditional write matched no row. Keep the current
            // surface rather than blanking it — the user pressed a button that had already been honoured.
            val next = (resolveConsent.resolve(current.id, stepIndex, granted)
                as? OperationResult.Success)?.value
            if (next != null) show(next)
        } finally {
            _confirming.value = false
        }
    }

    fun cancel() = scope.launch {
        _session.value?.let { cancelSession.cancel(it.id) }
        show(null)
        _confirming.value = false
    }

    /**
     * «Повторить остаток» (A4′ phase 3b, Task 11): the remainder of the finished `Failed`/`Blocked` model-plan
     * card on screen — the steps after its last act — goes to [onRemainder], built from that in-memory session
     * (a terminal session is no longer in the store) by the one domain entry. Nothing runs here and no model
     * is asked; «Выполнить» on the card starts a NEW session. Shares [busy]; nothing happens when no
     * remainder is offered.
     */
    fun retryRemainder() {
        if (busy) return
        val finished = _session.value ?: return
        if (finished.state != ExecutionState.Failed && finished.state != ExecutionState.Blocked) return
        val rest = resumeSession.remainder(finished) ?: return
        show(null)
        onRemainder(finished.goal, rest)
    }

    /**
     * «Отменить» (owner fork F2-3.3): a NEW session that undoes the finished one on screen, run through
     * the same engine and gates. Never automatic. Shares [busy] with [continueSession] — one run at a
     * time from this card, and a second tap does nothing (plan §0.3 P6).
     */
    fun undo() {
        if (busy) return
        val finished = _session.value ?: return
        busy = true
        scope.launch {
            try {
                val undoing = (undoSession.undo(finished) as? OperationResult.Success)?.value ?: return@launch
                show((runSession.run(undoing) as? OperationResult.Success)?.value)
            } finally {
                busy = false
            }
        }
    }
}
