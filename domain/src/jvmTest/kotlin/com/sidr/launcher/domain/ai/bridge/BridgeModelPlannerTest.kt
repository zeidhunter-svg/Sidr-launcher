package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.core.testing.FakeBridgeTransport
import com.sidr.launcher.domain.ai.AiError
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.descriptions
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.plan
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.registry
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The bridge's one model call (spec §7.12.2): one ask, at most one retry that adds ONLY the closed
 * code's static note, a ceiling that covers both, and every failure said by its reason.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BridgeModelPlannerTest {

    private val command = "открой lumora"
    private val valid = TransportReply.Answer(plan("launch_app", "app" to "lumora"), ReplyMeta.NONE)
    private val notOffered = TransportReply.Answer(
        plan("set_app_alias", "app" to "lumora", "phrase" to "SNTL-REJECTED-ECHO"),
        ReplyMeta.NONE,
    )

    private fun planner(transport: FakeBridgeTransport, budget: BridgeBudget = BridgeBudget.Default) =
        BridgeModelPlanner(registry, descriptions, transport, BridgeFilter.ANDROID_OFFERED_LEVELS, budget)

    @Test
    fun `a valid first answer asks the model once and returns its reading`() = runTest {
        val transport = FakeBridgeTransport(listOf(valid))

        val result = planner(transport).plan(command)

        assertEquals(ToolId("launch_app"), (result as ModelPlanning.Proposed).steps.single().tool)
        assertEquals(1, transport.requests.size)
    }

    /** Criterion 18: one retry, and the retry is the first request plus the closed code's note — nothing else. */
    @Test
    fun `an invalid answer is asked again exactly once, with only the closed code added`() = runTest {
        val transport = FakeBridgeTransport(listOf(notOffered, valid))

        val result = planner(transport).plan(command)

        assertEquals(ToolId("launch_app"), (result as ModelPlanning.Proposed).steps.single().tool)
        assertEquals(2, transport.requests.size)
        assertEquals(
            transport.requests[0].copy(retryNote = BridgeRendering.retryNote(BridgeError.NOT_OFFERED)),
            transport.requests[1],
        )
        assertFalse(transport.requests[1].toString().contains("SNTL-REJECTED-ECHO"))
    }

    /**
     * Spec §7.12.1 D1 — the model learns nothing about the world, refusals included. The validator tells
     * the two codes apart FOR THE DEVICE; the retry that goes out must not: otherwise a proposed
     * `shortcut:…` id reveals, by the note it gets back, whether that shortcut is installed.
     */
    @Test
    fun `a rejected id gets the same retry text whether or not it exists on the device`() = runTest {
        suspend fun retryRequestFor(tool: String): BridgeRequest {
            val transport = FakeBridgeTransport(
                listOf(TransportReply.Answer(plan(tool), ReplyMeta.NONE), valid),
            )
            planner(transport).plan(command)
            return transport.requests[1]
        }

        val registeredNotOffered = retryRequestFor("shortcut:net.fixture.qorvath/brimwell")
        val unregistered = retryRequestFor("fixture_quill")

        assertEquals(unregistered, registeredNotOffered)
        assertEquals(
            "Your previous reply was rejected: it named a tool that is not among the tool ids listed above. Use only those ids.",
            registeredNotOffered.retryNote,
        )
    }

    /** Criterion 18: never a second retry. */
    @Test
    fun `a second invalid answer is unreadable, and the model is never asked a third time`() = runTest {
        val transport = FakeBridgeTransport(listOf(notOffered, notOffered, valid))

        val result = planner(transport).plan(command)

        assertEquals(ModelPlanning.Unavailable(ModelUnavailableReason.UNREADABLE), result)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `an unparseable first reply is asked again with the shape code`() = runTest {
        val transport = FakeBridgeTransport(listOf(TransportReply.Unparseable(ReplyMeta.NONE), valid))

        planner(transport).plan(command)

        assertEquals(BridgeRendering.retryNote(BridgeError.SHAPE), transport.requests[1].retryNote)
    }

    /** Finding (c) of the A1″ acceptance: a broken key, a dead endpoint, a refused model each say so. */
    @Test
    fun `a transport failure is reported by its reason and never asked again`() = runTest {
        val table = listOf(
            AiError.Unauthorized to ModelUnavailableReason.CREDENTIALS,
            AiError.MissingCredentials to ModelUnavailableReason.CREDENTIALS,
            AiError.InvalidRequest("http_404") to ModelUnavailableReason.REQUEST_REJECTED,
            AiError.RateLimited(null) to ModelUnavailableReason.PROVIDER_FAILING,
            AiError.ServerError(502) to ModelUnavailableReason.PROVIDER_FAILING,
            AiError.Timeout to ModelUnavailableReason.TIMED_OUT,
            AiError.Network("IOException") to ModelUnavailableReason.NETWORK,
        )
        table.forEach { (error, reason) ->
            val transport = FakeBridgeTransport(listOf(TransportReply.Failed(error)))

            assertEquals("$error", ModelPlanning.Unavailable(reason), planner(transport).plan(command))
            assertEquals("$error", 1, transport.requests.size)
        }
    }

    /** Criterion 19: the ceiling bounds the WHOLE planning, retry included. */
    @Test
    fun `the ceiling covers both attempts`() = runTest {
        val transport = FakeBridgeTransport(listOf(notOffered, valid), delayMs = 30_000)

        val result = planner(transport, BridgeBudget(ceilingMs = 45_000, maxOutputTokens = 2_048)).plan(command)

        assertEquals(ModelPlanning.Unavailable(ModelUnavailableReason.TIMED_OUT), result)
        assertEquals(45_000L, currentTime)
    }

    @Test
    fun `the request carries the rendered offering and the command verbatim`() = runTest {
        val transport = FakeBridgeTransport(listOf(valid))

        planner(transport).plan(command)

        val offering = BridgeFilter.offeringOf(registry, BridgeFilter.ANDROID_OFFERED_LEVELS, descriptions)
        val request = transport.requests.single()
        assertEquals(BridgeRendering.systemText(offering), request.system)
        assertEquals(BridgeRendering.schemaJson(offering), request.schemaJson)
        assertEquals(command, request.command)
        assertEquals(null, request.retryNote)
    }

    @Test
    fun `the budget's output limit reaches the request`() = runTest {
        val transport = FakeBridgeTransport(listOf(valid))

        planner(transport, BridgeBudget(ceilingMs = 45_000, maxOutputTokens = 777)).plan(command)

        assertEquals(777, transport.requests.single().maxOutputTokens)
    }

    /**
     * The shipped numbers are phase 3b's in-phase measurement's (plan Task 14; ADR addendum «Замер фазы 3b»):
     * R4's 40 s ceiling, the 2026-11-07b output limit, and the reasoning cap ON at 4 096 / 2 by R3 (fork 3b-H).
     * A legitimate re-measure edits this line; a silent revert of the constants no longer passes the gate.
     */
    @Test
    fun `the shipped budget is the re-measured pair`() {
        assertEquals(BridgeBudget(40_000L, 4_096, 2_048), BridgeBudget.Default)
    }

    /** Fork 3b-H: the cap is a generation limit of the budget, so the one retry carries it as the first ask did. */
    @Test
    fun `the reasoning cap travels with the first ask and the retry`() = runTest {
        val transport = FakeBridgeTransport(listOf(notOffered, valid))

        planner(transport, BridgeBudget(ceilingMs = 45_000, maxOutputTokens = 4_096, reasoningMaxTokens = 1_234)).plan(command)

        assertEquals(listOf<Int?>(1_234, 1_234), transport.requests.map { it.reasoningMaxTokens })
    }
}
