package com.sidr.launcher.feature.launcher.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.component.SidrActionGate
import com.sidr.launcher.core.ui.component.SidrBlockedState
import com.sidr.launcher.core.ui.component.SidrErrorSurface
import com.sidr.launcher.core.ui.component.SidrResultSurface
import com.sidr.launcher.core.ui.component.SidrResultTone
import com.sidr.launcher.core.ui.component.SidrSurfaceAction
import com.sidr.launcher.core.ui.component.SidrTertiaryButton
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
import com.sidr.launcher.domain.agent.StepUndo
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.Undo
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
 * `Cancelled` before any step acted renders nothing at all — the ordinary command surface is the right
 * thing to be looking at once a plan has been called off. `Cancelled` AFTER a step acted is a finished
 * card (A4′ phase 3b, Task 12 — phase 2's N2, plan §0.2 B-11): what acted is real, so the card says what
 * ran and offers to undo it.
 *
 * A4′ phase 4, Task 7 (plan §0.2 B-10): the session a learned command started — [learned], its form, non-null
 * while that session is on screen (`LauncherAgentSession.learned`) — carries the line «Выученная команда» and
 * «Забыть» ([onForgetLearned]) under its card. Any other session is drawn exactly as before, with no node added;
 * a session that draws no card (`Cancelled` before any step acted) draws no mark either.
 *
 * A4′ acceptance tail, task 4 (controller ruling K4): the session a FRESH model reading started without the
 * interpretation card — [proposedByModel], `LauncherAgentSession.proposedByModel` — carries the line «Предложено
 * моделью» under its card, the same way; any other session draws no such node.
 */
@Composable
internal fun AgentSessionSurface(
    session: AgentSession,
    toolProvenance: Map<ToolId, StepProvenance>,
    dynamicLabels: Map<ToolId, DynamicToolLabel>,
    confirming: Boolean,
    onConfirm: (Int) -> Unit,
    onDeny: (Int) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    // A4′ phase 2 (F2-3.3). Defaulted so a caller that has no undo — a test of another state — draws
    // no button; the one production caller, `LauncherScreen`, passes both.
    undoReview: List<StepUndo> = emptyList(),
    onUndo: () -> Unit = {},
    // A4′ phase 3b, Task 11 (fork 3b-A). Defaulted like undo: whether this finished `Failed`/`Blocked`
    // model-plan card offers «Повторить остаток» (`LauncherAgentSession.remainderOffered`), and its tap.
    remainderOffered: Boolean = false,
    onRetryRemainder: () -> Unit = {},
    // A4′ phase 4, Task 7. Defaulted like undo: a caller that shows no learned command draws no mark.
    learned: CommandForm? = null,
    onForgetLearned: () -> Unit = {},
    // A4′ acceptance tail, task 4 (K4). Defaulted like the learned mark: a caller that shows no model session draws none.
    proposedByModel: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if ((learned == null && !proposedByModel) || session.title() == null) {
        AgentSessionCard(
            session, toolProvenance, dynamicLabels, confirming, onConfirm, onDeny, onContinue, onDismiss,
            undoReview, onUndo, remainderOffered, onRetryRemainder, modifier,
        )
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AgentSessionCard(
            session, toolProvenance, dynamicLabels, confirming, onConfirm, onDeny, onContinue, onDismiss,
            undoReview, onUndo, remainderOffered, onRetryRemainder, Modifier,
        )
        if (proposedByModel) ModelProposalMark()
        if (learned != null) LearnedCommandMark(onForget = onForgetLearned)
    }
}

/** The card itself, one component per runtime state — see [AgentSessionSurface]. */
@Composable
private fun AgentSessionCard(
    session: AgentSession,
    toolProvenance: Map<ToolId, StepProvenance>,
    dynamicLabels: Map<ToolId, DynamicToolLabel>,
    confirming: Boolean,
    onConfirm: (Int) -> Unit,
    onDeny: (Int) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    undoReview: List<StepUndo>,
    onUndo: () -> Unit,
    remainderOffered: Boolean,
    onRetryRemainder: () -> Unit,
    modifier: Modifier,
) {
    // Null for exactly one shape — Cancelled before any step acted — which renders nothing; every other
    // state has a title, and so has a Cancelled session in which a step acted (N2, `title()`'s KDoc).
    val title = session.title() ?: return
    val subject = session.goal.subject()
    val dismiss = SidrSurfaceAction(sidrString(R.string.launcher_agent_cancel), onDismiss)
    // «Отменить» exists only when at least one step can actually be undone; and when only part can,
    // the card names the rest, so the button's reach is never implied larger than it is (B14).
    val undo = if (undoReview.any { it.undo is Undo.By }) {
        SidrSurfaceAction(sidrString(R.string.launcher_agent_undo), onUndo)
    } else {
        null
    }
    val notUndoable = if (undo == null) {
        null
    } else {
        undoReview
            .filter { it.undo == Undo.NotUndoable }
            .mapNotNull { pending -> session.plan.steps.firstOrNull { it.index == pending.stepIndex } }
            .map { it.line(subject, dynamicLabels) }
            .takeIf { it.isNotEmpty() }
            ?.let { sidrString(R.string.launcher_agent_undo_not_all, it.joinToString(", ")) }
    }
    // «Повторить остаток» (A4′ phase 3b, Task 11): the steps after the last act, back on the interpretation
    // card. The finished card has one secondary slot, and «Отменить» keeps it when both exist — the rest is
    // offered only when nothing can be undone (named in the task report, not decided by a test).
    val secondary = undo ?: if (remainderOffered) {
        SidrSurfaceAction(sidrString(R.string.launcher_agent_retry_remainder), onRetryRemainder)
    } else {
        null
    }

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
            AgentPlanSteps(
                session = session,
                subject = subject,
                toolProvenance = toolProvenance,
                dynamicLabels = dynamicLabels,
            )
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
                    type = checkpoint.reasons.gateType(),
                    title = title,
                    // A4′ phase 3b, Task 10 (A0.5's F5): the gated step names the value it WILL receive —
                    // a reference to an earlier step's output is resolved for display from what the
                    // session recorded (`PlanStep.line`'s KDoc); the plan list below keeps the default.
                    consequence = sidrString(
                        R.string.launcher_agent_gate_consequence,
                        step.line(subject, dynamicLabels, session.observations),
                    ),
                    confirmLabel = sidrString(R.string.launcher_agent_gate_confirm),
                    onConfirm = { onConfirm(checkpoint.index) },
                    onCancel = { onDeny(checkpoint.index) },
                    modifier = modifier,
                    // The plan list is NOT drawn in this state (a named A0 limitation), so the
                    // card carries the pending step's own provenance: without it the user would be
                    // asked to approve a step while being told nothing about where its effect goes.
                    provenance = {
                        AgentStepProvenance(toolProvenance[step.invocation.id])
                        AgentProvenance(session = session)
                    },
                    confirming = confirming,
                )
            }
        }

        // Not an error: the user walked away mid-plan and is being offered the rest of it.
        ExecutionState.Paused -> SidrResultSurface(
            tone = SidrResultTone.Partial,
            title = title,
            modifier = modifier,
            // F2-3.2: the body says what «Продолжить» will actually do, by the predicate the resume
            // use case acts on — a plan that has only looked is planned afresh.
            body = sidrString(
                if (session.replansOnResume()) {
                    R.string.launcher_agent_paused_body_replan
                } else {
                    R.string.launcher_agent_paused_body
                },
            ),
            provenance = {
                AgentPlanSteps(
                    session = session,
                    subject = subject,
                    toolProvenance = toolProvenance,
                    dynamicLabels = dynamicLabels,
                )
                AgentProvenance(session = session)
            },
            primaryAction = SidrSurfaceAction(
                sidrString(R.string.launcher_agent_paused_continue),
                onContinue,
            ),
            secondaryAction = dismiss,
        )

        // «Plan complete» is only true when every step actually ran AND no step's outcome is out of
        // our sight. Anything short of that is not success, and `DOC-ILM-4` says it must not be shown
        // as success (review finding F3; A4′ phase 0).
        ExecutionState.Completed -> {
            // Three outcomes, and the ORDER is the behaviour (final review, controller ruling R17):
            //  1. a step did NOT run — skipped by its precondition, or failed and let pass by the
            //     budget: the plan is partial, «выполнено не всё». Asked FIRST, because that stays true
            //     even when another step was handed off, and the handed-off title below would hide it;
            //  2. every step ran, but at least one was HANDED OFF — its act left the launcher and its
            //     outcome is out of our sight (a cancelled `uninstall_app` is exactly this). Not
            //     «План выполнен», which would claim an outcome we cannot see; and not «выполнено не
            //     всё» either, whose body says a step «did not run» directly above a step list saying
            //     «передано системе». Same `Partial` tone as (1): the result is not a whole success,
            //     and no new status is invented for it;
            //  3. otherwise the plan is whole.
            val (tone, shownTitle, body) = when {
                !session.everyStepExecuted() -> Triple(
                    SidrResultTone.Partial,
                    sidrString(R.string.launcher_agent_completed_partial_title),
                    sidrString(R.string.launcher_agent_completed_partial_body),
                )
                session.anyStepHandedOff() -> Triple(
                    SidrResultTone.Partial,
                    sidrString(R.string.launcher_agent_completed_handed_off_title),
                    sidrString(R.string.launcher_agent_completed_handed_off_body),
                )
                else -> Triple(SidrResultTone.Completed, title, null)
            }
            SidrResultSurface(
                tone = tone,
                title = shownTitle,
                modifier = modifier,
                body = body,
                provenance = {
                    AgentPlanSteps(
                        session = session,
                        subject = subject,
                        toolProvenance = toolProvenance,
                        dynamicLabels = dynamicLabels,
                    )
                    AgentProvenance(session = session)
                    notUndoable?.let { SidrText(text = it, role = SidrTextRole.PROVENANCE) }
                },
                primaryAction = dismiss,
                secondaryAction = undo,
            )
        }

        ExecutionState.Failed -> SidrErrorSurface(
            title = title,
            modifier = modifier,
            next = notUndoable,
            primaryAction = dismiss,
            secondaryAction = secondary,
        )

        ExecutionState.Blocked -> SidrBlockedState(
            title = title,
            body = sidrString(R.string.launcher_agent_blocked_body),
            modifier = modifier,
            next = notUndoable,
            primaryAction = dismiss,
            secondaryAction = secondary,
        )

        // A4′ phase 3b, Task 12 — N2, answered «partial» (plan §0.2 B-11, `DOC-ILM-4`; the owner may
        // overturn). Reached only when a step acted (`title()` returned above otherwise): the user refused a
        // later step. The `Completed` card's shape, not `SidrBlockedState`, whose body is required and would
        // have to say who stopped it: the title alone says the plan stopped (R12-1), the step lines say what
        // acted and what never ran, and the undo is what it is on every finished card. The secondary slot is
        // `undo` only — «Повторить остаток» stays `Failed`/`Blocked`'s (`LauncherAgentSession.show`).
        ExecutionState.Cancelled -> SidrResultSurface(
            tone = SidrResultTone.Partial,
            title = title,
            modifier = modifier,
            provenance = {
                AgentPlanSteps(
                    session = session,
                    subject = subject,
                    toolProvenance = toolProvenance,
                    dynamicLabels = dynamicLabels,
                )
                AgentProvenance(session = session)
                notUndoable?.let { SidrText(text = it, role = SidrTextRole.PROVENANCE) }
            },
            primaryAction = dismiss,
            secondaryAction = undo,
        )
    }
}

