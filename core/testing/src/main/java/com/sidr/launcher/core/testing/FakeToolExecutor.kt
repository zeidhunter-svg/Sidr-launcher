package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolResult

/**
 * Scripted executor. [invocations] is the record tests assert against — "the store was never called"
 * is the shape of most A0 assertions, so the recording matters more than the return values.
 *
 * It records [ResolvedInvocation]s, so a test can assert on the **bound values** a step was actually
 * called with and not merely on which tool was reached (F6).
 */
class FakeToolExecutor(private val script: List<ToolResult>) : ToolExecutor {

    val invocations = mutableListOf<ResolvedInvocation>()

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
        val index = invocations.size
        invocations += invocation
        return script.getOrElse(index) { ToolResult.Failed(CommandFailure.Generic) }
    }
}
