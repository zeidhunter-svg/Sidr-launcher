package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.consumer.jvm.plan.FilePlanner
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionIdFactory
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionStore
import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.consumer.jvm.tool.SandboxToolWorker
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawArg
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.ReplyMeta
import com.sidr.launcher.domain.ai.bridge.TransportReply
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolInvocation
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
import java.nio.file.Files
import java.nio.file.Path

class ConsoleHarnessTest {

    @get:Rule val temp = TemporaryFolder()

    private val printed = mutableListOf<String>()

    private fun harness(answers: MutableList<String>) = ConsoleHarness(
        root = temp.root.toPath(),
        out = { printed += it },
        ask = { answers.removeFirstOrNull() },
    )

    /** The harness with a model behind it: [transport] answers every ask the bridge makes. */
    private fun harness(answers: MutableList<String>, transport: BridgeTransport) = ConsoleHarness(
        root = temp.root.toPath(),
        out = { printed += it },
        ask = { answers.removeFirstOrNull() },
        transport = transport,
    )

    /** A fake transport: the same reply to every ask, each request kept. No network, no environment. */
    private class ScriptedTransport(private val reply: TransportReply) : BridgeTransport {
        val requests = mutableListOf<BridgeRequest>()

        override suspend fun ask(request: BridgeRequest): TransportReply {
            requests += request
            return reply
        }
    }

    /**
     * The model's reading of «find orvelle.txt and delete it» (Review Focus 2): `find_file(name = "orvelle.txt")`,
     * then `delete_file(path ← step 0's resolved_path)` — the reference form `:data:ai-cloud` and this module's
     * transport both read. Every other argument name of the offering is given `null`, as the prompt asks.
     */
    private val findThenDelete = TransportReply.Answer(
        RawAnswer(
            kind = "plan",
            steps = listOf(
                RawStep("find_file", mapOf("name" to RawArg.Text("orvelle.txt"), "new_name" to null, "path" to null)),
                RawStep("delete_file", mapOf("name" to null, "new_name" to null, "path" to RawArg.Ref(step = 0, key = "resolved_path"))),
            ),
            question = null,
            destination = null,
        ),
        ReplyMeta.NONE,
    )