/**
 * «Выученная команда» and «Забыть» (A4′ phase 4, B-10) — under the card of a learned command's session, composed
 * of shipped primitives only (nothing added to `core/ui`). «Забыть» deletes the command's form; the card stays.
 */
@Composable
private fun LearnedCommandMark(onForget: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SidrText(
            text = sidrString(R.string.launcher_agent_learned_mark),
            role = SidrTextRole.PROVENANCE,
            modifier = Modifier.weight(1f),
        )
        SidrTertiaryButton(text = sidrString(R.string.launcher_agent_learned_forget), onClick = onForget)
    }
}

/**
 * «Предложено моделью» (A4′ acceptance tail, task 4 — D3, second amendment; controller ruling K4; `DOC-NYH-1`
 * «показывает, как поняла» — after the fact): under the card of a session a fresh model reading started without the
 * interpretation card. Shipped primitives only (nothing added to `core/ui`); no action — the card's own controls stand.
 */
@Composable
private fun ModelProposalMark() {
    SidrText(
        text = sidrString(R.string.launcher_agent_model_mark),
        role = SidrTextRole.PROVENANCE,
        modifier = Modifier.fillMaxWidth(),
    )
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
 * The plan, one row per step, in plan order. Each row names the step's own **tool** (Task 10 / A1' —
 * it used to name the rationale, which read as a launch for every `GOAL_DIRECT` step) **and** the
 * state the session actually recorded for it, rendered into the current locale — so done, skipped,
 * failed and not-yet-started read differently in greyscale and to TalkBack, not only in the colour of
 * the dot (`R-ADL-2`; review findings F3/F6).
 *
 * A step whose tool declares an `EXTERNAL` effect carries a provenance line under its row. This is the
 * **execution** half of `DOC-ILM-2` (spec §6.3), and for a `SAFE` tool it is the only half there is:
 * `SAFE` never reaches the consent gate, so nothing else on any surface would say the effect left the
 * launcher.
 */
