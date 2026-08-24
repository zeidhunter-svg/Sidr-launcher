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

    @Test
    fun `a second tap on confirm does not run the step twice`() = runTest {
        temp.newFile("stale.lock")
        val store = store()
        val paused = startAndRun(store)

        val resolve = ResolveConsentUseCase(store, RunAgentSessionUseCase(executor(), store))
        assertTrue(resolve.resolve(paused.id, 2, granted = true).value() != null)
        assertEquals(
            "the second tap must not apply — recordConsentIfPending is a CAS",
            null,
            resolve.resolve(paused.id, 2, granted = true).value(),
        )
    }
}
