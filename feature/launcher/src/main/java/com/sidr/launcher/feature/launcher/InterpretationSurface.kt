package com.sidr.launcher.feature.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sidr.launcher.core.ui.component.SidrActionProposal
import com.sidr.launcher.core.ui.component.SidrActionProposalTone
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Sizes
import com.sidr.launcher.core.ui.theme.Spacing
import com.sidr.launcher.domain.action.requiresConsent
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.SidrDestination
import com.sidr.launcher.domain.ai.bridge.modelTextGoingOut
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.feature.launcher.agent.AgentStepProvenance
import com.sidr.launcher.feature.launcher.agent.DynamicToolLabel
import com.sidr.launcher.feature.launcher.agent.StepProvenance
import com.sidr.launcher.feature.launcher.agent.line
import com.sidr.launcher.feature.launcher.agent.subject
import com.sidr.launcher.core.ui.R as UiR

/**
 * The interpretation card (A4′ phase 3a, spec §7.12.3 condition 1): what the model understood, BEFORE
 * anything runs. Built on `SidrActionProposal` — one-shot, never executing on render (`DOC-HYA-2`'s two
 * anchors in `SidrActionSafetyTest`) — so no `core/ui` component is added. It asks "is this what you
 * meant", not "do you allow the consequence": a `CONFIRM` step still stops at the engine's own consent
 * card after «Выполнить» (`DOC-ADL-1` untouched).
 *
 * **A reading of N steps** (A4′ phase 3b, fork 3b-B, Task 10): the card draws EVERY step, in plan order —
 * the description is the step lines, one per line; a value a step takes from an earlier step's output
 * reads «← из шага N» there, 1-based, because nothing has run yet (`PlanStep.line`'s KDoc). The provenance
 * slot carries, step by step in the same order, that step's model-written values (`DOC-MZN-2`, each
 * against its own origins and effect) and where its effect goes (`DOC-ILM-2`). The tone is `Confirm` when
 * ANY step needs consent. While a pick is owed the card is the pick list for the HEAD owed pick — picks
 * are answered one at a time, in step order — and «Выполнить» exists only once none is owed.
 */
@Composable
internal fun InterpretationArea(
    interpretation: Interpretation,
    provenance: Map<ToolId, StepProvenance>,
    dynamicLabels: Map<ToolId, DynamicToolLabel>,
    onRun: () -> Unit,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (interpretation) {
        is Interpretation.Navigate -> SidrActionProposal(
            title = sidrString(R.string.launcher_interpretation_title),
            description = sidrString(destinationLine(interpretation.destination)),
            tone = SidrActionProposalTone.Safe,
            onExecute = onRun,
            onCancel = onDismiss,
            executeLabel = sidrString(R.string.launcher_interpretation_run),
            modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
        is Interpretation.Act -> {
            val choice = interpretation.choices.firstOrNull()?.choice
            if (choice != null) {
                AppChoiceList(
                    mention = choice.mention,
                    candidates = choice.candidates,
                    confirm = choice.confirm,
                    onChoose = onChoose,
                    onDismiss = onDismiss,
                    modifier = modifier,
                )
            } else {
                val subject = interpretation.goal.subject()
                SidrActionProposal(
                    title = sidrString(R.string.launcher_interpretation_title),
                    // Nothing has run before «Выполнить»: an empty record, so a reference reads its source step.
                    // A reading of two or more steps numbers its lines from one (task 7, ruling 4) — `index + 1`,
                    // the same number a later step's «← из шага N» names; one step keeps its line.
                    description = interpretation.steps
                        .map { step ->
                            val line = step.line(subject, dynamicLabels, observations = emptyMap())
                            if (interpretation.steps.size > 1) {
                                sidrString(R.string.launcher_interpretation_step_numbered, step.index + 1, line)
                            } else {
                                line
                            }
                        }
                        .joinToString(STEP_LINE_SEPARATOR),
                    tone = if (interpretation.steps.any { requiresConsent(it.risk) }) {
                        SidrActionProposalTone.Confirm
                    } else {
                        SidrActionProposalTone.Safe
                    },
                    provenance = {
                        interpretation.steps.forEach { step ->
                            ModelValues(interpretation.modelTextGoingOut(step))
                            AgentStepProvenance(provenance[step.invocation.id])
                        }
                    },
                    onExecute = onRun,
                    onCancel = onDismiss,
                    executeLabel = sidrString(R.string.launcher_interpretation_run),
                    modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                )
            }
        }
    }
}

/** «Разбираю команду…» with «Отмена» (spec §7.12.2, plan §0.2 B-10). */
@Composable
internal fun ThinkingArea(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SidrText(text = sidrString(R.string.launcher_thinking), role = SidrTextRole.PROVENANCE, modifier = Modifier.weight(1f))
        SidrText(
            text = sidrString(UiR.string.ui_action_cancel),
            role = SidrTextRole.HUMAN_BODY,
            color = SidrTheme.colors.accent,
            modifier = Modifier.heightIn(min = Sizes.minTouchTarget).clickable(onClick = onCancel).padding(Spacing.sm),
        )
    }
}

