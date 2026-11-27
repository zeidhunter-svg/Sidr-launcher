package com.sidr.launcher.domain.memory.plan

import com.sidr.launcher.domain.ai.bridge.BridgeRendering
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawArg
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.text.AppNameMatcher
import com.sidr.launcher.domain.text.TextFolding

/**
 * What a confirmed reading teaches, and how a later command is read back against it (A4′ phase 4, plan §0.2
 * B-1, B-2, B-3; spec §8.1–8.2). Pure: the device's [TextFolding] is passed in, nothing is stored here.
 *
 * **A command is read as its space-separated tokens** (B-1) — `\S+` over the trimmed command, each with its
 * range — and a token's words are `folding.words(token)`; the command's words `W` are those, in order. A
 * slot always covers whole tokens, so «lumora» is never a slot of «lumora-studio».
 *
 * **A form never holds a package** (spec §8.2): a value the user said becomes a slot, re-bound on every
 * replay; a value the model wrote stays the model's text — unless it is the user's own words in another form
 * (A4′ tail, task 13: the model wrote «погода» for the user's «погоду»), which becomes a slot of the user's
 * words too; a reference stays a reference.
 */
object LearnedForms {

    /**
     * The form and template a confirmed reading teaches, or `null` (B-2). [steps] are the validated steps
     * (`ModelPlanning.Proposed.steps`) before binding; their argument names are the model-facing ones.
     *
     *  - an argument [ValueOrigin.SaidBy] the user: its words must occur in `W` **exactly once**, starting at
     *    a token's first word and ending at a token's last word; two arguments over the same words share one
     *    slot, two over overlapping different words are not learned;
     *  - an argument the model wrote ([ValueOrigin.ModelGenerated] or any origin but `SaidBy`): its words are
     *    looked for in `W` as consecutive words, each pair [AppNameMatcher.wordsMatch] exact or edge (a form of
     *    the word), starting at a token's first word and ending at a token's last word. No such place:
     *    [TemplateArg.Constant] of its value, at most [MAX_CONSTANT_LENGTH] characters. Exactly one place, and
     *    at least one pair an edge: **a slot of the user's words**, like a `SaidBy` one (same numbering, same
     *    overlap rule, same self-check below) — the model's text is not kept, so on a replay the slot holds what
     *    the user typed and the validator itself recognises those words as said by the user; [Derivability] is
     *    untouched. Exactly one place with every pair exact: a constant, as before. Two places or more, exact or
     *    edge («поищи погоду и погода»): not learned — the slot would be a guess;
     *  - a reference: [TemplateArg.Ref].
     *
     * Slots are numbered in the order their words occur in the command. Not learned either: a form with no
     * word of its own, two slots side by side, a key past [MAX_FORM_LENGTH] — or a form that does not give
     * back, through [match], exactly the values just learned (review M1: a form that pins but never replays).
     */
    fun learn(command: String, steps: List<ProposedStep>, folding: TextFolding): LearnedPlanShape? {
        val reading = CommandReading.of(command, folding)
        val said = HashMap<Pair<Int, String>, IntRange>()
        for ((index, step) in steps.withIndex()) {
            for ((name, value) in step.args) {
                if (step.origins[name] is ValueOrigin.SaidBy) {
                    val found = reading.occurrencesOf(folding.words(value))
                    if (found.size != 1) return null
                    val span = found.single()
                    if (!reading.startsToken(span.first) || !reading.endsToken(span.last)) return null
                    said[index to name] = span
                } else {
                    val places = reading.occurrencesOfForms(folding.words(value))
                    if (places.size > 1) return null
                    val place = places.singleOrNull()
                    if (place != null && place.edge) {
                        said[index to name] = place.span
                    } else if (value.length > MAX_CONSTANT_LENGTH) {
                        return null
                    }
                }
            }
        }
        val spans = said.values.distinct().sortedBy { it.first }
        if (spans.zipWithNext().any { (earlier, later) -> later.first <= earlier.last }) return null
        val form = CommandForm(partsOf(reading, spans))
        if (form.parts.none { it is FormPart.Word }) return null
        if (form.parts.zipWithNext().any { (a, b) -> a is FormPart.Slot && b is FormPart.Slot }) return null
        if (form.key.length > MAX_FORM_LENGTH) return null
        if (match(form, command, folding) != spans.map(reading::valueOf)) return null
        val template = PlanTemplate(steps.mapIndexed { index, step -> TemplateStep(step.tool, argsOf(index, step, said, spans)) })
        return LearnedPlanShape(form, template)
    }