    /** Every regular file under the sandbox, outside the harness's own session store, with its bytes. */
    private fun tree(): Map<String, List<Byte>> {
        val root = temp.root.toPath()
        return Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && !root.relativize(it).startsWith(STORE_DIR) }
                .toList()
                .associate { root.relativize(it).toString() to Files.readAllBytes(it).toList() }
        }
    }

    /**
     * A4′ phase 3b, Task 13 — the JVM consumer's model path (B-12, B-13): `ask <goal>` goes to the SAME bridge
     * `:domain` holds, over this consumer's offering (the sandbox level); its answer is validated, bound on
     * this computer (the root by the device), printed as a card, and only a `y` to the card starts it through
     * `startPlanned` — after which the engine's consent prompt is the one the deterministic path has always had.
     */
    @Test
    fun `a free goal goes through the model, the card, then the engine`() = runTest {
        val orvelle = temp.newFile("orvelle.txt").toPath().toRealPath()
        temp.newFile("pallimer.md")
        val sandbox = temp.root.toPath().toRealPath()
        val transport = ScriptedTransport(findThenDelete)

        val state = harness(mutableListOf("y", "y"), transport).run("ask find orvelle.txt and delete it")

        assertEquals(ExecutionState.Completed, state)
        assertEquals(listOf("find orvelle.txt and delete it"), transport.requests.map { it.command })
        // Phase 3b close, final review B-M7 — the JVM's egress sentinel: nothing of this computer reaches the
        // model in OUR words, the schema or a retry note — not a file the sandbox holds, not the sandbox's own
        // path (bound by the device after the answer). The command is the one part that is the user's.
        val world = listOf("pallimer", "orvelle", temp.root.toPath().toString(), sandbox.toString())
        transport.requests.forEach { request ->
            listOfNotNull(request.system, request.schemaJson, request.retryNote).forEach { part ->
                world.forEach { trace -> assertFalse("«$trace» reached the model's request", part.contains(trace)) }
            }
        }
        val card = listOf(
            "The model's reading of \"find orvelle.txt and delete it\":",
            "  0. find_file(query=orvelle.txt [user], root=$sandbox [device]) — risk SAFE",
            "  1. delete_file(path ← step 0 resolved_path) — risk DANGEROUS",
            "Run this plan? [y/N]",
        )
        val at = printed.indexOf(card.first())
        assertTrue("the card is printed:\n${printed.joinToString("\n")}", at >= 0)
        assertEquals(card, printed.subList(at, minOf(printed.size, at + card.size)))
        assertTrue("the engine starts only after the card", printed.indexOf("  PlanCreated(stepCount=2)") > at)
        assertTrue("the consent prompt names the file found", printed.contains("  Arguments: path=$orvelle"))
        assertFalse("the file the model named is deleted", File(temp.root, "orvelle.txt").exists())
        assertTrue("nothing else is touched", File(temp.root, "pallimer.md").exists())
    }

    /** The model's reading of «find <name> and delete it» for whatever file the command names. */
    private fun findThenDeleteOf(command: String) = TransportReply.Answer(
        RawAnswer(
            kind = "plan",
            steps = listOf(
                RawStep("find_file", mapOf("name" to RawArg.Text(command.removePrefix("find ").removeSuffix(" and delete it")), "new_name" to null, "path" to null)),
                RawStep("delete_file", mapOf("name" to null, "new_name" to null, "path" to RawArg.Ref(step = 0, key = "resolved_path"))),
            ),
            question = null,
            destination = null,
        ),
        ReplyMeta.NONE,
    )

    /** A transport that reads each command as «find X and delete it», counting how often it is asked. */
    private class FindDeleteTransport : BridgeTransport {
        val requests = mutableListOf<BridgeRequest>()
        override suspend fun ask(request: BridgeRequest): TransportReply {
            requests += request
            return reply(request.command)
        }
        lateinit var reply: (String) -> TransportReply
    }

    /** Teaches «find … and delete it» three times, each through a fresh harness over the same sandbox (the file is the memory). */
    private suspend fun teachFindDelete(transport: FindDeleteTransport) {
        listOf("orvelle.txt", "pallimer.md", "quembly.txt").forEach { name ->
            temp.newFile(name)
            val state = harness(mutableListOf("y", "y"), transport).run("ask find $name and delete it")
            assertEquals(ExecutionState.Completed, state)
            assertFalse(File(temp.root, name).exists())
        }
    }

    /**
     * A4′ phase 4, Task 9 (B-13): the learned-plan cache on the second consumer. Three confirmations of one form
     * pin it; the fourth ask of that form — with another file's name in the slot — never reaches the model, runs
     * the same engine, and says that it is a learned command.
     */
    @Test
    fun `the fourth ask of a confirmed form runs without the model`() = runTest {
        val transport = FindDeleteTransport().also { it.reply = ::findThenDeleteOf }
        teachFindDelete(transport)
        assertEquals("the model answered each of the three", 3, transport.requests.size)
        printed.clear()
        temp.newFile("kestun.txt")

        val state = harness(mutableListOf("y"), transport).run("ask find kestun.txt and delete it")

        assertEquals("the model is not asked a fourth time", 3, transport.requests.size)
        assertEquals(ExecutionState.Completed, state)
        assertTrue("a learned line is printed:\n${printed.joinToString("\n")}", printed.contains("Learned command: find … and delete it"))
        assertTrue("the session ran", printed.contains("  ToolInvoked(1, delete_file)"))
        assertFalse("the file named in the slot is deleted", File(temp.root, "kestun.txt").exists())
    }

    /** A learned delete is still a `DANGEROUS` step: the prompt is asked every time, and «no» deletes nothing. */
    @Test
    fun `a learned delete still asks consent`() = runTest {
        val transport = FindDeleteTransport().also { it.reply = ::findThenDeleteOf }
        teachFindDelete(transport)
        printed.clear()
        temp.newFile("kestun.txt")

        val state = harness(mutableListOf("n"), transport).run("ask find kestun.txt and delete it")

        assertEquals("the model is not asked a fourth time", 3, transport.requests.size)
        assertTrue("the replayed delete asks:\n${printed.joinToString("\n")}", printed.contains("Proceed? [y/N]"))
        assertTrue(printed.any { it.startsWith("Consent required for step 1: delete_file") })
        assertEquals(ExecutionState.Cancelled, state)
        assertTrue("nothing is deleted", File(temp.root, "kestun.txt").exists())
    }

    /**
     * Task 13 fix round 1 (review I1): «nothing runs before a `y` to the card». The refused reading is one that
     * WOULD act with no further question — `rename_file` is `SAFE`, so the engine's consent gate would not stop
     * it — so an «n» the harness ignored would show up as a renamed file, a started session and a different
     * state. The card is asserted printed first (the file named in words is bound on this computer, through
     * the file sort), so the refusal is the card's and not an earlier stop.
     */
    @Test
    fun `a refused card starts nothing`() = runTest {
        val orvelle = temp.newFile("orvelle.txt").toPath().toRealPath()
        orvelle.toFile().writeText("SNTL-UNTOUCHED-ORVELLE")
        val before = tree()
        val transport = ScriptedTransport(
            TransportReply.Answer(
                RawAnswer(
                    kind = "plan",
                    steps = listOf(
                        RawStep("rename_file", mapOf("name" to null, "new_name" to RawArg.Text("quembly.txt"), "path" to RawArg.Text("orvelle.txt"))),
                    ),
                    question = null,
                    destination = null,
                ),
                ReplyMeta.NONE,
            ),
        )

        val state = harness(mutableListOf("n"), transport).run("ask rename orvelle.txt to quembly.txt")

        val card = listOf(
            "The model's reading of \"rename orvelle.txt to quembly.txt\":",
            "  0. rename_file(path=$orvelle [device], new_name=quembly.txt [user]) — risk SAFE",
            "Run this plan? [y/N]",
        )
        val at = printed.indexOf(card.first())
        assertTrue("the card is printed:\n${printed.joinToString("\n")}", at >= 0)
        assertEquals(card, printed.subList(at, minOf(printed.size, at + card.size)))
        assertEquals(ExecutionState.Cancelled, state)
        assertEquals("the model is asked once", 1, transport.requests.size)
        assertEquals("no session is started", emptyList<String>(), printed.filter { it.startsWith("  PlanCreated") })
        assertEquals(before, tree())
        assertEquals(
            "the store holds no session",
            OperationResult.Success(null),
            JvmAgentSessionStore(temp.root.toPath().resolve(".sidr-agent/session.json")).active(),
        )
    }

    /**
     * Review Focus 2: «find orvelle.txt, then delete it», and the find finds nothing. The plan validates and
     * STARTS (it is a well-formed plan; nothing is known about the sandbox before it runs), the user even says
     * `y` at the gate — and nothing is deleted: the delete's reference has no value, the step is rejected, and
     * the outcome says so. The tree is compared byte for byte, outside the harness's own session store.
     */
    @Test
    fun `a delete bound to a find that found nothing deletes nothing`() = runTest {
        temp.newFile("pallimer.md").writeText("SNTL-UNTOUCHED-PALLIMER")
        File(temp.newFolder("kestun"), "brask.txt").writeText("SNTL-UNTOUCHED-BRASK")
        val before = tree()

        val state = harness(mutableListOf("y", "y"), ScriptedTransport(findThenDelete)).run("ask find orvelle.txt and delete it")

        assertTrue("the plan validates and starts:\n${printed.joinToString("\n")}", printed.contains("  PlanCreated(stepCount=2)"))
        assertTrue("the find ran", printed.contains("  ToolInvoked(0, find_file)"))
        assertTrue(
            "the gate says the delete cannot be bound",
            printed.contains("  Arguments: not bindable (UNRESOLVED_ARG_SOURCE) — this step would fail rather than act."),
        )
        assertTrue("the outcome names the unresolved binding", printed.contains("  StepRejected(1, UNRESOLVED_ARG_SOURCE)"))
        assertEquals(ExecutionState.Failed, state)
        assertEquals(before, tree())
    }

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

    @Test
    fun `after a rename the harness offers undo, and undo puts the name back`() = runTest {
        temp.newFile("ledger.txt")

        val state = harness(mutableListOf("y")).run("rename ledger.txt to ledger.old")

        assertEquals(ExecutionState.Completed, state)
        val log = printed.joinToString("\n")
        assertTrue(log, log.contains("Undo outcome: Completed"))
        assertTrue(File(temp.root, "ledger.txt").exists())
        assertFalse(File(temp.root, "ledger.old").exists())
    }

    /**
     * I1 (fix round 1): "undo is never automatic" (spec §6.3) was asserted only in prose — the only
     * harness undo test answers "y", and every older test runs a `remove` plan with nothing undoable,
     * so `offerUndo` returns before ever reaching `ask()`. This is the first test that answers "n" to
     * an actually-undoable outcome: without the gate at `ConsoleHarness.offerUndo`, the rename would be
     * undone anyway.
     */
    @Test
    fun `after a rename the harness never undoes without a yes`() = runTest {
        temp.newFile("ledger.txt")

        val state = harness(mutableListOf("n")).run("rename ledger.txt to ledger.old")

        assertEquals(ExecutionState.Completed, state)
        val log = printed.joinToString("\n")
        assertTrue(log, log.contains("Undo what can be undone? [y/N]"))
        assertFalse(log, log.contains("Undo outcome"))
        assertTrue(File(temp.root, "ledger.old").exists())
        assertFalse(File(temp.root, "ledger.txt").exists())
    }

    /**
     * A4′ phase 3b, Task 12 — N2 on the JVM (plan §0.2 B-11: the harness already offers undo after every
     * resolved state, so it follows the domain). A plan that renames `ledger.txt` and then asks to delete
     * `other.txt` is refused at the delete's gate AFTER the rename acted: the outcome is `Cancelled`, and
     * the harness now offers to undo the rename — and does it only on a yes.
     *
     * `FilePlanner` builds no such plan (no undoable step before a gate — plan §0.3 P8), so the plan is
     * SEEDED through the store, exactly as the mid-call test above seeds its state; what is under test —
     * the rename running, the gate, the refusal, the undo offer and the undo — is walked by the harness.
     */
    @Test
    fun `a plan cancelled after a step acted offers undo`() = runTest {
        val ledger = temp.newFile("ledger.txt").toPath().toRealPath()
        val other = temp.newFile("other.txt").toPath().toRealPath()
        val store = JvmAgentSessionStore(temp.root.toPath().resolve(".sidr-agent/session.json"))
        val plan = ExecutionPlan(
            listOf(
                PlanStep(
                    0,
                    ToolInvocation(SandboxToolIds.RENAME_FILE, mapOf(SandboxKeys.PATH to ArgSource.Literal(ledger.toString()), SandboxKeys.NEW_NAME to ArgSource.Literal("ledger.old"))),
                    ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT,
                ),
                PlanStep(
                    1,
                    ToolInvocation(SandboxToolIds.DELETE_FILE, mapOf(SandboxKeys.PATH to ArgSource.Literal(other.toString()))),
                    ActionRiskLevel.DANGEROUS, StepPrecondition.None, StepRationale.GOAL_DIRECT,
                ),
            ),
        )
        val goal = AgentGoal("tidy the ledger", GoalShape.Free("tidy the ledger"))
        val seeded = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), SandboxToolSource())
            .startPlanned(goal, plan)
        assertTrue("the seed must be persisted, was $seeded", (seeded as OperationResult.Success).value != null)

        val state = harness(mutableListOf("n", "y")).run("tidy the ledger")

        val log = printed.joinToString("\n")
        assertEquals(ExecutionState.Cancelled, state)
        assertTrue("the refused delete did not run:\n$log", File(temp.root, "other.txt").exists())
        assertTrue("undo is offered after the refusal:\n$log", log.contains("Undo what can be undone? [y/N]"))
        assertTrue("the undo ran to its end:\n$log", log.contains("Undo outcome: Completed"))
        assertTrue("the rename is put back", File(temp.root, "ledger.txt").exists())
        assertFalse("the renamed file is gone", File(temp.root, "ledger.old").exists())
    }

    private companion object {
        /** The harness's own session store inside the sandbox (`ConsoleHarness`: `.sidr-agent/session.json`). */
        val STORE_DIR: Path = Path.of(".sidr-agent")
    }
}
