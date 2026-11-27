package com.sidr.launcher.domain.memory.plan

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.ai.bridge.BridgeRendering
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawArg
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.StepRef
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A4′ phase 4, Task 1 (plan §0.2 B-1, B-2, B-3): what a confirmed reading teaches — a command's form with
 * slots and the plan's template — and how a form takes a later command back. Pure: [PlainTextFolding], and
 * [ProposedStep]s built directly, as `ModelPlanning.Proposed` hands them over before binding.
 *
 * Fixtures (R14-43): the apps «Lumora» and «Vemblic» are the phase's fixtures, never real apps; every
 * assertion compares by equality.
 */
class LearnedFormsTest {

    private val folding = PlainTextFolding

    /** One argument's value and where it came from. */
    private data class Given(val value: String, val origin: ValueOrigin)

    private fun said(value: String) = Given(value, ValueOrigin.SaidBy(PrincipalId.USER))
    private fun model(value: String) = Given(value, ValueOrigin.ModelGenerated)

    private fun step(
        tool: ToolId,
        vararg args: Pair<String, Given>,
        refs: Map<String, StepRef> = emptyMap(),
        effect: ToolEffect = ToolEffect.LOCAL,
    ) =
        ProposedStep(
            tool = tool,
            args = args.associate { (name, given) -> name to given.value },
            origins = args.associate { (name, given) -> name to given.origin },
            risk = ActionRiskLevel.SAFE,
            effect = effect,
            refs = refs,
        )

    private fun learned(command: String, vararg steps: ProposedStep): LearnedPlanShape {
        val shape = LearnedForms.learn(command, steps.toList(), folding)
        assertNotNull("«$command» was expected to be learned", shape)
        return shape!!
    }

    @Test
    fun `a mention the user typed becomes a slot and the rest stays words`() {
        val shape = learned("сотри lumora", step(UNINSTALL_APP, "app" to said("lumora")))

        assertEquals("сотри …", shape.form.key)
        assertEquals(PlanTemplate(listOf(TemplateStep(UNINSTALL_APP, mapOf("app" to TemplateArg.Slot(0))))), shape.template)
    }

    @Test
    fun `a value the model wrote stays a constant`() {
        val shape = learned("будильник на рассвете", step(SET_ALARM, "time" to model("6:00")))

        assertEquals("будильник на рассвете", shape.form.key)
        assertEquals(PlanTemplate(listOf(TemplateStep(SET_ALARM, mapOf("time" to TemplateArg.Constant("6:00"))))), shape.template)
        assertEquals(
            RawAnswer(
                kind = BridgeRendering.KIND_PLAN,
                steps = listOf(RawStep(SET_ALARM.value, mapOf("time" to RawArg.Text("6:00")))),
                question = null,
                destination = null,
            ),
            LearnedForms.fill(shape.template, emptyList()),
        )
    }

    @Test
    fun `a reference stays a reference`() {
        val shape = learned(
            "сотри orvelle.txt",
            step(FIND_FILE, "name" to said("orvelle.txt")),
            step(DELETE_FILE, refs = mapOf("path" to StepRef(0, "path"))),
        )

        assertEquals("сотри …", shape.form.key)
        assertEquals(TemplateArg.Ref(0, "path"), shape.template.steps[1].args["path"])
        assertEquals(
            PlanTemplate(
                listOf(
                    TemplateStep(FIND_FILE, mapOf("name" to TemplateArg.Slot(0))),
                    TemplateStep(DELETE_FILE, mapOf("path" to TemplateArg.Ref(0, "path"))),
                ),
            ),
            shape.template,
        )
        assertEquals(
            RawAnswer(
                kind = BridgeRendering.KIND_PLAN,
                steps = listOf(
                    RawStep(FIND_FILE.value, mapOf("name" to RawArg.Text("orvelle.txt"))),
                    RawStep(DELETE_FILE.value, mapOf("path" to RawArg.Ref(0, "path"))),
                ),
                question = null,
                destination = null,
            ),
            LearnedForms.fill(shape.template, listOf("orvelle.txt")),
        )
    }

    @Test
    fun `the same words in two arguments are one slot`() {
        val shape = learned(
            "покажи lumora",
            step(LAUNCH_APP, "app" to said("lumora")),
            step(OPEN_APP_INFO, "app" to said("lumora")),
        )

        assertEquals("покажи …", shape.form.key)
        assertEquals(1, shape.form.slotCount)
        assertEquals(
            PlanTemplate(
                listOf(
                    TemplateStep(LAUNCH_APP, mapOf("app" to TemplateArg.Slot(0))),
                    TemplateStep(OPEN_APP_INFO, mapOf("app" to TemplateArg.Slot(0))),
                ),
            ),
            shape.template,
        )
    }

    @Test
    fun `words that occur twice are not learned`() {
        assertNull(LearnedForms.learn("lumora сотри lumora", listOf(step(UNINSTALL_APP, "app" to said("lumora"))), folding))
    }

