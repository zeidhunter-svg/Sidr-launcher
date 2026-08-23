package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import java.util.UUID

/**
 * `java.util.UUID` lives **here**, in the consumer — which is the entire reason
 * [AgentSessionIdFactory] is a port. A0's spec made it one because `UUID` does not exist in
 * `commonMain`; A0.5 is the first thing to show the port earning its keep, with a second
 * implementation the core needed no edit to accept. One of the two contracts that survived contact
 * with a second consumer (spec §11.2).
 */
class JvmAgentSessionIdFactory : AgentSessionIdFactory {
    override fun newId(): AgentSessionId = AgentSessionId(UUID.randomUUID().toString())
}
