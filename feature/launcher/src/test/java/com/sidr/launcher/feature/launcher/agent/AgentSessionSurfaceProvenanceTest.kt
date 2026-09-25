package com.sidr.launcher.feature.launcher.agent

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.PlanningRequest
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 10 / A1'. **This is what closes `DOC-ILM-2`, and the unit tests beside it are not.**
 *
 * Spec §6.3 names the trap in advance: the obvious guard — "an `EXTERNAL` tool carries provenance" —
 * cannot fail once `level`/`effect` are required non-null fields, because the compiler already enforces
 * it; and a unit test on `provenanceLabelFor` stays green whether or not a single call site exists. The
 * non-vacuous property is that provenance **reaches the user**, so these tests drive the real
 * [AgentSessionSurface] through real string resources and assert the rendered line. Removing either
 * call site turns exactly one of them red (mutation-verified, Task 10 report).
 *
 * Shape follows [com.sidr.launcher.feature.launcher.PrayerProvenanceTextTest] — Robolectric plus
 * `createComposeRule`, real resources, an exact expected sentence.
 *
 * **Where the line is and is not visible.** [AgentSessionSurface] draws the plan list in `Running`,
 * `Paused` and `Completed`, and the consent card in `AwaitingConsent`; those four states are covered
 * below. `Planning` has no plan yet, and `Failed` / `Blocked` / `Cancelled` draw no steps at all —
 * pre-existing surface design this task does not change. In particular the plan list is still not
 * drawn under `AwaitingConsent` (a named A0 limitation in `CLAUDE.md`), which is why the consent card
 * carries its own provenance line for the one step it is asking about rather than relying on the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionSurfaceProvenanceTest {

    @get:Rule val compose = createComposeRule()

    /**
     * `Tier0ToolIds.SET_TIMER` lives in `:data:repository`, which `:feature:launcher` must not depend
     * on (no feature -> data edge), so the id is written out here exactly as the production mapping
     * writes it.
     */
    private val setTimer = ToolId("set_timer")

    private val external = mapOf(setTimer to StepProvenance(ToolLevels.SYSTEM_INTENT, ToolEffect.EXTERNAL))
    private val local = mapOf(setTimer to StepProvenance(ToolLevels.SANDBOX, ToolEffect.LOCAL))

    /** Task 9's shape: a typed command matched a registered tool, so the plan is one `GOAL_DIRECT` step. */
    private fun timerSession(
        state: ExecutionState,
        trace: List<TraceEvent> = emptyList(),
    ): AgentSession {
        val goal = AgentGoal("set a timer for 10 minutes", GoalShape.Free("set a timer for 10 minutes"))
        return AgentSession(
            id = AgentSessionId("s1"),
            goal = goal,
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(
                            setTimer,
                            mapOf("duration" to ArgSource.Literal("10 minutes")),
                        ),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
            cursor = 0,
            state = state,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(trace),
        )
    }

    /**
     * The registry the A0 plan is planned against, held as a field so the plan and the provenance map
     * below are derived from the **same** descriptors — see `both A0 steps disclose…`.
     */
    private val a0Registry = FakeToolRegistry.withA0Tools()

    private fun a0Session(): AgentSession {
        val goal = AgentGoal("открой убер", GoalShape.AppNotInstalled("убер"))
        val planned = runBlocking { TemplatePlanner().plan(PlanningRequest(goal), a0Registry) }
        check(planned is PlanningResult.Planned)
        return AgentSession(
            id = AgentSessionId("s1"),
            goal = goal,
            plan = planned.plan,
            cursor = 0,
            state = ExecutionState.Paused,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(emptyList()),
        )
    }

    private fun render(session: AgentSession, provenance: Map<ToolId, StepProvenance>) {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AgentSessionSurface(
                    session = session,
                    toolProvenance = provenance,
                    // A1″ Task 8. Empty on purpose: every tool in this file is an AUTHORED one, and an
                    // empty map is what makes this file's byte-identity baseline still mean what it
                    // did — the dynamic arm of `line` is unreachable for a tool that has no data name,
                    // so A0's and A1′'s rendered lines are unchanged. The populated case is held one
                    // layer out, by `LauncherScreenAgentProvenanceTest`, which is where the map is
                    // actually supplied from.
                    dynamicLabels = emptyMap(),
                    confirming = false,
                    onConfirm = {},
                    onDeny = {},
                    onContinue = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
    }

    /**
     * **The byte-identity baseline.** Task 10 moves the step line off `StepRationale` and onto the
     * step's tool id; these two sentences are what the surface the owner accepted on 2026-08-22
     * renders, and they must survive that move unchanged — `launch_app` must land on the same resource
     * `GOAL_DIRECT` did, and `play_store_search` on the same one `APP_NOT_INSTALLED_FALLBACK` did.
     *
     * Written and run BEFORE the change for exactly that reason: a claim of byte-identity is worth only
     * the run that produced it on the old code.
     *
     * The provenance map is empty here **on purpose** — that isolates the claim to the step line, which
     * is the half that must not move. What production actually passes, and what it adds to this same
     * plan, is the test immediately below.
     *
     * **A4′ phase 0 changed the STATE WORD of the paused step, deliberately, and the SUBJECT of neither
     * sentence** (Task 6, D2): [a0Session] is `Paused` at `cursor = 0`, and a step the engine stopped on
     * is not in progress, so «in progress» became «waiting for you». The baseline now pins the unchanged
     * subject plus the corrected word — exact text, not a substring match on the subject, because the
     * word is exactly what D2 is about.
     */
    @Test
    fun `the A0 two-step plan renders the two sentences it always has`() {
        render(a0Session(), emptyMap())

        compose.onNodeWithText("Open убер — waiting for you").assertExists()
        compose.onNodeWithText("Find убер in the app store — not started").assertExists()
    }

    /**
     * What Task 10 **does** change on the surface the owner accepted on 2026-08-22: both A0 tools are
     * `IN_APP` + `EXTERNAL` (`SystemIntentToolSource`), so the A0 plan list now carries a disclosure
     * line under each of its two rows. The sentences above are unchanged; this is added, not altered,
     * and it is named here rather than left for someone to discover on the phone.
     *
     * **The map is derived, not written down** (Task 10 review, finding 2). It used to build
     * `StepProvenance(IN_APP, EXTERNAL)` by hand while [a0Registry] already declared exactly those two
     * fields, so a maintainer editing the registry's `level`/`effect` had no reason to notice this test
     * still asserted a disclosure it had supplied itself, disconnected from the fixture sitting right
     * next to it. The projection below is the one `LauncherViewModel.agentToolProvenance` performs,
     * over the same registry the plan came from, so the two cannot drift apart in that specific way.
     *
     * **What mutation actually shows** (verified, not asserted): both `provenance` and `expected` below
     * read [a0Registry], so a registry edit moves them together — changing one tool's `effect` to
     * `LOCAL` lowers `expected` by one and the render honestly follows it down; the assertion stays
     * green because it is now telling the truth about a smaller registry, not because it stopped
     * checking anything. What DOES turn this red is the vacuous collapse: every A0 tool going `LOCAL`
     * drives `expected` to zero, and the `assertTrue` below refuses to let `assertCountEquals(0)` pass
     * silently as if the test had verified something.
     */
    @Test
    fun `both A0 steps disclose that the effect leaves the launcher`() {
        val a0 = a0Session()
        val provenance = a0Registry.all().associate { it.id to StepProvenance(it.level, it.effect) }
        val expected = a0.plan.steps.count { step ->
            a0Registry.find(step.invocation.id)?.effect == ToolEffect.EXTERNAL
        }
        assertTrue(
            "the A0 registry must declare at least one EXTERNAL tool, or this test asserts nothing",
            expected > 0,
        )

        render(a0, provenance)

        compose.onAllNodesWithContentDescription("source SIDR · EXTERNAL").assertCountEquals(expected)
    }

    /**
     * The `SAFE` + `EXTERNAL` case, which is the one `DOC-ILM-2` is actually about: both tier-0 tools
     * are `SAFE`, so they never reach the consent gate and this line is the ONLY thing telling the user
     * the effect left the launcher.
     */
    @Test
    fun `an EXTERNAL step discloses where its effect went, on the plan list`() {
        render(timerSession(ExecutionState.Running), external)

        // The step names its tool, not a launch.
        compose.onNodeWithText("Set a timer for 10 minutes — in progress").assertExists()
        // SidrProvenanceLine clears its text semantics and speaks a composed sentence instead, so the
        // rendered line is asserted through the description TalkBack actually reads.
        compose.onNodeWithContentDescription("source SYSTEM INTENT · EXTERNAL").assertExists()
    }

    /**
     * Non-vacuity: the line is a function of the tool's declared effect, not something the surface
     * prints over every step. A `LOCAL` tool discloses nothing, because there is nothing to disclose.
     */
    @Test
    fun `a LOCAL step discloses nothing`() {
        render(timerSession(ExecutionState.Running), local)

        compose.onNodeWithText("Set a timer for 10 minutes — in progress").assertExists()
        compose.onNodeWithContentDescription("EXTERNAL", substring = true).assertDoesNotExist()
    }

    /**
     * The consent half of §6.3's "consent / execution surface". The plan list is not drawn in
     * `AwaitingConsent` (named A0 limitation), so without its own line the card would ask the user to
     * approve a step while disclosing nothing about where its effect goes.
     */
    @Test
    fun `the consent card discloses where the step it is gating will send the effect`() {
        render(
            timerSession(
                ExecutionState.AwaitingConsent,
                trace = listOf(TraceEvent.ConsentRequested(0, ConsentReason.RISK_LEVEL)),
            ),
            external,
        )

        compose.onNodeWithContentDescription("source SYSTEM INTENT · EXTERNAL").assertExists()
    }

    // ── Task 11 / A1″ Phase 3a: two-argument step lines, read by argument name ──
    //
    // `set_app_alias` and `uninstall_app` are `MemoryToolIds`/`Tier0ToolIds` in `:data:repository`,
    // which `:feature:launcher` must not depend on, so the ids are written out as literals here exactly
    // as the production mapping in `AgentSessionPresentation.kt` writes them.
    private val setAppAlias = ToolId("set_app_alias")
    private val uninstallApp = ToolId("uninstall_app")

    /**
     * Task 6b's shape: `ToolMatchPlanner` binds all three of `app` / `app_label` / `phrase` as literals.
     *
     * **The goal text deliberately contains neither "telegram" nor "телега".** A goal like "назови
     * telegram телега" would let this test pass on the OLD, unfixed code too — `literalSubject()` falls
     * back to the goal text on three literals, and the goal text would already contain both substrings
     * for the wrong reason (mutation check, see the task report).
     */
    private fun stateWithAliasStep(state: ExecutionState = ExecutionState.Running): AgentSession {
        val goal = AgentGoal("сохрани псевдоним для мессенджера", GoalShape.Free("сохрани псевдоним для мессенджера"))
        return AgentSession(
            id = AgentSessionId("s1"),
            goal = goal,
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(
                            setAppAlias,
                            mapOf(
                                "app" to ArgSource.Literal("com.example.msg"),
                                "app_label" to ArgSource.Literal("telegram"),
                                "phrase" to ArgSource.Literal("телега"),
                            ),
                        ),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
            cursor = 0,
            state = state,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(emptyList()),
        )
    }

    /**
     * Owner condition 2's shape: `app` (the resolved package) and `app_label` (what the user said).
     *
     * The goal text names neither the label nor the package, for the same non-vacuity reason
     * [stateWithAliasStep] does: it must be the two-argument branch putting both substrings on screen,
     * not a goal text that happened to contain one of them already.
     *
     * **The package deliberately does NOT contain "telegram" as a substring** (mutation check, task
     * report). The first fixture used `org.telegram.messenger`, and a mutation that dropped `app_label`
     * and substituted the package for it *still* passed — `"org.telegram.messenger".contains("telegram")`
     * is true regardless of which argument actually reached the string, so the assertion was checking
     * nothing about the label at all. `com.example.msg` shares no substring with either fixture value,
     * so the two `assertExists` calls below can only both pass if BOTH literals independently reached
     * the rendered text.
     */
    private fun awaitingConsentForUninstall(): AgentSession {
        val goal = AgentGoal("снеси это", GoalShape.Free("снеси это"))
        return AgentSession(
            id = AgentSessionId("s1"),
            goal = goal,
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(
                            uninstallApp,
                            mapOf(
                                "app" to ArgSource.Literal("com.example.msg"),
                                "app_label" to ArgSource.Literal("telegram"),
                            ),
                        ),
                        risk = ActionRiskLevel.CONFIRM,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
            cursor = 0,
            state = ExecutionState.AwaitingConsent,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(listOf(TraceEvent.ConsentRequested(0, ConsentReason.RISK_LEVEL))),
        )
    }

    @Test
    fun `a two-argument alias step names both arguments, not the goal text`() {
        // set_app_alias binds app_label and phrase; the generic single-literal path would fall through
        // to the goal text ("назови telegram телега") instead, because three literals is not one.
        render(stateWithAliasStep(), emptyMap())

        compose.onNodeWithText("telegram", substring = true).assertExists()
        compose.onNodeWithText("телега", substring = true).assertExists()
    }

    @Test
    fun `an uninstall step on the consent gate names BOTH the label and the package`() {
        // Owner condition 2. Both substrings, because "contains(\"telegram\")" alone would also pass on
        // a card that shows only what the user typed - which is what a positional or single-literal
        // read would still produce (review finding C4).
        render(awaitingConsentForUninstall(), emptyMap())

        compose.onNodeWithText("telegram", substring = true).assertExists()
        compose.onNodeWithText("com.example.msg", substring = true).assertExists()
    }
}