    /**
     * The values [form]'s slots take in [command], or `null` (B-3). Every assignment of the command's words
     * to the form's parts is tried — a word equals the next word, a slot takes one or more whole tokens — and
     * only **exactly one** complete assignment answers; none, or more than one, is `null`. A slot's value is
     * the trimmed command from its first token's start to its last token's end, as typed — case, marks and
     * inner punctuation kept, so an address stays an address — with [SLOT_EDGE_PUNCTUATION] stripped from both
     * ends; an empty value is `null`.
     */
    fun match(form: CommandForm, command: String, folding: TextFolding): List<String>? {
        val reading = CommandReading.of(command, folding)
        val found = assignmentsOf(form, reading, enough = 2)
        if (found.size != 1) return null
        val values = found.single().map(reading::valueOf)
        return if (values.any { it.isEmpty() }) null else values
    }

    /**
     * The template as an answer of the model would carry it (B-4): a [RawAnswer] of kind
     * [BridgeRendering.KIND_PLAN] that `BridgeValidator` judges against today's offering and this command,
     * exactly as at step 7. A slot takes `slots[index]`; a constant its text; a reference stays a reference.
     *
     * A template whose slots [slots] does not cover fills no steps at all — the validator rejects that as a
     * shape, a miss — rather than throwing, or answering a plan short of an argument the user confirmed.
     */
    fun fill(template: PlanTemplate, slots: List<String>): RawAnswer {
        val covered = template.steps.all { step ->
            step.args.values.all { arg -> arg !is TemplateArg.Slot || arg.index in slots.indices }
        }
        return RawAnswer(
            kind = BridgeRendering.KIND_PLAN,
            steps = if (!covered) null else template.steps.map { step ->
                RawStep(step.tool.value, step.args.mapValues { (_, arg) -> rawOf(arg, slots) })
            },
            question = null,
            destination = null,
        )
    }

    /** `W` with each span replaced by one [FormPart.Slot]; [spans] are sorted and do not overlap. */
    private fun partsOf(reading: CommandReading, spans: List<IntRange>): List<FormPart> {
        val parts = mutableListOf<FormPart>()
        var word = 0
        var next = 0
        while (word < reading.words.size) {
            if (next < spans.size && spans[next].first == word) {
                parts += FormPart.Slot
                word = spans[next].last + 1
                next++
            } else {
                parts += FormPart.Word(reading.words[word])
                word++
            }
        }
        return parts
    }

    private fun argsOf(
        index: Int,
        step: ProposedStep,
        said: Map<Pair<Int, String>, IntRange>,
        spans: List<IntRange>,
    ): Map<String, TemplateArg> {
        val args = LinkedHashMap<String, TemplateArg>()
        for ((name, value) in step.args) {
            args[name] = said[index to name]?.let { TemplateArg.Slot(spans.indexOf(it)) } ?: TemplateArg.Constant(value)
        }
        for ((name, ref) in step.refs) args[name] = TemplateArg.Ref(ref.step, ref.key)
        return args
    }

