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
        val plan = ExecutionPlan(
            listOf(
                PlanStep(
                    0,
                    ToolInvocation(id, emptyMap()),
                    ActionRiskLevel.SAFE,
                    StepPrecondition.None,
                    StepRationale.GOAL_DIRECT,
                ),
            ),
        )

        // The app is uninstalled while the plan sits paused.
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
     * The worker's own `runCatching` and `AgentExecutor.perform`'s call-site `try` both sit between a
     * throwing [ShortcutLauncher] and the caller. This test crosses both **and** Task 1's engine floor
     * in one run, over the real federation on both sides of [AgentExecutor] — so removing either
     * containment layer would surface here, not just in a per-layer unit test.
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
     * A fresh one-step `Running` session over [id], built the same way the first test builds its plan.
     * Written once; both tests would need it, so only the second does (the first calls the executor
     * directly, beneath the engine).
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
