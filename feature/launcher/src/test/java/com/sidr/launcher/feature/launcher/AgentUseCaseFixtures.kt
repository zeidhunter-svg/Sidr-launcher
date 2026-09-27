package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.ResumeAgentSessionUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * A4′ phase 2 — the agent use cases a `LauncherViewModel` or `LauncherAgentSession` now also needs,
 * built one way for every test that constructs them. Not a fake: the real domain use cases over
 * whatever store, runner and registry the test already holds.
 */
internal class SequentialIds(private val prefix: String) : AgentSessionIdFactory {
    private var n = 0
    override fun newId() = AgentSessionId("$prefix${++n}")
}

internal fun resumeOver(
    store: AgentSessionStore,
    run: RunAgentSessionUseCase,
    registry: ToolRegistry,
): ResumeAgentSessionUseCase = ResumeAgentSessionUseCase(
    StartAgentSessionUseCase(TemplatePlanner(), store, SequentialIds("resumed-"), registry),
    run,
)
