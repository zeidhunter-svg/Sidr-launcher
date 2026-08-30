package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.consumer.jvm.plan.FilePlanner
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionIdFactory
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionStore
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.consumer.jvm.tool.SandboxToolWorker
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConsoleHarnessTest {

    @get:Rule val temp = TemporaryFolder()

    private val printed = mutableListOf<String>()

    private fun harness(answers: MutableList<String>) = ConsoleHarness(
        root = temp.root.toPath(),
        out = { printed += it },
        ask = { answers.removeFirstOrNull() },
    )

    @Test
    fun `a granted run completes and prints the whole trace`() = runTest {
        val file = temp.newFile("stale.lock")
        // Computed before the run, since the run itself deletes the file — toRealPath() needs the
        // file to exist, and this must be the same value SandboxToolWorker resolves it to.
        val resolvedTarget = file.toPath().toRealPath().toString()

        val state = harness(mutableListOf("y")).run("remove stale.lock")

        assertEquals(ExecutionState.Completed, state)
        val log = printed.joinToString("\n")
        assertTrue("the plan is announced", log.contains("PlanCreated(stepCount=3)"))
        assertTrue("the gate is visible", log.contains("ConsentRequested"))
        assertTrue("the outcome is stated", log.contains("Completed"))

        // The consent prompt must name the actual bound target, not just say something was found —
        // this is boundArguments() calling the engine's own InvocationValidator.resolve, and the
        // value asserted here is the real path SandboxToolWorker will act on, computed the same way
        // it computes it (toRealPath()), not a loose substring like "Arguments" that a wrong value
        // would still satisfy.
        assertTrue(
            "the consent prompt names the real path about to be deleted:\n$log",
            log.contains("Arguments: path=$resolvedTarget"),
        )
    }

    @Test
    fun `refusing at the prompt cancels`() = runTest {
        temp.newFile("stale.lock")
        val state = harness(mutableListOf("n")).run("remove stale.lock")
        assertEquals(ExecutionState.Cancelled, state)
    }

    @Test
    fun `an unreadable goal says so instead of guessing`() = runTest {
        val state = harness(mutableListOf()).run("what is the weather")
        assertEquals(ExecutionState.Blocked, state)
        assertTrue(printed.joinToString("\n").contains("No plan"))
    }

    /**
     * M-A1's "the session survives process death", on the second consumer. Answering nothing at the
     * prompt is this harness's stand-in for the process dying at the gate: the run returns with the
     * session persisted and unresolved. A **fresh** harness over the same directory must then find it
     * and present it as `Paused` — never resume it silently, because the person asked for this
     * minutes or days ago.
     *
     * **The second harness is given a different goal, and that is the whole test.** With the same goal
     * string on both runs, every assertion here passes just as well if the harness ignored the
     * persisted session and re-planned the text it was handed — the two hypotheses produce identical
     * output and identical effects, so the name "found by a fresh harness" was held by nothing. A
     * distinct second goal separates them: the persisted goal is the one printed, and the persisted
     * target is the file that disappears.
     */
    @Test
    fun `a session left at the gate is found by a fresh harness and offered, not resumed`() = runTest {
        temp.newFile("stale.lock")
        temp.newFile("other.lock")

        val first = harness(mutableListOf()).run("remove stale.lock")
        assertEquals(ExecutionState.AwaitingConsent, first)

        printed.clear()
        val resumed = harness(mutableListOf("y")).run("remove other.lock")

        val log = printed.joinToString("\n")
        assertTrue("the fresh harness reports the pause:\n$log", log.contains("Paused"))
        assertTrue("it recorded SessionPaused, not a silent resume:\n$log", log.contains("SessionPaused"))
        // Pinned to restore()'s own wording ("Its goal:"), not gate()'s ("Goal:") — this run also
        // passes through gate() for the same persisted session, which prints the goal again under a
        // different phrasing. A substring shared by both prints would stay green if restore()'s own
        // disclosure were deleted, since gate()'s later print would still satisfy it.
        assertTrue(
            "restore() itself names the persisted goal, so a `y` is informed before gate() runs:\n$log",
            log.contains("Its goal: \"remove stale.lock\""),
        )
        assertFalse("the goal typed now is not what runs:\n$log", log.contains("remove other.lock"))
        assertEquals(ExecutionState.Completed, resumed)

        assertFalse("the persisted target is what was deleted", File(temp.root, "stale.lock").exists())
        assertTrue("the typed target was never touched", File(temp.root, "other.lock").exists())
    }

    /**
     * **The second shape of process death: the process died DURING a tool call** — spec §9, required
     * rather than optional since the spec was re-anchored to `a7f4755`.
     *
     * A0's finding F1 was that the engine's own "died mid-call" signal — `ToolInvoked(i)` with no
     * `ToolObserved(i)` — was unreadable on the only path that produces it, because the restore
     * transitions write `SessionPaused` and `SessionResumed` on top of it and the predicate was keyed
     * on the trace *tail*. The step was re-cleared, re-traced and re-run; a step whose consent had
     * already been granted ran twice. Android could reach that window only with `pm disable-user` plus
     * an on-device `force-stop` poll, 157-170 ms wide. **This consumer reaches it by seeding a file.**
     *
     * Note what is simulated and what is not. A live process cannot be asked to die, so the mid-call
     * *state* is seeded through the store — that is setup. The **transition under test** — find it,
     * offer it, continue — is walked by the harness itself, which is what the Global Constraints rule
     * about simulated paths demands. Seeding the state and then also hand-rolling the restore would
     * reproduce exactly the mistake that cost A0 three findings.
     */
    @Test
    fun `a session that died mid-call is offered, and the pending call runs once`() = runTest {
        temp.newFile("stale.lock")
        val store = JvmAgentSessionStore(temp.root.toPath().resolve(".sidr-agent/session.json"))
        val registry = SandboxToolSource()
        // A1' Task 3 compile bridge: AgentExecutor still takes a ToolExecutor until Task 4 rewires
        // this consumer through ToolFederation (see ConsoleHarness's own note).
        val worker = SandboxToolWorker(temp.root.toPath())
        val executor = AgentExecutor(
            registry,
            object : ToolExecutor {
                override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = worker.invoke(invocation)
            },
        )
        val goal = AgentGoal("remove stale.lock", GoalShape.Free("remove stale.lock"))

        StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry)
            .start(goal)
        val prepared = executor.prepare((store.active() as OperationResult.Success).value!!)
        store.save(prepared)
        assertTrue(
            "the seed must be mid-call, was ${prepared.trace.events.last()}",
            prepared.trace.events.last() is TraceEvent.ToolInvoked,
        )

        val state = harness(mutableListOf("y")).run("remove stale.lock")

        val log = printed.joinToString("\n")
        assertTrue("the fresh harness reports the pause", log.contains("Paused"))
        assertEquals(
            "one pending call is one ToolInvoked, however many times the process died:\n$log",
            1,
            Regex("ToolInvoked\\(0,").findAll(log).count(),
        )
        assertEquals(ExecutionState.Completed, state)
    }
}
