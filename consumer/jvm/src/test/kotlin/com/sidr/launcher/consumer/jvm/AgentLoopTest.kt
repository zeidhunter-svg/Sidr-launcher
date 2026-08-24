package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.consumer.jvm.plan.FilePlanner
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionIdFactory
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionStore
import com.sidr.launcher.consumer.jvm.tool.SandboxToolExecutor
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.RejectionReason
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The block's actual claim: one goal passes `goal → plan → gate → tool → observe → tool → result →
 * trace` on plain JVM, over the **unchanged** engine.
 */
class AgentLoopTest {

    @get:Rule val temp = TemporaryFolder()

    private val registry = SandboxToolSource()
    private fun store() = JvmAgentSessionStore(temp.root.toPath().resolve("state/session.json"))
    private fun executor() = AgentExecutor(registry, SandboxToolExecutor(temp.root.toPath()))

    private fun <T> OperationResult<T>.value(): T = (this as OperationResult.Success).value

    private fun goal() = AgentGoal("remove stale.lock", GoalShape.Free("remove stale.lock"))

    private suspend fun startAndRun(store: JvmAgentSessionStore): AgentSession {
        val start = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry)
        val id = start.start(goal()).value()!!
        val run = RunAgentSessionUseCase(executor(), store)
        val session = store.active().value()!!
        return run.run(session).value().also { assertEquals(id, it.id) }
    }

    @Test
    fun `the loop stops for consent at the risk transition, before the world is touched`() = runTest {
        val file = temp.newFile("stale.lock")
        val store = store()

        val paused = startAndRun(store)

        assertEquals(ExecutionState.AwaitingConsent, paused.state)
        assertEquals(2, paused.cursor)
        assertTrue("nothing was deleted before consent", file.exists())
        assertTrue(
            // `checkpointFor`'s first branch since 2026-08-23 is `maxOf(PlanStep.risk, registry.risk)
            // >= CONFIRM` (A0 finding F2). DANGEROUS takes it either way, and the reason is RISK_LEVEL
            // rather than DURABLE_EFFECT — spec §11.3's recorded finding, not a defect to fix here.
            "the gate names the risk level, not durability — DANGEROUS takes checkpointFor's first branch",
            paused.trace.events.contains(TraceEvent.ConsentRequested(2, ConsentReason.RISK_LEVEL)),
        )
        assertTrue(
            "step 1 bound its path from step 0's output",
            paused.trace.events.contains(TraceEvent.ToolInvoked(1, SandboxToolIds.FIND_FILE)),
        )
    }

    /**
     * **The plan's risk and the registry's risk agree — asserted, not assumed** (A0 finding F2,
     * 2026-08-23).
     *
     * The gate acts on `maxOf(PlanStep.risk, registry.risk)` because the two have different lifetimes:
     * a plan is a snapshot of the build that wrote it, the registry is the declaration in force when it
     * runs. `FilePlanner` copies risk out of `SandboxToolSource`, so for this consumer they are the same
     * value — which is what makes the hit path above take the branch it does. That agreement is a
     * property of `FilePlanner`, not a law, so it is pinned here: a future `FilePlanner` that assigned
     * its own risk levels would still be *safe* (the max wins) but would stop being the clean evidence
     * spec §11.3 reports.
     */
    @Test
    fun `the planner's risk for every step is the registry's risk`() = runTest {
        val planned = FilePlanner().plan(goal(), registry)
        assertTrue("expected a plan", planned is PlanningResult.Planned)

        (planned as PlanningResult.Planned).plan.steps.forEach { step ->
            val declared = registry.find(step.invocation.id)
            assertTrue("no descriptor for ${step.invocation.id.value}", declared != null)
            assertEquals(
                "step ${step.index} (${step.invocation.id.value}) disagrees with the registry",
                declared!!.risk,
                step.risk,
            )
        }
    }

    /**
     * **A process death DURING a tool call is resumed, not re-issued** (A0 finding F1, 2026-08-23).
     *
     * This is the seam the A0 review found unheld, and the reason it went unheld for a whole block is
     * worth carrying into this one: the domain test that "covered" it simulated the restart by calling
     * `advance` on the prepared session, while the product goes through `pausedForRestore()` and
     * `resumed()` — both of which append trace events on top of the pending `ToolInvoked`. Keyed on the
     * trace *tail*, the engine's mid-step predicate missed the one shape it exists to recognise.
     *
     * Android could only reach this window with `pm disable-user` plus an on-device `force-stop` poll,
     * 157–170 ms wide. Here it is three lines: prepare, persist, walk away. That asymmetry — a core
     * invariant that the second consumer can hold and the first could only approximate — is itself an
     * answer to "what is a second consumer for", and Task 10 Step 4 reports it as one.
     */
    @Test
    fun `a step interrupted mid-call is performed once when the session is picked back up`() = runTest {
        temp.newFile("stale.lock")
        val store = store()
        val start = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry)
        start.start(goal()).value()

        // The mid-call shape, written the way `RunAgentSessionUseCase` writes it: prepare, save, die.
        val prepared = executor().prepare(store.active().value()!!)
        store.save(prepared)
        val tail = prepared.trace.events.last()
        assertTrue("the seed must be mid-step, was $tail", tail is TraceEvent.ToolInvoked)

        // A fresh process: find it, offer it, and continue only on an explicit choice.
        val offered = store.active().value()!!.pausedForRestore()
        store.save(offered)
        assertEquals(ExecutionState.Paused, offered.state)

        val continued = RunAgentSessionUseCase(executor(), store).run(offered.resumed()).value()

        val invokedZero = continued.trace.events.count {
            it is TraceEvent.ToolInvoked && it.index == 0
        }
        assertEquals(
            "one pending call is one ToolInvoked, however many times the process died: " +
                "${continued.trace.events}",
            1,
            invokedZero,
        )
        assertEquals(1, continued.trace.events.count { it is TraceEvent.StepStarted && it.index == 0 })
        assertEquals(ExecutionState.AwaitingConsent, continued.state)
    }

    @Test
    fun `granting consent completes the plan and the file is gone`() = runTest {
        val file = temp.newFile("stale.lock")
        val store = store()
        val paused = startAndRun(store)

        val resolve = ResolveConsentUseCase(store, RunAgentSessionUseCase(executor(), store))
        val done = resolve.resolve(paused.id, stepIndex = 2, granted = true).value()!!

        assertEquals(ExecutionState.Completed, done.state)
        assertTrue("the file is deleted", !file.exists())
    }

    @Test
    fun `refusing consent cancels and deletes nothing`() = runTest {
        val file = temp.newFile("stale.lock")
        val store = store()
        val paused = startAndRun(store)

        val resolve = ResolveConsentUseCase(store, RunAgentSessionUseCase(executor(), store))
        val done = resolve.resolve(paused.id, stepIndex = 2, granted = false).value()!!

        assertEquals(ExecutionState.Cancelled, done.state)
        assertTrue("a refusal must not act", file.exists())
        assertEquals("a terminal state leaves nothing at rest", null, store.active().value())
    }

    /**
     * **Renamed from `a second tap on confirm does not run the step twice` — that name promised a CAS
     * proof this test cannot give.** Step 2 is this plan's last step (spec §6.1: one `DANGEROUS` step,
     * always last), so granting it drives the session to `Completed`, and `RunAgentSessionUseCase.persist`
     * deletes a terminal session. The second `resolve()` call therefore hits
     * `recordConsentIfPending`'s **first** branch — `current == null -> false` — and never reaches the
     * `consents.containsKey(stepIndex)` branch that is the actual compare-and-set. A fresh-eyes review
     * proved this by deleting the `containsKey` branch outright: this test stayed green.
     *
     * What this test actually holds — a decision arriving after the session has already ended and been
     * swept from disk is a no-op, which matters for the same reason double-tapping a "Buy" button after
     * checkout must not double-charge — is real and worth keeping under its own name.
     *
     * **The compare-and-set itself is proven elsewhere, not here:**
     * `JvmAgentSessionStoreTest` (`consumer/jvm/src/test/kotlin/.../store/JvmAgentSessionStoreTest.kt`)
     * holds it directly against a session parked **mid-plan** (`AwaitingConsent`, not deleted) —
     * `recordConsentIfPending does not apply twice for the same step`, and, with two genuinely racing
     * calls, `two concurrent consent writes for the same step - exactly one applies`.
     *
     * **A named residual, not implied absent:** the loop-level double-tap on a *non-terminal* risky step
     * — the shape this test's original name promised — is structurally unreachable on this consumer.
     * `FilePlanner` only ever produces the one three-step shape spec §6.1 defines, whose sole `DANGEROUS`
     * step is always last; reaching a risky-then-another-step plan would mean changing that shape, which
     * is out of this block's scope.
     */
    @Test
    fun `a decision that arrives after the session ended is a no-op`() = runTest {
        temp.newFile("stale.lock")
        val store = store()
        val paused = startAndRun(store)

        val resolve = ResolveConsentUseCase(store, RunAgentSessionUseCase(executor(), store))
        assertTrue(resolve.resolve(paused.id, 2, granted = true).value() != null)
        assertEquals(
            "a decision for a session already ended (and swept from disk) must not apply",
            null,
            resolve.resolve(paused.id, 2, granted = true).value(),
        )
    }

    /**
     * **Deviation from the brief, evidenced.** The brief's literal body was `startAndRun(store)` alone,
     * asserting `Failed` directly. Run as written it never reaches `Failed` — it stops at
     * `AwaitingConsent`, cursor 2, exactly like the hit path, because `AgentExecutor.prepare` computes
     * `checkpointFor` (pure risk, `delete_file` is DANGEROUS regardless of whether the target exists)
     * **before** it ever calls `InvocationValidator.resolve` to bind step 2's argument (`prepare`'s own
     * KDoc: "Binding... belongs HERE — after the checkpoint, before ToolInvoked"). A missing file changes
     * nothing about *whether* the gate fires, only what binding does once past it. So the rejection this
     * test is named for is reachable only *after* consent is granted for step 2 — same as the "granting
     * consent completes the plan" test above, except here `find_file` reported a blank `resolved_path`
     * (`SandboxToolExecutor.findFile`: the key is emitted on every branch, blank when nothing matched),
     * so the bind fails closed instead of succeeding.
     */
    @Test
    fun `a missing target rejects the bound step and never reaches the world`() = runTest {
        val store = store()
        val paused = startAndRun(store)

        val resolve = ResolveConsentUseCase(store, RunAgentSessionUseCase(executor(), store))
        val ended = resolve.resolve(paused.id, stepIndex = 2, granted = true).value()!!

        assertEquals(ExecutionState.Failed, ended.state)
        assertTrue(
            "the binding failed closed rather than deleting something blank",
            ended.trace.events.contains(TraceEvent.StepRejected(2, RejectionReason.UNRESOLVED_ARG_SOURCE)),
        )
        assertTrue(
            "delete_file was never invoked",
            ended.trace.events.none { it is TraceEvent.ToolInvoked && it.index == 2 },
        )
    }

    /**
     * **A recorded architectural finding, held as a test rather than as a paragraph** (spec §6.3,
     * §11.2; owner instruction 2026-08-23).
     *
     * The same shape of reality — "the thing you named is not there" — takes two different paths:
     *  - on the **Android** consumer it has a name, `ObservedFact.APP_NOT_INSTALLED`. It satisfies the
     *    next step's precondition, the store step runs, and the session ends `Completed`.
     *  - **here** it has no name. `find_file` reports a blank `resolved_path`, the binding fails closed
     *    as `UNRESOLVED_ARG_SOURCE`, and the session ends `Failed`.
     *
     * Same reality, two session outcomes and two traces, purely because `ObservedFact` is a closed
     * two-value Android-shaped enum. It is a **trace-fidelity** gap, not a safety gap: the step
     * correctly does not run either way.
     *
     * **THE ASYMMETRY IS INTENDED AND STAYS.** Owned by A1'. If you have just made this test fail by
     * making the two agree, you have changed the thing this block exists to record — revert it, or take
     * the decision to the owner. Relaxing or deleting this test is a conscious act against a named
     * decision, not a tidy-up.
     *
     * **Mechanics note, not part of the finding:** reaching `Failed` requires granting consent for step
     * 2 first — same reason as the miss-path test above: the DANGEROUS-risk checkpoint on `delete_file`
     * fires on risk alone, before binding is attempted, so it gates identically whether or not the
     * target exists. Only past that gate does the blank `resolved_path` from `find_file` turn into
     * `UNRESOLVED_ARG_SOURCE`. This is scaffolding to reach the divergence, not the divergence itself.
     */
    @Test
    fun `a missing target ends Failed here where Android ends Completed - INTENDED divergence, owned by A1 prime`() = runTest {
        val store = store()
        val paused = startAndRun(store)
        val resolve = ResolveConsentUseCase(store, RunAgentSessionUseCase(executor(), store))
        val ended = resolve.resolve(paused.id, stepIndex = 2, granted = true).value()!!

        // Half one: the outcome on this consumer.
        assertEquals(
            "the JVM consumer ends Failed for a missing target. If this now says Completed, someone " +
                "has unified the two consumers' outcomes — see the KDoc above; that is not a task here.",
            ExecutionState.Failed,
            ended.state,
        )

        // Half two: WHY it differs, and the mechanical hold. The Android consumer ends `Completed`
        // because it can name the fact. The moment `ObservedFact` gains a value for "the thing is not
        // there", this assertion fails — which is precisely the edit this block has decided not to
        // make, and the reason this test is the guard rather than a comment.
        assertEquals(
            "ObservedFact must stay at A0's two values. A third value here means the finding was " +
                "'fixed' instead of recorded (spec §2 Approach A; owner instruction 2026-08-23).",
            listOf("APP_NOT_INSTALLED", "APP_AMBIGUOUS"),
            ObservedFact.entries.map { it.name },
        )
    }
}