    /** Complete assignments of `W` to [form]'s parts — each one the word spans of its slots — at most [enough]. */
    private fun assignmentsOf(form: CommandForm, reading: CommandReading, enough: Int): List<List<IntRange>> {
        val found = mutableListOf<List<IntRange>>()
        val slots = ArrayList<IntRange>()
        fun walk(part: Int, word: Int) {
            if (found.size >= enough) return
            if (part == form.parts.size) {
                if (word == reading.words.size) found += slots.toList()
                return
            }
            when (val wanted = form.parts[part]) {
                is FormPart.Word -> if (word < reading.words.size && reading.words[word] == wanted.word) walk(part + 1, word + 1)
                FormPart.Slot -> {
                    if (word >= reading.words.size || !reading.startsToken(word)) return
                    for (last in word until reading.words.size) {
                        if (!reading.endsToken(last)) continue
                        slots += word..last
                        walk(part + 1, last + 1)
                        slots.removeAt(slots.lastIndex)
                    }
                }
            }
        }
        walk(part = 0, word = 0)
        return found
    }

    private fun rawOf(arg: TemplateArg, slots: List<String>): RawArg = when (arg) {
        is TemplateArg.Slot -> RawArg.Text(slots[arg.index])
        is TemplateArg.Constant -> RawArg.Text(arg.text)
        is TemplateArg.Ref -> RawArg.Ref(arg.step, arg.key)
    }
}

/** A place a model's value stands in `W` by word forms; [edge] — at least one pair was a form, not the same word. */
private class FormPlace(val span: IntRange, val edge: Boolean)

/**
 * A command read as B-1 reads it: the trimmed [text], its words `W`, and for each word the token it came
 * from. A token with no word (a lone «—» or «!») holds no word; it is inside a slot's value only when it
 * stands between the slot's first and last token.
 */
private class CommandReading(
    val text: String,
    val words: List<String>,
    private val tokenOfWord: List<Int>,
    private val tokens: List<IntRange>,
) {
    fun startsToken(word: Int): Boolean = word == 0 || tokenOfWord[word - 1] != tokenOfWord[word]

    fun endsToken(word: Int): Boolean = word == words.lastIndex || tokenOfWord[word + 1] != tokenOfWord[word]

    /** Every place [wanted] stands in `W`, whole and in order; none for no words. */
    fun occurrencesOf(wanted: List<String>): List<IntRange> {
        if (wanted.isEmpty()) return emptyList()
        return (0..words.size - wanted.size)
            .filter { start -> words.subList(start, start + wanted.size) == wanted }
            .map { start -> start until start + wanted.size }
    }

    /**
     * Every place [wanted] stands in `W` by word forms: consecutive words, each pair
     * [AppNameMatcher.wordsMatch] (typed = the command's word, label = the wanted one) exact or edge, the place
     * starting at a token's first word and ending at a token's last. Each place says whether any pair was an
     * edge. None for no words.
     */
    fun occurrencesOfForms(wanted: List<String>): List<FormPlace> {
        if (wanted.isEmpty()) return emptyList()
        val places = mutableListOf<FormPlace>()
        for (start in 0..words.size - wanted.size) {
            val last = start + wanted.size - 1
            if (!startsToken(start) || !endsToken(last)) continue
            var edge = false
            var whole = true
            for (offset in wanted.indices) {
                val pair = AppNameMatcher.wordsMatch(words[start + offset], wanted[offset])
                if (pair == null) {
                    whole = false
                    break
                }
                if (!pair) edge = true
            }
            if (whole) places += FormPlace(start..last, edge)
        }
        return places
    }

    /** The text of the tokens [span]'s words come from, as typed, edge punctuation stripped. */
    fun valueOf(span: IntRange): String =
        text.substring(tokens[tokenOfWord[span.first]].first, tokens[tokenOfWord[span.last]].last + 1)
            .trim { it in SLOT_EDGE_PUNCTUATION }

    companion object {
        private val TOKEN = Regex("\\S+")

        fun of(command: String, folding: TextFolding): CommandReading {
            val text = command.trim()
            val words = mutableListOf<String>()
            val tokenOfWord = mutableListOf<Int>()
            val tokens = mutableListOf<IntRange>()
            for (token in TOKEN.findAll(text)) {
                for (word in folding.words(token.value)) {
                    words += word
                    tokenOfWord += tokens.size
                }
                tokens += token.range
            }
            return CommandReading(text, words, tokenOfWord, tokens)
        }
    }
}
