package com.sidr.launcher.domain.memory.plan

import com.sidr.launcher.domain.memory.resolution.DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD
import com.sidr.launcher.domain.tool.ToolId

/** The longest form key that is learned (B-2). Chosen, not measured: the learned-choice store's `MAX_QUERY_LENGTH`. */
const val MAX_FORM_LENGTH: Int = 64

/** The longest constant a template keeps (B-2). Chosen, not measured, as [MAX_FORM_LENGTH]. */
const val MAX_CONSTANT_LENGTH: Int = 64

/** The most learned plans the store keeps (B-9). Chosen, not measured. */
const val MAX_LEARNED_PLANS: Int = 200

/** How a key writes a slot (B-1): U+2026, never a folded word, which holds only letters, marks and digits. */
const val SLOT_MARK: String = "…"

/** What `LearnedForms.match` strips from both ends of a slot's value (B-3). Inner punctuation stays. */
const val SLOT_EDGE_PUNCTUATION: String = ".,;:!?…\"'«»“”„()[]"

/** One part of a [CommandForm]: a folded word the command must say, or a slot that takes whole tokens. */
sealed interface FormPart {
    data class Word(val word: String) : FormPart
    data object Slot : FormPart
}

/**
 * A command's form (B-1): its folded words, each value the user said replaced by one [FormPart.Slot]. Many
 * wordings may meet one template (spec §8.1); the form is the wording side of that, never a package.
 *
 * [key] is the parts joined by one space, a slot written [SLOT_MARK] — canonical, and what the screen shows:
 * «сотри …». [parse] reads a key back; it folds nothing, so it expects a key [key] wrote.
 */
data class CommandForm(val parts: List<FormPart>) {
    val key: String = parts.joinToString(" ") { part ->
        when (part) {
            is FormPart.Word -> part.word
            FormPart.Slot -> SLOT_MARK
        }
    }
    val slotCount: Int = parts.count { it is FormPart.Slot }

    companion object {
        fun parse(key: String): CommandForm =
            CommandForm(key.split(' ').filter { it.isNotEmpty() }.map { if (it == SLOT_MARK) FormPart.Slot else FormPart.Word(it) })
    }
}

/**
 * One argument of a learned step (B-2): the value of slot [Slot.index] of the form, the model's own [Constant]
 * text, or a [Ref] to output [Ref.key] of the plan's earlier step [Ref.step]. Never a resolved package.
 */
sealed interface TemplateArg {
    data class Slot(val index: Int) : TemplateArg
    data class Constant(val text: String) : TemplateArg
    data class Ref(val step: Int, val key: String) : TemplateArg
}

/** One step of a learned plan: the tool and its model-facing arguments. Risk and effect are today's registry's (B-4). */
data class TemplateStep(val tool: ToolId, val args: Map<String, TemplateArg>)

data class PlanTemplate(val steps: List<TemplateStep>)

/** What one confirmed reading teaches: the form it was said in and the plan it meant. */
data class LearnedPlanShape(val form: CommandForm, val template: PlanTemplate)

/**
 * A learned shape with its evidence, as the learned-choice store keeps a choice (B-8): [streak] consecutive
 * confirmations of this template for this form. [pinned] — replayed without the model — at the same threshold
 * as a learned choice, [DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD] (spec §8.4).
 */
data class LearnedPlan(
    val shape: LearnedPlanShape,
    val streak: Int,
    val confirmations: Int,
    val confirmedAtEpochMs: Long,
) {
    val pinned: Boolean get() = streak >= DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD
}
