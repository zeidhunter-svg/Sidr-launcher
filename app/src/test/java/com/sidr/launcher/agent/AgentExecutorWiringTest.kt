package com.sidr.launcher.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.di.AgentProvidesModule
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A4′ phase 2, Task 1 (owner fork F2-1). `AgentExecutorTest` proves the ENGINE stops waiting when it
 * is handed a real context; this proves the composition root HANDS it one. The production provider is
 * called directly — the same seam `DynamicToolLabelWiringTest` uses — with the IO dispatcher Hilt binds
 * under `@IoDispatcher`.
 *
 * Why it matters on Android specifically: the agent runs on `viewModelScope` (Main), and a Tier-0 or
 * shortcut call reaches `startActivity` / `startShortcut` synchronously (plan §0.3 P1). A provider
 * that forgot the context would put every world call back on the main thread, where the budget's own
 * timeout is a Handler callback that cannot fire while the thread is held.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentExecutorWiringTest {

    @Test(timeout = 20_000)
    fun `the shipped executor stops waiting for a blocked world call at the budget`() = runTest {
        val release = CountDownLatch(1)
        val parked = object : ToolExecutor {
            override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
                release.await()
                return ToolResult.Effected()
            }
        }
        val session = AgentSession(
            id = AgentSessionId("wiring"),
            goal = AgentGoal("открой лумора", GoalShape.AppNotInstalled("лумора")),
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("лумора"))),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
            cursor = 0,
            state = ExecutionState.Running,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(emptyList()),
        )
        try {
            val executor = AgentProvidesModule.provideAgentExecutor(
                FakeToolRegistry.withA0Tools(),
                parked,
                Dispatchers.IO,
            )

            val after = executor.advance(session)

            assertEquals(ToolResult.HandedOff(), after.observations[0])
            assertEquals(RuntimeBudget.Default.maxStepWallClockMs, currentTime)
        } finally {
            release.countDown()
        }
    }
}
