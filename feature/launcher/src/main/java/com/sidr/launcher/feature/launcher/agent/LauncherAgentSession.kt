package com.sidr.launcher.feature.launcher.agent

import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepUndo
import com.sidr.launcher.domain.agent.UndoAgentSessionUseCase
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
 * puts a pending consent checkpoint back on screen rather than stepping past it.
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

    private fun show(next: AgentSession?) {
        _session.value = next
        _undoReview.value = next?.let(undoSession::review).orEmpty()
    }

    fun attach(id: AgentSessionId) = scope.launch {
        val active = (store.active() as? OperationResult.Success)?.value ?: return@launch
        if (active.id != id) return@launch
        show((runSession.run(active) as? OperationResult.Success)?.value)
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
                show((resumeSession.resume(paused) as? OperationResult.Success)?.value)
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
