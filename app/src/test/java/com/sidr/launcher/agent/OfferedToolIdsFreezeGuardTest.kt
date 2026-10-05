package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADR 2 of A4′ phase 3a (owner fork F3a-B): the outbound contract with the model passed from the seven
 * `ActionIds` to the offered tools. Their VALUES are frozen — a rename or a removal orphans what phase 4
 * caches and what a model was taught, and needs a superseding ADR. Their NUMBER is not: a new tool is
 * offered by registering it, with no edit here (containment, not equality).
 */
class OfferedToolIdsFreezeGuardTest {

    @Test
    fun `the nineteen offered tool ids are frozen byte for byte`() {
        val offered = BridgeFilter.offeringOf(ProductionGraph.federation().registry, AuthoredToolDescriptions())
            .tools.map { it.descriptor.id.value }.toSet()

        assertEquals(emptySet<String>(), BridgeOfferingGuardTest.OFFERED_NINETEEN - offered)
    }
}