    @Test
    fun `a value that cuts a space-separated word is not learned`() {
        assertNull(LearnedForms.learn("открой lumora-studio", listOf(step(LAUNCH_APP, "app" to said("lumora"))), folding))
    }

    @Test
    fun `a form with no word of its own is not learned`() {
        assertNull(LearnedForms.learn("lumora", listOf(step(LAUNCH_APP, "app" to said("lumora"))), folding))
    }

    @Test
    fun `two slots side by side are not learned`() {
        assertNull(
            LearnedForms.learn(
                "открой lumora vemblic",
                listOf(step(LAUNCH_APP, "app" to said("lumora")), step(LAUNCH_APP, "app" to said("vemblic"))),
                folding,
            ),
        )
    }

    /** Each cap is shown at its edge: 64 characters are learned, 65 are not. */
    @Test
    fun `a form or a constant past its cap is not learned`() {
        val formAtCap = "сотри lumora " + "z".repeat(56)
        val formPastCap = "сотри lumora " + "z".repeat(57)
        assertEquals(MAX_FORM_LENGTH, "сотри … ${"z".repeat(56)}".length)
        assertEquals(formAtCap.replace("lumora", "…"), learned(formAtCap, step(UNINSTALL_APP, "app" to said("lumora"))).form.key)
        assertNull(LearnedForms.learn(formPastCap, listOf(step(UNINSTALL_APP, "app" to said("lumora"))), folding))

        val constantAtCap = "а".repeat(MAX_CONSTANT_LENGTH)
        val constantPastCap = "а".repeat(MAX_CONSTANT_LENGTH + 1)
        assertEquals(
            TemplateArg.Constant(constantAtCap),
            learned("будильник на рассвете", step(SET_ALARM, "time" to model(constantAtCap))).template.steps[0].args["time"],
        )
        assertNull(LearnedForms.learn("будильник на рассвете", listOf(step(SET_ALARM, "time" to model(constantPastCap))), folding))
    }

    /** Review Focus 2: other case, trailing punctuation — the slot holds «Vemblic», not «Vemblic!». */
    @Test
    fun `a form takes another value in its slot, as the user typed it`() {
        val form = learned("сотри lumora", step(UNINSTALL_APP, "app" to said("lumora"))).form

        assertEquals(listOf("Vemblic"), LearnedForms.match(form, "Сотри Vemblic!", folding))
    }

    @Test
    fun `a form does not match a command with a different or a missing word`() {
        val form = CommandForm.parse("сотри …")

        assertNull(LearnedForms.match(form, "удали Vemblic", folding))
        assertNull(LearnedForms.match(form, "сотри", folding))
    }

    @Test
    fun `a form that fits a command two ways does not match`() {
        assertNull(LearnedForms.match(CommandForm.parse("найди … в …"), "найди книгу в магазине в центре", folding))
    }

    @Test
    fun `the key round-trips and marks a slot with an ellipsis`() {
        val form = learned("сотри lumora", step(UNINSTALL_APP, "app" to said("lumora"))).form

        assertEquals("сотри \u2026", form.key)
        assertEquals(form, CommandForm.parse("сотри …"))
        assertEquals(form.key, CommandForm.parse(form.key).key)
    }

    @Test
    fun `an address slot keeps the user's text whole`() {
        val shape = learned(
            "открой HTTPS://Quillmere.example/Path",
            step(OPEN_URL, "url" to said("https://quillmere.example/path"), effect = ToolEffect.EXTERNAL),
        )
        assertEquals("открой …", shape.form.key)

        val values = LearnedForms.match(shape.form, "открой HTTPS://Quillmere.example/Other", folding)
        assertEquals(listOf("HTTPS://Quillmere.example/Other"), values)

        assertEquals(
            RawAnswer(
                kind = BridgeRendering.KIND_PLAN,
                steps = listOf(RawStep(OPEN_URL.value, mapOf("url" to RawArg.Text("HTTPS://Quillmere.example/Other")))),
                question = null,
                destination = null,
            ),
            LearnedForms.fill(shape.template, values!!),
        )
    }

    /** Review M1, B-2's last clause: «найди … в …» fits its own command two ways, so it could never replay. */
    @Test
    fun `a form that cannot match its own command is not learned`() {
        assertNull(
            LearnedForms.learn(
                "найди книгу в магазине в центре",
                listOf(step(WEB_SEARCH, "query" to said("книгу в магазине"), "near" to said("центре"), effect = ToolEffect.EXTERNAL)),
                folding,
            ),
        )
    }

    /**
     * Fix round 1 (review, Important 1): slots are numbered in the order the command says them, not in the
     * order the arguments come — `near` is given first and said second, so it is slot 1. Numbered the other way,
     * a card-less replay would put each value into the other argument.
     */
    @Test
    fun `two slots are numbered in the order the command says them`() {
        val shape = learned("найди книгу в центре", step(WEB_SEARCH, "near" to said("центре"), "query" to said("книгу"), effect = ToolEffect.EXTERNAL))

        assertEquals("найди … в …", shape.form.key)
        assertEquals(
            PlanTemplate(listOf(TemplateStep(WEB_SEARCH, mapOf("near" to TemplateArg.Slot(1), "query" to TemplateArg.Slot(0))))),
            shape.template,
        )
        assertEquals(listOf("телефон", "Казани"), LearnedForms.match(shape.form, "найди телефон в Казани", folding))
    }