/**
 * Spec §7.12.1 / `DOC-MZN-2`: a value the MODEL produced, on a step whose effect leaves the phone (an
 * address, a search text), is shown as the step carries it — for a search text that is as the model
 * wrote it (trimmed); for an address it is the NORMALISED form (scheme lower-cased by the URL sort before the card,
 * A4′ phase 3b Task 6), i.e. the address that will open. A value the user said needs no mark — it is
 * theirs.
 *
 * [values] are the domain's ONE reading of «model text going out» (`modelTextGoingOut`, A4′ acceptance tail task 4) —
 * the same function `needsCard()` counts, so the card draws exactly what it is shown for; the model's name for an app
 * the device bound (controller ruling T2-R2) is not among them: the package goes out, not the word.
 */
@Composable
private fun ModelValues(values: List<String>) {
    values.forEach { value ->
        SidrText(text = sidrString(R.string.launcher_interpretation_model_value, value), role = SidrTextRole.PROVENANCE)
    }
}

/**
 * Condition 2: label AND package (two apps can share a label); a tap binds that one and launches nothing.
 * A word form offered as ONE app (phase 3b, fork 3b-G: [AppChoice.confirm]) — or, for `uninstall_app`, a name
 * exact only after folding (owner decision 2026-11-17) — asks «did you mean» with that
 * app's label; a confirmation over two or more apps keeps the «which app» title (E4-P7, E5-P6).
 *
 * **Two callers, one list** (phase 3b, B-7): the card's pick ([AppChoice], the model's mention) and step
 * 2b's target pick (`UnresolvedTarget`, the user's own words), drawn from `LauncherScreen`. The list knows
 * neither: it shows [mention] and [candidates] and reports the tapped package — what a tap BINDS is the
 * caller's, so neither caller's binding is spelled here.
 */
@Composable
internal fun AppChoiceList(
    mention: String,
    candidates: List<InstalledApp>,
    confirm: Boolean,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        val title = if (confirm && candidates.size == 1) {
            sidrString(R.string.launcher_interpretation_did_you_mean, candidates.single().label)
        } else {
            sidrString(R.string.launcher_interpretation_choose_app, mention)
        }
        SidrText(text = title, role = SidrTextRole.HUMAN_TITLE)
        candidates.forEach { app ->
            SidrText(
                text = sidrString(R.string.launcher_interpretation_candidate, app.label, app.packageName),
                role = SidrTextRole.HUMAN_BODY,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.minTouchTarget)
                    .clickable { onChoose(app.packageName) }
                    .padding(vertical = Spacing.sm),
            )
        }
        SidrText(
            text = sidrString(UiR.string.ui_action_cancel),
            role = SidrTextRole.HUMAN_BODY,
            color = SidrTheme.colors.accent,
            modifier = Modifier.heightIn(min = Sizes.minTouchTarget).clickable(onClick = onDismiss).padding(vertical = Spacing.sm),
        )
    }
}

/** One step per line on the card: layout between whole sentences, not copy joined into one. */
private const val STEP_LINE_SEPARATOR = "\n"

private fun destinationLine(destination: SidrDestination): Int = when (destination) {
    SidrDestination.LAUNCHER_SETTINGS -> R.string.launcher_interpretation_open_settings
    SidrDestination.APP_GRID -> R.string.launcher_interpretation_show_apps
}
