package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.RejectionReason
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The A0 engine. `advance` performs exactly one transition, which is what lets cancellation be checked
 * *between* transitions and lets every test drive the machine without coroutine timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentExecutorTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val budget = RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2)

    /**
     * What `launch_app` reports when the app is missing. The output is not decoration: step 1 binds
     * its `query` to `resolved_query`, so a script without it cannot reach the store at all (F6).
     */
    private fun notInstalled(query: String = "убер") = ToolResult.Observed(
        ObservedFact.APP_NOT_INSTALLED,
        ToolOutput(mapOf("resolved_query" to query)),
    )

    private fun planForMissingApp(query: String) = ExecutionPlan(
        listOf(
            PlanStep(
                index = 0,
                invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal(query))),
                risk = ActionRiskLevel.SAFE,
                precondition = StepPrecondition.None,
                rationale = StepRationale.GOAL_DIRECT,
            ),
            PlanStep(
                index = 1,
                invocation = ToolInvocation(
                    ToolIds.PLAY_STORE_SEARCH,
                    mapOf("query" to ArgSource.FromStep(0, "resolved_query")),
                ),
                risk = ActionRiskLevel.CONFIRM,
                precondition = StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
                rationale = StepRationale.APP_NOT_INSTALLED_FALLBACK,
            ),
        ),
    )

    private fun session(query: String = "убер") = AgentSession(
        id = AgentSessionId("s1"),
        goal = AgentGoal(text = "открой $query", shape = GoalShape.AppNotInstalled(query)),
        plan = planForMissingApp(query),
        cursor = 0,
        state = ExecutionState.Running,
        observations = emptyMap(),
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    @Test
    fun `a SAFE first step runs without consent and records the observation`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        val after = AgentExecutor(registry, tools, budget).advance(session())

        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
        assertEquals(1, after.cursor)
        assertEquals(ExecutionState.Running, after.state)
        assertEquals(notInstalled(), after.observations[0])
    }

    @Test
    fun `the risk transition stops the loop before the second tool is ever called`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        val executor = AgentExecutor(registry, tools, budget)

        val afterFirst = executor.advance(session())
        val afterSecond = executor.advance(afterFirst)

        assertEquals(ExecutionState.AwaitingConsent, afterSecond.state)
        assertEquals(1, tools.invocations.size)          // the store was NOT called
        assertTrue(
            afterSecond.trace.events.any {
                it is TraceEvent.ConsentRequested && it.reason == ConsentReason.RISK_LEVEL
            },
        )
    }

    @Test
    fun `granted consent runs the second step and completes`() = runTest {
        val tools = FakeToolExecutor(
            listOf(notInstalled(), ToolResult.Effected()),
        )
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s)
        s = executor.advance(s.copy(state = ExecutionState.Running, consents = mapOf(1 to true)))
        s = executor.advance(s)

        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.PLAY_STORE_SEARCH), tools.invocations.map { it.id })
        assertEquals(ExecutionState.Completed, s.state)
    }

    @Test
    fun `denied consent cancels and never invokes the tool`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s.copy(consents = mapOf(1 to false)))

        assertEquals(ExecutionState.Cancelled, s.state)
        assertEquals(1, tools.invocations.size)
    }

    @Test
    fun `an installed app skips the fallback step and still completes`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())      // step 0 launches the app
        s = executor.advance(s)                  // step 1's precondition is unsatisfied -> skipped
        s = executor.advance(s)                  // end of plan -> Completed

        assertEquals(1, tools.invocations.size)
        assertEquals(ExecutionState.Completed, s.state)
        assertTrue(s.trace.events.any { it is TraceEvent.StepSkipped && it.index == 1 })
    }

    @Test
    fun `an invocation the validator rejects fails the session and is traced`() = runTest {
        val tools = FakeToolExecutor(emptyList())
        val broken = session().let {
            it.copy(
                plan = ExecutionPlan(
                    listOf(
                        it.plan.steps[0].copy(
                            invocation = ToolInvocation(ToolId("not_registered"), mapOf("query" to ArgSource.Literal("x"))),
                        ),
                    ),
                ),
            )
        }

        val after = AgentExecutor(registry, tools, budget).advance(broken)

        assertEquals(ExecutionState.Failed, after.state)
        assertEquals(0, tools.invocations.size)
        assertTrue(after.trace.events.any { it is TraceEvent.StepRejected })
    }

    @Test
    fun `exhausting the step budget blocks instead of stopping silently`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected(), ToolResult.Effected()))
        val tight = AgentExecutor(registry, tools, RuntimeBudget(maxSteps = 1, maxConsecutiveFailures = 2))

        var s = tight.advance(session())
        s = tight.advance(s.copy(consents = mapOf(1 to true)))

        assertEquals(ExecutionState.Blocked, s.state)
    }

    @Test
    fun `repeated tool failures fail the session rather than looping`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Failed(CommandFailure.Generic), ToolResult.Failed(CommandFailure.Generic)),
        )
        val executor = AgentExecutor(registry, tools, RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 1))

        val after = executor.advance(session())

        assertEquals(ExecutionState.Failed, after.state)
    }

    @Test
    fun `advance on a terminal or awaiting session is a no-op`() = runTest {
        val tools = FakeToolExecutor(emptyList())
        val executor = AgentExecutor(registry, tools, budget)

        for (state in listOf(
            ExecutionState.AwaitingConsent, ExecutionState.Paused, ExecutionState.Completed,
            ExecutionState.Cancelled, ExecutionState.Failed, ExecutionState.Blocked,
        )) {
            val frozen = session().copy(state = state)
            assertEquals(frozen, executor.advance(frozen))
        }
        assertEquals(0, tools.invocations.size)
    }

    @Test
    fun `every executed step carries both a ToolInvoked and a ToolObserved event`() = runTest {
        val tools = FakeToolExecutor(
            listOf(notInstalled(), ToolResult.Effected()),
        )
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s)
        s = executor.advance(s.copy(state = ExecutionState.Running, consents = mapOf(1 to true)))

        val invoked = s.trace.events.filterIsInstance<TraceEvent.ToolInvoked>().map { it.index }
        val observed = s.trace.events.filterIsInstance<TraceEvent.ToolObserved>().map { it.index }
        assertEquals(invoked, observed)
        assertEquals(listOf(0, 1), invoked)
    }

    @Test
    fun `prepare records ToolInvoked without calling the tool`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        val executor = AgentExecutor(registry, tools, budget)

        val prepared = executor.prepare(session())

        assertEquals(0, tools.invocations.size)
        assertTrue(prepared.trace.events.last() is TraceEvent.ToolInvoked)
    }

    @Test
    fun `perform on a session that is not mid-step is a no-op`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        val executor = AgentExecutor(registry, tools, budget)

        val untouched = session()
        val after = executor.perform(untouched)

        assertEquals(0, tools.invocations.size)
        assertEquals(untouched, after)
    }

    // --- Containment: a throwing worker must not kill the caller --------------------------------

    @Test
    fun `a worker that throws is observed as Failed rather than killing the caller`() = runTest {
        val throwing = object : ToolExecutor {
            override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
                throw IllegalStateException("no activity found to handle this intent")
        }
        val executor = AgentExecutor(registry, throwing, budget)

        val advanced = executor.advance(session())

        val observed = advanced.observations.getValue(0)
        assertTrue("expected a Failed observation, got $observed", observed is ToolResult.Failed)
        assertEquals(1, advanced.cursor)
        assertTrue(
            "the throw must still be recorded in the trace",
            advanced.trace.events.any { it is TraceEvent.ToolObserved && it.index == 0 },
        )
    }

    @Test
    fun `cancellation is never converted into a Failed observation`() = runTest {
        val cancelling = object : ToolExecutor {
            override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
                throw CancellationException("parent scope cancelled")
        }
        val executor = AgentExecutor(registry, cancelling, budget)

        try {
            executor.advance(session())
            fail("cancellation must propagate, not become a Failed observation")
        } catch (expected: CancellationException) {
            // the contract: parent cancellation is never swallowed
        }
    }

    // --- Fix round 1 regression + coverage tests -----------------------------------------------

    @Test
    fun `prepare does not advance the cursor`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        val executor = AgentExecutor(registry, tools, budget)

        val prepared = executor.prepare(session())

        assertEquals(0, prepared.cursor)
    }

    @Test
    fun `perform does not re-invoke a step that already has a matching ToolObserved`() = runTest {
        // Last event IS ToolInvoked(0), but an earlier ToolObserved(0) already exists in the trace —
        // this is exactly the shape the "alreadyObserved" half of the mid-step predicate must catch;
        // without it, this reads as mid-step and the tool fires a second time.
        val tools = FakeToolExecutor(emptyList())
        val executor = AgentExecutor(registry, tools, budget)

        val corrupted = session().copy(
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
                    TraceEvent.ToolObserved(0, ToolResult.Effected()),
                    TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
                ),
            ),
        )

        val after = executor.perform(corrupted)

        assertEquals(0, tools.invocations.size)
        assertEquals(corrupted, after)
    }

    @Test
    fun `advance never invokes the tool for a Cancelled or Paused mid-step session`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)
        val midStepTrace = ExecutionTrace(
            listOf(TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP)),
        )

        for (state in listOf(ExecutionState.Cancelled, ExecutionState.Paused)) {
            val frozen = session().copy(state = state, trace = midStepTrace)
            assertEquals(frozen, executor.advance(frozen))
        }
        assertEquals(0, tools.invocations.size)
    }

    @Test
    fun `perform refuses to invoke when the trace names a step index the plan does not contain`() = runTest {
        // `ExecutionPlan` now enforces index == position, so the trace/plan disagreement this branch
        // guards can no longer be expressed as a shuffled plan — it is expressed from the TRACE side,
        // which is where it was always the realistic one. The invariant binds an index to a position
        // *within one plan*; nothing binds a persisted trace to the plan it is later replayed against,
        // so a plan swapped underneath a stored trace (a restore against a different build, a shorter
        // re-plan) can leave the tail naming a step that no longer exists.
        //
        // The recorded toolId deliberately MATCHES step 0's, so only the index lookup can refuse this:
        // a lookup that fell back to the first step would sail straight through the id-match assertion.
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)

        val stale = session().copy(
            trace = ExecutionTrace(
                listOf(TraceEvent.StepStarted(5), TraceEvent.ToolInvoked(5, ToolIds.LAUNCH_APP)),
            ),
        )

        val after = executor.perform(stale)

        assertEquals(0, tools.invocations.size)
        assertEquals(stale, after)
    }

    @Test
    fun `perform refuses to invoke when the resolved step's tool does not match the trace`() = runTest {
        // Step 0's real invocation is LAUNCH_APP; a corrupted trace claims PLAY_STORE_SEARCH was the one
        // invoked for it. The id-match assertion must refuse to invoke rather than run either tool.
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)

        val tampered = session().copy(
            trace = ExecutionTrace(
                listOf(TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.PLAY_STORE_SEARCH)),
            ),
        )

        val after = executor.perform(tampered)

        assertEquals(0, tools.invocations.size)
        assertEquals(tampered, after)
    }

    @Test
    fun `resuming through advance after persisting the prepared step does not duplicate ToolInvoked`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        val executor = AgentExecutor(registry, tools, budget)

        val prepared = executor.prepare(session())    // simulates: persisted right after prepare
        val resumed = executor.advance(prepared)       // simulates: process restarts, resumes via advance()

        val invoked = resumed.trace.events.filterIsInstance<TraceEvent.ToolInvoked>()
        assertEquals(1, invoked.size)
        assertEquals(1, tools.invocations.size)
    }

    // --- F6: step-to-step data flow -------------------------------------------------------------

    /**
     * The whole point of the binding. The goal's literal is `убер`; step 0 *reports* `uber`. If step 1
     * were carrying its own copy of the goal text, the store would be asked for `убер` — it is asked
     * for what step 0 actually reported, because it is the same value and not a second copy of it.
     */
    @Test
    fun `the second step is invoked with exactly what the first step reported`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(query = "uber"), ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session(query = "убер"))
        s = executor.advance(s)
        s = executor.advance(s.copy(state = ExecutionState.Running, consents = mapOf(1 to true)))

        assertEquals(mapOf("query" to "убер"), tools.invocations[0].args)
        assertEquals(mapOf("query" to "uber"), tools.invocations[1].args)
    }

    /**
     * A binding that cannot be bound is a rejection, and it is raised in `prepare` — **before**
     * `ToolInvoked` reaches the trace. `ToolInvoked(i)` with no `ToolObserved(i)` has exactly one
     * meaning in this design ("the process died during the call"), and a rejection that counterfeited
     * that shape would make `DOC-ILM-3`'s "the trace is 1:1 with reality" false.
     */
    @Test
    fun `an unresolvable binding is rejected before ToolInvoked is written`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)

        val afterFailedSource = session().copy(
            cursor = 1,
            consents = mapOf(1 to true),
            observations = mapOf(0 to ToolResult.Failed(CommandFailure.Generic)),
            plan = unconditionalFallbackPlan(),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.StepStarted(0),
                    TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
                    TraceEvent.ToolObserved(0, ToolResult.Failed(CommandFailure.Generic)),
                ),
            ),
        )

        val after = executor.advance(afterFailedSource)

        assertEquals(ExecutionState.Failed, after.state)
        assertEquals(0, tools.invocations.size)
        assertTrue(
            after.trace.events.any {
                it is TraceEvent.StepRejected &&
                    it.index == 1 &&
                    it.reason == RejectionReason.UNRESOLVED_ARG_SOURCE
            },
        )
        assertTrue(
            "a resolution rejection must not counterfeit a mid-step trace",
            after.trace.events.none { it is TraceEvent.ToolInvoked && it.index == 1 },
        )
    }

    /** Resolution runs *after* the consent checkpoint, so a stop for consent is never pre-empted. */
    @Test
    fun `an unresolvable binding still stops at the consent checkpoint first`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)

        val afterFailedSource = session().copy(
            cursor = 1,
            observations = mapOf(0 to ToolResult.Failed(CommandFailure.Generic)),
            plan = unconditionalFallbackPlan(),
            trace = ExecutionTrace(emptyList()),
        )

        val after = executor.advance(afterFailedSource)

        assertEquals(ExecutionState.AwaitingConsent, after.state)
        assertTrue(after.trace.events.none { it is TraceEvent.StepRejected })
    }

    /**
     * A skipped step is never resolved. Step 0 here returns `Effected()` with **no** output, so if the
     * engine resolved step 1's binding before honouring its precondition, the session would fail
     * instead of completing.
     */
    @Test
    fun `a skipped step never evaluates its binding`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s)
        s = executor.advance(s)

        assertEquals(ExecutionState.Completed, s.state)
        assertTrue(s.trace.events.any { it is TraceEvent.StepSkipped && it.index == 1 })
        assertTrue(s.trace.events.none { it is TraceEvent.StepRejected })
    }

    /**
     * A forward reference is a static defect in the plan, caught by `validate` before anything runs —
     * not something the engine discovers mid-flight.
     */
    @Test
    fun `a step binding to itself is rejected before any tool is called`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val selfBinding = session().copy(
            plan = ExecutionPlan(
                listOf(
                    session().plan.steps[0].copy(
                        invocation = ToolInvocation(
                            ToolIds.LAUNCH_APP,
                            mapOf("query" to ArgSource.FromStep(0, "resolved_query")),
                        ),
                    ),
                ),
            ),
        )

        val after = AgentExecutor(registry, tools, budget).advance(selfBinding)

        assertEquals(ExecutionState.Failed, after.state)
        assertEquals(0, tools.invocations.size)
        assertTrue(
            after.trace.events.any {
                it is TraceEvent.StepRejected && it.reason == RejectionReason.FORWARD_ARG_SOURCE
            },
        )
    }

    // --- The restore seam (review finding F1, 2026-08-23) ---------------------------------------

    /**
     * **The mid-step short-circuit, driven the way the product actually drives it.**
     *
     * `resuming through advance after persisting the prepared step…` above simulates the restart by
     * calling `advance` straight on the prepared session. The product never does that: the only path
     * a mid-step session takes back into the engine is `LauncherAgentSession.restoreOnStart` →
     * [AgentSession.pausedForRestore], then `continueSession` → [AgentSession.resumed] — and both
     * append a trace event **on top of** the pending `ToolInvoked`.
     *
     * With the short-circuit keyed on the trace *tail*, those two events hid the pending call: the
     * engine re-cleared the same step, wrote a second `StepStarted` + `ToolInvoked`, and only then
     * performed it. Two invocations recorded against one observation is not a trace 1:1 with reality,
     * and on a step whose consent was already granted it is one confirmation buying two executions.
     */
    @Test
    fun `continuing a session interrupted mid-call runs the pending step once and traces it once`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled()))
        val executor = AgentExecutor(registry, tools, budget)

        val midCall = executor.prepare(session())               // persisted; the process then dies
        val offered = midCall.pausedForRestore()                // restoreOnStart
        val after = executor.advance(offered.resumed())         // the user taps «Continue»

        assertEquals(1, tools.invocations.size)
        assertEquals(
            "one pending call must yield one ToolInvoked, not one per resume: ${after.trace.events}",
            1,
            after.trace.events.count { it is TraceEvent.ToolInvoked },
        )
        assertEquals(1, after.trace.events.count { it is TraceEvent.StepStarted })
        assertEquals(1, after.trace.events.count { it is TraceEvent.ToolObserved })
        assertEquals(1, after.cursor)
    }

    /**
     * The other half: a **consented** step interrupted mid-call must not spend the same consent twice.
     * `consents` survives the restart, so nothing asks again — which is correct only as long as the
     * pending call is resumed rather than re-issued.
     */
    @Test
    fun `a consented step interrupted mid-call is not invoked a second time on continue`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)

        val afterStep0 = executor.advance(session())                       // step 0 observes
        val consented = afterStep0.copy(consents = mapOf(1 to true))
        val midCall = executor.prepare(consented)                          // step 1 cleared, not run
        assertEquals(1, tools.invocations.size)

        val after = executor.advance(midCall.pausedForRestore().resumed())

        assertEquals(
            "the store step must run exactly once across the interruption: ${tools.invocations}",
            2,
            tools.invocations.size,
        )
        assertEquals(1, after.trace.events.count { it is TraceEvent.ToolInvoked && it.index == 1 })
        assertEquals(ExecutionState.Running, after.state)
    }

    // --- Risk is read from the registry, not only from the plan (finding F2) --------------------

    /**
     * A plan is a snapshot; the registry is the current truth. A session planned by a build in which
     * `play_store_search` was `SAFE`, resumed under this build where it is `CONFIRM`, must still stop
     * — nothing re-validates risk (`InvocationValidator` knows only shape), so if the gate trusted the
     * persisted `PlanStep.risk` the store step would run with no `ConsentRequested` at all.
     */
    @Test
    fun `a step whose persisted risk is stale still stops at the gate`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)
        val stalePlan = ExecutionPlan(
            listOf(
                planForMissingApp("убер").steps[0],
                planForMissingApp("убер").steps[1].copy(risk = ActionRiskLevel.SAFE),
            ),
        )

        var current = executor.advance(session().copy(plan = stalePlan))
        current = executor.advance(current)

        assertEquals(ExecutionState.AwaitingConsent, current.state)
        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
        assertTrue(
            "the gate must have asked: ${current.trace.events}",
            current.trace.events.any { it is TraceEvent.ConsentRequested && it.index == 1 },
        )
    }

    /** The inverse: a plan that says CONFIRM still stops even if the registry has since gone quiet. */
    @Test
    fun `a step whose persisted risk is higher than the registry's still stops at the gate`() = runTest {
        val allSafe = FakeToolRegistry(
            registry.all().map { if (it.id == ToolIds.PLAY_STORE_SEARCH) it.copy(risk = ActionRiskLevel.SAFE) else it },
        )
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val executor = AgentExecutor(allSafe, tools, budget)

        var current = executor.advance(session())
        current = executor.advance(current)

        assertEquals(ExecutionState.AwaitingConsent, current.state)
        assertEquals(1, tools.invocations.size)
    }

    // --- One decision, one event (finding F4) ----------------------------------------------------

    /**
     * `prepare` is the single writer of `ConsentResolved`. Both branches are checked here because the
     * bug was asymmetric: a granted decision produced one event and a refused one produced two, since
     * `ResolveConsentUseCase` wrote it as well and `prepare` wrote it again on its way to `Cancelled`.
     */
    @Test
    fun `a consent decision is traced exactly once, granted or refused`() = runTest {
        for (granted in listOf(true, false)) {
            val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
            val executor = AgentExecutor(registry, tools, budget)

            val atGate = executor.advance(executor.advance(session()))
            // What `ResolveConsentUseCase` does: record the decision, put the session back to Running.
            val decided = atGate.copy(consents = mapOf(1 to granted), state = ExecutionState.Running)
            val after = executor.advance(decided)

            assertEquals(
                "granted=$granted produced the wrong number of ConsentResolved: ${after.trace.events}",
                1,
                after.trace.events.count { it is TraceEvent.ConsentResolved && it.index == 1 },
            )
        }
    }

    /**
     * Once per step is a property of the **control flow**, not of a check: a cleared step short-
     * circuits on the mid-step tail before it can reach the consent block again, and every other route
     * out of that block ends the session. Written as an assertion about re-entry rather than about an
     * "already traced" guard, because the guard version of this test passed with the guard deleted —
     * it could not fail, which is the defect this block's own review is about.
     */
    @Test
    fun `a cleared step does not pass the consent block a second time`() = runTest {
        val tools = FakeToolExecutor(listOf(notInstalled(), ToolResult.Effected()))
        val executor = AgentExecutor(registry, tools, budget)

        val atGate = executor.advance(executor.advance(session()))
        val decided = atGate.copy(consents = mapOf(1 to true), state = ExecutionState.Running)
        val cleared = executor.prepare(decided)

        assertEquals("prepare on a cleared step must be a no-op", cleared, executor.prepare(cleared))
        assertEquals(
            1,
            cleared.trace.events.count { it is TraceEvent.ConsentResolved && it.index == 1 },
        )
    }

    /**
     * The same two steps, but step 1 is not gated on an observation — the only way to drive a
     * *resolution* rejection, since a skipped step is never resolved at all.
     */
    private fun unconditionalFallbackPlan() = ExecutionPlan(
        listOf(
            planForMissingApp("убер").steps[0],
            planForMissingApp("убер").steps[1].copy(precondition = StepPrecondition.None),
        ),
    )
}
