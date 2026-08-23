package com.sidr.launcher.feature.launcher.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.component.SidrActionGate
import com.sidr.launcher.core.ui.component.SidrBlockedState
import com.sidr.launcher.core.ui.component.SidrErrorSurface
import com.sidr.launcher.core.ui.component.SidrResultSurface
import com.sidr.launcher.core.ui.component.SidrResultTone
import com.sidr.launcher.core.ui.component.SidrSurfaceAction
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrProgress
import com.sidr.launcher.core.ui.primitive.SidrProvenanceLine
import com.sidr.launcher.core.ui.primitive.SidrStatusMarker
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.trace.TraceEvent
import com.sidr.launcher.feature.launcher.R

/**
 * Task 12 / A0. One component per runtime state, composed entirely from DS-5 primitives that already
 * shipped — nothing was added to `core/ui` for this, which is why no Roborazzi golden moves.
 *
 * The surface is a **function of the session**, not a script: the step list comes from
 * `session.plan.steps` and the marked row from `session.cursor`, so a 2-step plan and a 7-step plan
 * differ in list length and in nothing else. There is no two-row special case anywhere below.
 *
 * `Cancelled` renders nothing at all — the ordinary command surface is the right thing to be looking
 * at once a plan has been called off.
 */
@Composable
internal fun AgentSessionSurface(
    session: AgentSession,
    confirming: Boolean,
    onConfirm: (Int) -> Unit,
    onDeny: (Int) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Null for exactly one state (Cancelled), which renders nothing; every other state has a title.
    val title = session.state.title() ?: return
    val subject = session.goal.subject()
    val dismiss = SidrSurfaceAction(sidrString(R.string.launcher_agent_cancel), onDismiss)

    when (session.state) {
        // The planner has not answered yet. Indeterminate, because there is nothing honest to
        // measure — no plan exists to count steps against.
        ExecutionState.Planning -> AgentBlock(title = title, modifier = modifier) {
            SidrProgress(modifier = Modifier.fillMaxWidth())
        }

        ExecutionState.Running -> AgentBlock(title = title, modifier = modifier) {
            val total = session.plan.steps.size
            if (total > 0) {
                SidrProgress(
                    modifier = Modifier.fillMaxWidth(),
                    progress = (session.cursor.toFloat() / total).coerceIn(0f, 1f),
                )
            }
            AgentPlanSteps(session = session, subject = subject)
            AgentProvenance(session = session)
        }

        ExecutionState.AwaitingConsent -> {
            // The checkpoint is read back from the trace, which is where the engine actually recorded
            // it — index AND reason — rather than re-derived from the cursor. If it is somehow absent
            // the session is inconsistent, and the fail-closed move is to offer no confirm button at
            // all: no gate, no execution.
            val checkpoint = session.trace.events
                .filterIsInstance<TraceEvent.ConsentRequested>()
                .lastOrNull()
            val step = checkpoint?.let { pending ->
                session.plan.steps.firstOrNull { it.index == pending.index }
            }
            if (checkpoint != null && step != null) {
                SidrActionGate(
                    type = checkpoint.reason.gateType(),
                    title = title,
                    consequence = sidrString(
                        R.string.launcher_agent_gate_consequence,
                        step.rationale.label(subject),
                    ),
                    confirmLabel = sidrString(R.string.launcher_agent_gate_confirm),
                    onConfirm = { onConfirm(checkpoint.index) },
                    onCancel = { onDeny(checkpoint.index) },
                    modifier = modifier,
                    provenance = { AgentProvenance(session = session) },
                    confirming = confirming,
                )
            }
        }

        // Not an error: the user walked away mid-plan and is being offered the rest of it.
        ExecutionState.Paused -> SidrResultSurface(
            tone = SidrResultTone.Partial,
            title = title,
            modifier = modifier,
            body = sidrString(R.string.launcher_agent_paused_body),
            provenance = {
                AgentPlanSteps(session = session, subject = subject)
                AgentProvenance(session = session)
            },
            primaryAction = SidrSurfaceAction(
                sidrString(R.string.launcher_agent_paused_continue),
                onContinue,
            ),
            secondaryAction = dismiss,
        )

        // «Plan complete» is only true when every step actually ran. A plan that closed with a step
        // skipped by its precondition, or with a failed step the budget let pass, is a PARTIAL result
        // and `DOC-ILM-4` says it must be shown as one rather than as success (review finding F3).
        ExecutionState.Completed -> {
            val whole = session.everyStepExecuted()
            SidrResultSurface(
                tone = if (whole) SidrResultTone.Completed else SidrResultTone.Partial,
                title = if (whole) title else sidrString(R.string.launcher_agent_completed_partial_title),
                modifier = modifier,
                body = if (whole) null else sidrString(R.string.launcher_agent_completed_partial_body),
                provenance = {
                    AgentPlanSteps(session = session, subject = subject)
                    AgentProvenance(session = session)
                },
                primaryAction = dismiss,
            )
        }

        ExecutionState.Failed -> SidrErrorSurface(
            title = title,
            modifier = modifier,
            primaryAction = dismiss,
        )

        ExecutionState.Blocked -> SidrBlockedState(
            title = title,
            body = sidrString(R.string.launcher_agent_blocked_body),
            modifier = modifier,
            primaryAction = dismiss,
        )

        ExecutionState.Cancelled -> Unit
    }
}

/** The plain framed block the two in-flight states share; the safety surfaces bring their own. */
@Composable
private fun AgentBlock(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    SidrSurface(
        tone = SidrSurfaceTone.SURFACE,
        modifier = modifier.fillMaxWidth(),
        shape = SidrShapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SidrText(text = title, role = SidrTextRole.HUMAN_TITLE)
            content()
        }
    }
}

/**
 * The plan, one row per step, in plan order. Each row is the step's own typed rationale **and** the
 * state the session actually recorded for it, rendered into the current locale — so done, skipped,
 * failed and not-yet-started read differently in greyscale and to TalkBack, not only in the colour of
 * the dot (`R-ADL-2`; review findings F3/F6).
 */
@Composable
private fun AgentPlanSteps(session: AgentSession, subject: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        session.plan.steps.forEach { step ->
            val state = session.stateOf(step)
            SidrStatusMarker(
                status = state.marker(),
                label = sidrString(
                    R.string.launcher_agent_step_line,
                    step.rationale.label(subject),
                    state.word(),
                ),
            )
        }
    }
}

/**
 * Origin, in the DS-2 keystone. The details are the registered tool ids the plan will actually reach
 * — locked machine vocabulary (spec §7.1), not copy — so the user can see which capabilities are in
 * play without the surface claiming anything the registry did not.
 */
@Composable
private fun AgentProvenance(session: AgentSession) {
    SidrProvenanceLine(
        source = sidrString(R.string.launcher_agent_title),
        details = session.plan.steps.map { it.invocation.id.value },
    )
}
