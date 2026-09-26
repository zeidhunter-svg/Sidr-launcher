package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.memory.resolution.ResolvedCommand
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.model.InstalledApp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveCommandWithAliasUseCaseTest {
    private val store = FakeAliasStore()
    private val apps = FakeInstalledAppsRepository().apply {
        appsToReturn = listOf(InstalledApp("com.telegram", "Telegram"))
    }

    private fun step(result: ResolvedCommand) = ResolvedCommandStep { result }

    private fun useCase(inner: ResolvedCommand) = ResolveCommandWithAliasUseCase(
        inner = step(inner),
        store = store,
        installedApps = apps,
    )

    @Test
    fun `unknown with installed alias hit becomes AutoLaunch with unknown fallback`() = runTest {
        store.upsert(Alias("work chat", AliasTarget.App("com.telegram"), 1L))
        val unknown = ResolvedCommand.Outcome(CommandOutcome.Unknown("work chat"), null)

        val out = useCase(unknown).resolve("Work Chat")

        assertTrue(out is ResolvedCommand.AutoLaunch)
        out as ResolvedCommand.AutoLaunch
        assertEquals(ResolvedTarget.App("com.telegram"), out.target)
        assertEquals(CommandOutcome.Unknown("work chat"), out.fallback)
    }

    @Test
    fun `unknown with no alias passes through unchanged`() = runTest {
        val unknown = ResolvedCommand.Outcome(CommandOutcome.Unknown("nope"), null)

        val out = useCase(unknown).resolve("nope")

        assertSame(unknown, out)
    }

    @Test
    fun `unknown with alias to uninstalled target passes through`() = runTest {
        store.upsert(Alias("work chat", AliasTarget.App("com.gone"), 1L))
        val unknown = ResolvedCommand.Outcome(CommandOutcome.Unknown("work chat"), null)

        val out = useCase(unknown).resolve("work chat")

        assertSame(unknown, out)
    }

    @Test
    fun `non-unknown outcome passes through untouched (parity)`() = runTest {
        store.upsert(Alias("telegram", AliasTarget.App("com.telegram"), 1L))
        val executed = ResolvedCommand.Outcome(CommandOutcome.Executed, null)

        assertSame(executed, useCase(executed).resolve("telegram"))
    }

    @Test
    fun `inner AutoLaunch passes through untouched`() = runTest {
        val auto = ResolvedCommand.AutoLaunch(
            target = ResolvedTarget.App("com.x"),
            fallback = CommandOutcome.Unknown("x"),
        )

        assertSame(auto, useCase(auto).resolve("x"))
    }
}
