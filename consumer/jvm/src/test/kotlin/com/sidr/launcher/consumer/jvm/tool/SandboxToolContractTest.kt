package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The twin of `SystemIntentToolContractTest`, for the second consumer.
 *
 * A tool that declares an output must return it on **every** result it can carry one on. The Android
 * source satisfies that only because `resolved_query` is an *echo of the input*; `find_file`'s output
 * is genuinely **discovered**, which is what makes this test the interesting one — it is the branch
 * where the two consumers' contracts are hardest to hold identically.
 */
class SandboxToolContractTest {

    @get:Rule val temp = TemporaryFolder()

    private val source = SandboxToolSource()
    private fun executor() = SandboxToolExecutor(temp.root.toPath())
    private fun rootArg() = temp.root.toPath().toAbsolutePath().toString()

    private data class Case(
        val toolId: ToolId,
        val args: Map<String, String>,
        val setUp: () -> Unit,
        val branch: String,
    )

    @Test
    fun `every result that can carry an output carries exactly the keys its tool declares`() = runTest {
        val cases = listOf(
            Case(SandboxToolIds.WORKSPACE_INFO, emptyMap(), {}, "Effected"),
            Case(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "hit.lock", SandboxKeys.ROOT to rootArg()),
                { temp.newFile("hit.lock") },
                "Effected",
            ),
            Case(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "absent.lock", SandboxKeys.ROOT to rootArg()),
                {},
                "Effected",
            ),
        )

        cases.forEach { case ->
            case.setUp()
            val result = executor().invoke(ResolvedInvocation(case.toolId, case.args))
            val declared = source.find(case.toolId)!!.outputSchema.map { it.name }.toSet()

            assertEquals("${case.toolId.value}: wrong branch for this world state", case.branch, result.branch())
            assertEquals(
                "${case.toolId.value} / ${case.branch}: emitted output keys must equal the declared outputSchema",
                declared,
                result.outputKeys(),
            )
        }
    }

    @Test
    fun `at least one sandbox tool declares an output, so the comparison above is not empty sets`() {
        assertTrue(source.all().any { it.outputSchema.isNotEmpty() })
    }

    private fun ToolResult.branch(): String = when (this) {
        is ToolResult.Effected -> "Effected"
        is ToolResult.Observed -> "Observed($fact)"
        is ToolResult.Failed -> "Failed(${failure::class.simpleName})"
    }

    private fun ToolResult.outputKeys(): Set<String> = when (this) {
        is ToolResult.Effected -> output.values.keys
        is ToolResult.Observed -> output.values.keys
        is ToolResult.Failed -> emptySet()
    }
}
