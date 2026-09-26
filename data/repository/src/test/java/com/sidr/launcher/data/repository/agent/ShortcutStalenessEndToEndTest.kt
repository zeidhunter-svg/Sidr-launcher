package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.agent.shortcut.AppShortcut
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutLauncher
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolWorker
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seam Phase 1's own per-task tests never cross: **dynamic registry -> federation -> validator ->
 * worker**, over the real `app_shortcut` adapter (Tasks 4, 6, 7). [Tier0ToolExecutionEndToEndTest] is
 * this module's precedent for the shape — this block's most expensive lesson, twice over now: four
 * layers each fully tested in isolation summed to a capability that was dead in production while every
 * test stayed green.
 *
 * `app_shortcut` adds a failure mode none of the earlier adapters had: **its tool set moves while the
 * process lives.** A shortcut a plan named can be gone by the time the step actually runs — the app was
 * uninstalled, the shortcut was unpublished — and the two tests below are about what happens at that
 * moment, not about the adapter's steady state (already covered per-layer). Neither test needs
 * Robolectric: every type in the chain up to [ShortcutLauncher] is plain Kotlin, and `ShortcutLauncher`
 * is the one Android seam, faked here exactly as [ShortcutToolWorkerTest] fakes it.
 */
class ShortcutStalenessEndToEndTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /**
     * A vanished tool must fail closed rather than route to a stale adapter.
     *
     * The plan is built against a federation that still advertises the shortcut; the call is then made
     * against a second federation, built over a catalog that has since gone empty — the shape of "the
     * app was uninstalled while the plan sat paused". `ToolFederation.executor` re-derives its snapshot
     * on every call (`ToolFederation.snapshot`), so the vanished id must be absent from both faces at
     * once: neither routable nor listed.
     */
    @Test
    fun `a shortcut that disappears between planning and invocation fails rather than crashing`() = runTest {
        val catalog = ShortcutCatalog(query = { listOf(appShortcut("com.a", "new_chat")) }, ioDispatcher = dispatcher)
        catalog.refresh()
        val source = ShortcutToolSource(catalog)
        val federation = ToolFederation(
            listOf(ToolAdapter(ToolLevels.APP_SHORTCUT, source, ShortcutToolWorker(ShortcutLauncher { _, _ -> }))),
        )

        val id = federation.registry.all().single().id

        // The app is uninstalled while the plan sits paused: a second federation, over a catalog
        // that has since gone empty, is what the step actually runs against.
        val emptied = ShortcutCatalog(query = { emptyList() }, ioDispatcher = dispatcher).also { it.refresh() }
        val afterUninstall = ToolFederation(
            listOf(
                ToolAdapter(
                    ToolLevels.APP_SHORTCUT,
                    ShortcutToolSource(emptied),
                    ShortcutToolWorker(ShortcutLauncher { _, _ -> }),
                ),
            ),
        )

        val result = afterUninstall.executor.invoke(ResolvedInvocation(id, emptyMap()))

        assertTrue("a vanished tool must fail closed, not route to a stale adapter", result is ToolResult.Failed)
        assertNull(afterUninstall.registry.find(id))
    }

    /**
     * A throw originating in the real `app_shortcut` worker, run through the real [ToolFederation] and
     * the real [AgentExecutor], still yields an honest [ToolResult.Failed] observation carrying a
     * [TraceEvent.ToolObserved] entry — not an uncaught exception reaching the caller.
     *
     * **Named limitation, not implied absent: this does not distinguish which containment layer
     * caught the throw, because both produce the identical value.** `ShortcutToolWorker.invoke` wraps
     * the call in `runCatching { }.fold(...)` and returns `ToolResult.Failed(CommandFailure.Generic)`
     * as an ordinary value — no exception ever leaves the worker; `AgentExecutor.perform`'s call site
     * separately catches `Exception` into the same `ToolResult.Failed(CommandFailure.Generic)`. Delete
     * either layer alone and this test stays green — the other one still catches the same throw into
     * the same value. It reddens only if **both** are removed. What this test actually holds is
     * narrower than "either layer is load-bearing": a throw from the real worker, through the real
     * federation and the real engine, ends the run as `Failed` rather than killing the process — Task
     * 1's engine floor and Task 7's adapter catch, exercised together, with no assertion here able to
     * tell them apart.
     */
    @Test
    fun `a worker that throws inside the real federation still yields a Failed observation`() = runTest {
        val catalog = ShortcutCatalog(query = { listOf(appShortcut("com.a", "new_chat")) }, ioDispatcher = dispatcher)
        catalog.refresh()
        val federation = ToolFederation(
            listOf(
                ToolAdapter(
                    ToolLevels.APP_SHORTCUT,
                    ShortcutToolSource(catalog),
                    ShortcutToolWorker(ShortcutLauncher { _, _ -> throw IllegalStateException("shortcut is gone") }),
                ),
            ),
        )
        val id = federation.registry.all().single().id
        // Three parameters -- registry, toolExecutor, budget. `federation.registry` for the first: this
        // test is about the real federation on both sides, not a fake registry beside a real executor.
        val executor = AgentExecutor(federation.registry, federation.executor)

        val advanced = executor.advance(runningSessionFor(id))

        assertTrue(advanced.observations.getValue(0) is ToolResult.Failed)
        assertTrue(advanced.trace.events.any { it is TraceEvent.ToolObserved && it.index == 0 })
    }

    /**
     * A fresh one-step `Running` session over [id]: the same shape of single-step plan the first test
     * invokes directly (`PlanStep(0, ToolInvocation(id, emptyMap()), ActionRiskLevel.SAFE,
     * StepPrecondition.None, StepRationale.GOAL_DIRECT)`), wrapped in a session so [AgentExecutor] has
     * somewhere to run it. Written once; only the second test calls it — the first calls the executor
     * directly, beneath the engine, so it never needs a session.
     */
    private fun runningSessionFor(id: ToolId): AgentSession = AgentSession(
        id = AgentSessionId("s1"),
        goal = AgentGoal("open new chat", GoalShape.Free("open new chat")),
        plan = ExecutionPlan(
            listOf(
                PlanStep(
                    0,
                    ToolInvocation(id, emptyMap()),
                    ActionRiskLevel.SAFE,
                    StepPrecondition.None,
                    StepRationale.GOAL_DIRECT,
                ),
            ),
        ),
        cursor = 0,
        state = ExecutionState.Running,
        observations = emptyMap(),
        consents = emptyMap(),
        trace = ExecutionTrace(),
    )

    /** Local fixture -- `AppShortcut` needs four fields; only the first two vary in these tests. */
    private fun appShortcut(packageName: String, shortcutId: String) = AppShortcut(
        packageName = packageName,
        shortcutId = shortcutId,
        appLabel = "App",
        shortcutLabel = "Shortcut",
    )
}
