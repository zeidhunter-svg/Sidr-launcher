package com.sidr.launcher.feature.launcher.agent

import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
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
 * would be the system deciding on their behalf. [continueSession] records `SessionResumed`, sets
 * `Running`, and lets the engine re-evaluate — which puts a pending consent checkpoint back on screen
 * rather than stepping past it.
 *
 * Everything it touches is a `:domain` port, so it is unit-testable without Android or Compose.
 */
internal class LauncherAgentSession(
    private val runSession: RunAgentSessionUseCase,
    private val resolveConsent: ResolveConsentUseCase,
    private val cancelSession: CancelAgentSessionUseCase,
    private val store: AgentSessionStore,
    private val scope: CoroutineScope,
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

    fun attach(id: AgentSessionId) = scope.launch {
        val active = (store.active() as? OperationResult.Success)?.value ?: return@launch
        if (active.id != id) return@launch
        _session.value = (runSession.run(active) as? OperationResult.Success)?.value
    }

    /** Called once from the ViewModel's init. Anything that survived is Paused, with an honest offer. */
    fun restoreOnStart() = scope.launch {
        val active = (store.active() as? OperationResult.Success)?.value ?: return@launch
        val paused = active.pausedForRestore()
        store.save(paused)
        _session.value = paused
    }

    fun continueSession() = scope.launch {
        val paused = _session.value ?: return@launch
        _session.value = (runSession.run(paused.resumed()) as? OperationResult.Success)?.value
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
            if (next != null) _session.value = next
        } finally {
            _confirming.value = false
        }
    }

    fun cancel() = scope.launch {
        _session.value?.let { cancelSession.cancel(it.id) }
        _session.value = null
        _confirming.value = false
    }
}
