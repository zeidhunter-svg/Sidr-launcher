package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.ai.bridge.BridgeRequest
import com.sidr.launcher.domain.ai.bridge.BridgeTransport
import com.sidr.launcher.domain.ai.bridge.TransportReply
import kotlinx.coroutines.delay

/**
 * A scripted [BridgeTransport]: one reply per ask, in order, each after [delayMs] of (virtual) time.
 * An ask beyond the script throws — a planner that asks more often than the test expected fails loudly.
 * [sendsCap] is its answer to [BridgeTransport.sendsReasoningCap] — whether the host would get the cap.
 */
class FakeBridgeTransport(
    replies: List<TransportReply>,
    private val delayMs: Long = 0L,
    private val sendsCap: Boolean = false,
) : BridgeTransport {

    override suspend fun sendsReasoningCap(): Boolean = sendsCap

    private val queue = ArrayDeque(replies)

    val requests = mutableListOf<BridgeRequest>()

    override suspend fun ask(request: BridgeRequest): TransportReply {
        requests += request
        if (delayMs > 0) delay(delayMs)
        return queue.removeFirstOrNull() ?: error("FakeBridgeTransport: ask #${requests.size} has no scripted reply")
    }
}
