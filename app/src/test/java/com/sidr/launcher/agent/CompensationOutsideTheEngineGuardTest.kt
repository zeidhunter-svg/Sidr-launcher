package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A4′ phase 2, spec §6.3 (owner, 2026-09-21): compensation must not buy silence. The strongest form of
 * that promise is structural — the engine that decides consent cannot see an inverse, an undo review
 * or an undo step, so no edit INSIDE it can let "this can be undone" lower a gate. Every rule for undo
 * lives outside `AgentExecutor.kt` (`Compensation`, `UndoAgentSessionUseCase`, the per-consumer
 * `ToolInverses`); this reads the engine's code, comments stripped, and requires it to name none of it.
 *
 * `UndoAgentSessionUseCaseTest > an undo whose inverse asks for consent stops at the gate…` is the
 * behavioural half. Both go red under M3a (plan Task 3).
 *
 * Reopening spec §6.3 (the first reversible CONFIRM tool, by ADR) is exactly the edit that must
 * change this guard — deliberately, in the same commit as the ADR.
 */
class CompensationOutsideTheEngineGuardTest {

    private val engine = File("../domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt")

    @Test
    fun `the engine that decides consent never sees an inverse or an undo`() {
        assertTrue("missing engine source: ${engine.canonicalPath}", engine.isFile)
        val code = stripComments(engine.readText())

        val seen = listOf("ToolInverses", "Undo", "Compensation", "COMPENSATION", "StepUndo").filter { code.contains(it) }

        assertEquals(
            "AgentExecutor must not consult undo in any form (spec §6.3). Found: $seen",
            emptyList<String>(),
            seen,
        )
    }
}
