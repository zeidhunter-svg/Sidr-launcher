package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult

/**
 * Scripted executor. [invocations] is the record tests assert against — "the store was never called"
 * is the shape of most A0 assertions, so the recording matters more than the return values.
 */
class FakeToolExecutor(private val script: List<ToolResult>) : ToolExecutor {

    val invocations = mutableListOf<ToolInvocation>()

    override suspend fun invoke(invocation: ToolInvocation): ToolResult {
        val index = invocations.size
        invocations += invocation
        return script.getOrElse(index) { ToolResult.Failed(CommandFailure.Generic) }
    }
}