    /**
     * Fix round 1 (review, Important 2): `match`'s own whole-token rule, apart from `learn`'s boundary check —
     * a slot neither starts (`сотри-Vemblic`) nor ends (`книгу-в`) inside a space-separated word.
     */
    @Test
    fun `a slot never takes part of a space-separated word`() {
        assertNull(LearnedForms.match(CommandForm.parse("сотри …"), "сотри-Vemblic", folding))
        assertNull(LearnedForms.match(CommandForm.parse("найди … в …"), "найди книгу-в магазине", folding))
    }

    /**
     * Task 13 (decision 8): the model wrote «лумора» for the user's «лумору» — a form of the word, not the same
     * word — in a step that stays on the phone, and still the user's words are what the form takes next time.
     * The command has no verbatim copy of the value (that would be `SaidBy`), as `BridgeValidator` hands it over.
     */
    @Test
    fun `a model value in another form of the user's words is learned as the user's slot`() {
        val shape = learned("открой лумору", step(LAUNCH_APP, "app" to model("лумора")))

        assertEquals("открой …", shape.form.key)
        assertEquals(PlanTemplate(listOf(TemplateStep(LAUNCH_APP, mapOf("app" to TemplateArg.Slot(0))))), shape.template)
        val values = LearnedForms.match(shape.form, "открой вемблику", folding)
        assertEquals(listOf("вемблику"), values)
        assertEquals(
            RawAnswer(
                kind = BridgeRendering.KIND_PLAN,
                steps = listOf(RawStep(LAUNCH_APP.value, mapOf("app" to RawArg.Text("вемблику")))),
                question = null,
                destination = null,
            ),
            LearnedForms.fill(shape.template, values!!),
        )
    }

    /**
     * Owner 2026-11-27 («не для шагов наружу»): a step that leaves the phone keeps the model's text as a constant,
     * as before task 13 — a slot would let three confirmed searches answer any «поищи …» without the model (I5).
     */
    @Test
    fun `a model value for a step that leaves the phone stays a constant`() {
        val shape = learned(
            "поищи погоду в казани",
            step(WEB_SEARCH, "query" to model("погода в казани"), effect = ToolEffect.EXTERNAL),
        )

        assertEquals("поищи погоду в казани", shape.form.key)
        assertEquals(
            PlanTemplate(listOf(TemplateStep(WEB_SEARCH, mapOf("query" to TemplateArg.Constant("погода в казани"))))),
            shape.template,
        )
    }

    /**
     * Review Focus 4, honestly: «лумору» and «луморы» are both forms (edges) of «лумора»; there is no verbatim copy
     * in the command (that would be `SaidBy`, not reachable here). Two places, so which one the user meant is a
     * guess — the whole reading is not learned.
     */
    @Test
    fun `two places of the same form - not learned`() {
        assertNull(LearnedForms.learn("открой лумору и луморы", listOf(step(LAUNCH_APP, "app" to model("лумора"))), folding))
    }

    @Test
    fun `a model value with no word of the user stays a constant`() {
        val shape = learned("поищи погоду", step(WEB_SEARCH, "query" to model("солнечно"), effect = ToolEffect.EXTERNAL))

        assertEquals("поищи погоду", shape.form.key)
        assertEquals(PlanTemplate(listOf(TemplateStep(WEB_SEARCH, mapOf("query" to TemplateArg.Constant("солнечно"))))), shape.template)
    }

    /**
     * The plan's rule as written: one place with every pair exact is a constant. Through `BridgeValidator` this
     * input does not arrive (a verbatim copy is `SaidBy`, never `ModelGenerated`); the test holds the rule for a
     * `learn` call that bypasses the validator.
     */
    @Test
    fun `a model value standing exactly once in the command stays a constant`() {
        val shape = learned("открой лумора", step(LAUNCH_APP, "app" to model("лумора")))

        assertEquals("открой лумора", shape.form.key)
        assertEquals(PlanTemplate(listOf(TemplateStep(LAUNCH_APP, mapOf("app" to TemplateArg.Constant("лумора"))))), shape.template)
    }

    private companion object {
        val UNINSTALL_APP = ToolId("uninstall_app")
        val SET_ALARM = ToolId("set_alarm")
        val LAUNCH_APP = ToolId("launch_app")
        val OPEN_APP_INFO = ToolId("open_app_info")
        val OPEN_URL = ToolId("open_url")
        val WEB_SEARCH = ToolId("web_search")
        val FIND_FILE = ToolId("find_file")
        val DELETE_FILE = ToolId("delete_file")
    }
}