@Composable
private fun AgentPlanSteps(
    session: AgentSession,
    subject: String,
    toolProvenance: Map<ToolId, StepProvenance>,
    dynamicLabels: Map<ToolId, DynamicToolLabel>,
) {
    val numbered = session.plan.steps.size > 1
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        session.plan.steps.forEach { step ->
            val state = session.stateOf(step)
            // A plan of two or more steps numbers its lines from one — the number «← из шага N» on the
            // interpretation card names (`stepIndex + 1`); a one-step plan keeps its line (task 7, ruling 4).
            val line = step.line(subject, dynamicLabels)
            SidrStatusMarker(
                status = state.marker(),
                label = if (numbered) {
                    sidrString(R.string.launcher_agent_step_line_numbered, step.index + 1, line, state.word())
                } else {
                    sidrString(R.string.launcher_agent_step_line, line, state.word())
                },
            )
            AgentStepProvenance(toolProvenance[step.invocation.id])
        }
    }
}

/**
 * Where one step's effect goes, or nothing at all when it stays on the device.
 *
 * `null` covers two different silences and both are correct: a `LOCAL` tool has nothing to disclose,
 * and a tool the registry does not know cannot be described — the surface claims less rather than
 * guessing. The label is a resource, never [com.sidr.launcher.domain.tool.ToolLevel.value]; see
 * `provenanceLabelFor`.
 */
@Composable
internal fun AgentStepProvenance(provenance: StepProvenance?) {
    val label = provenance?.let { provenanceLabelFor(it.level, it.effect) }
    if (label != null) {
        SidrProvenanceLine(source = sidrString(label))
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
