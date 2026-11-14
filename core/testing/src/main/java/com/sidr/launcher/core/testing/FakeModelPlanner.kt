package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanning

/**
 * In-memory [ModelPlanner]. Returns [answer] (default `Declined` — the case that leaves FastPath's
 * outcome in place) and records what it was asked. The property names are `FakeCommandPlanner`'s on
 * purpose (A4′ phase 3a, Task 11): every "the model was (not) asked" assertion in the ten files that
 * used the router's fake moves over unchanged.
 */
class FakeModelPlanner(var answer: ModelPlanning = ModelPlanning.Declined) : ModelPlanner {

    var planCallCount: Int = 0
        private set

    /** The last command the model was asked about; `null` until [plan] is called. */
    var lastCommand: String? = null
        private set

    override suspend fun plan(command: String): ModelPlanning {
        planCallCount++
        lastCommand = command
        return answer
    }
}
