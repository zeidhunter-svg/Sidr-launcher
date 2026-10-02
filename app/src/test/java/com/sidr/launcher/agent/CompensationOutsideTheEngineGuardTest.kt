package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A4′ phase 2, spec §6.3 (owner, 2026-09-21): compensation must not buy silence. This guard holds the
 * engine free of undo VOCABULARY — a name scan: `AgentExecutor.kt`, comments stripped, must not
 * mention `ToolInverses`, `Undo`, `Compensation`/`COMPENSATION` or `StepUndo`. It cannot see, and does
 * not try to see, whether some other spelling lets "this can be undone" influence a gate decision —
 * that is what the behavioural half holds.
 *
 * `UndoAgentSessionUseCaseTest > an undo whose inverse asks for consent stops at the gate…` is that
 * behavioural half: it holds the actual property, that undo never lowers a gate. Both go red under
 * M3a (plan Task 3).
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
