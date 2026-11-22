package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanning

/**
 * In-memory [ModelPlanner]. Returns [answer] (default `Declined` — since task 5 of the A4′ acceptance tail the router
 * answers it «понял команду, но Sidr так пока не умеет» (`CommandMessage.UnderstoodNoAction`), not FastPath's outcome,
 * unless FastPath yielded or step 2b handed a target over, which the device then answers — K1, K11; batch 1 review
 * M-1) and records what it was asked. The property names are `FakeCommandPlanner`'s on
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
