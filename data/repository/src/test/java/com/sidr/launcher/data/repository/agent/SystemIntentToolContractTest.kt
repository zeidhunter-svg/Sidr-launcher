package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The declaration and the adapter, checked against each other.**
 *
 * `ToolDescriptor.outputSchema` states that a tool which declares an output returns it on *every*
 * result that can carry one. [SystemIntentToolSource] makes that declaration and
 * [SystemIntentToolExecutor] honours it — but they are two files, and the tests for each of them pass
 * happily while the two drift apart: deleting `launch_app`'s `outputSchema` leaves every executor test
 * green, because no executor test ever consults the registry.
 *
 * This test consults both. For each result branch that can carry an output it asserts the emitted key
 * set **equals** the declared one, so a declared-but-never-returned output (which would fail closed at
 * run time as `UNRESOLVED_ARG_SOURCE`, mid-plan, on a device) and a returned-but-undeclared output
 * (which no step could ever bind to) are both build failures here instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemIntentToolContractTest {

    private val source = SystemIntentToolSource(DefaultActionCatalog())
    private val apps = FakeInstalledAppsRepository()
    private val actionExecutor = FakeActionExecutor()

    private fun executor() = SystemIntentToolExecutor(
        ExecuteActionUseCase(IntentActionResolver(apps), actionExecutor),
    )

    private val uber = InstalledApp(packageName = "com.uber", label = "убер", activityName = "Main")
    private val uberClone = InstalledApp(packageName = "com.uber.clone", label = "убер", activityName = "Main")

    /**
     * @param branch the result this world state must produce. Asserted before the schema comparison:
     *   without it a mis-set world state would silently run the same branch four times and the schema
     *   check would hold vacuously over one branch while claiming to cover three.
     */
    private data class Case(
        val toolId: ToolId,
        val installed: List<InstalledApp>,
        val branch: String,
    )

    @Test
    fun `every result that can carry an output carries exactly the keys its tool declares`() = runTest {
        val cases = listOf(
            Case(ToolIds.LAUNCH_APP, listOf(uber), branch = "Effected"),
            Case(ToolIds.LAUNCH_APP, emptyList(), branch = "Observed(APP_NOT_INSTALLED)"),
            Case(ToolIds.LAUNCH_APP, listOf(uber, uberClone), branch = "Observed(APP_AMBIGUOUS)"),
            Case(ToolIds.PLAY_STORE_SEARCH, listOf(uber), branch = "Effected"),
        )

        cases.forEach { case ->
            apps.appsToReturn = case.installed
            actionExecutor.reset()

            val result = executor().invoke(ResolvedInvocation(case.toolId, mapOf("query" to "убер")))
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
    fun `at least one A0 tool declares an output, so the comparison above is not two empty sets`() {
        assertTrue(source.all().any { it.outputSchema.isNotEmpty() })
    }

    private fun ToolResult.branch(): String = when (this) {
        is ToolResult.Effected -> "Effected"
        is ToolResult.Observed -> "Observed($fact)"
        is ToolResult.Failed -> "Failed($failure)"
    }

    private fun ToolResult.outputKeys(): Set<String> = when (this) {
        is ToolResult.Effected -> output.values.keys
        is ToolResult.Observed -> output.values.keys
        is ToolResult.Failed -> error("Failed carries no output by construction — it must not appear here")
    }
}
