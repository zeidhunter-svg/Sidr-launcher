package com.sidr.launcher.agent

import android.content.Intent
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.IntentLauncher
import com.sidr.launcher.data.repository.agent.PermissionPresence
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.SystemIntentToolWorker
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolWorker
import com.sidr.launcher.data.repository.agent.ToolPermissionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.data.repository.agent.memory.MemoryToolWorker
import com.sidr.launcher.data.repository.agent.shortcut.AppShortcut
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutLauncher
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolWorker
import com.sidr.launcher.di.AgentProvidesModule
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ActionExecutor
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.memory.resolution.DeleteLearnedChoiceUseCase
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolFederation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * The production tool federation, built by calling `AgentProvidesModule.provideToolFederation` itself
 * with the real sources — what the graph reaches is what the provider returns (`DoctrineGuardTest`'s
 * reasoning). Workers are trivial: nothing here invokes a tool. The shortcut source is NON-empty by
 * default, or every reading of it would pass in silence.
 */
internal object ProductionGraph {

    val grantsEverything = PermissionPresence { true }

    fun shortcutSource(
        vararg shortcuts: AppShortcut = arrayOf(
            AppShortcut(packageName = "com.example.chat", shortcutId = "new_message", appLabel = "Example Chat", shortcutLabel = "New message"),
        ),
    ): ShortcutToolSource {
        val catalog = ShortcutCatalog(query = { shortcuts.toList() }, ioDispatcher = Dispatchers.Unconfined)
        runBlocking { catalog.refresh() }
        return ShortcutToolSource(catalog)
    }

    fun federation(shortcuts: ShortcutToolSource = shortcutSource()): ToolFederation =
        AgentProvidesModule.provideToolFederation(
            inAppRegistry = SystemIntentToolSource(DefaultActionCatalog()),
            inAppWorker = SystemIntentToolWorker(
                ExecuteActionUseCase(resolver = IntentActionResolver(NoApps), executor = NoopExecutor),
            ),
            tier0Registry = Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything),
            tier0Worker = Tier0IntentToolWorker(NoopIntentLauncher, ToolPermissionCatalog(), grantsEverything, "com.sidr.launcher"),
            shortcutRegistry = shortcuts,
            shortcutWorker = ShortcutToolWorker(ShortcutLauncher { _, _ -> }),
            memoryRegistry = MemoryToolSource(),
            memoryWorker = FakeAliasStore().let { aliases ->
                MemoryToolWorker(
                    save = SaveAliasUseCase(aliases),
                    deleteAlias = DeleteAliasUseCase(aliases),
                    deleteChoice = DeleteLearnedChoiceUseCase(FakeResolutionPreferenceStore()),
                    aliases = aliases,
                )
            },
        )

    private object NoApps : InstalledAppsRepository {
        override suspend fun getInstalledApps(): OperationResult<List<InstalledApp>> = OperationResult.Success(emptyList())
    }

    private object NoopExecutor : ActionExecutor {
        override suspend fun execute(action: ExecutableAction): ActionExecutionResult = ActionExecutionResult.Success
    }

    private object NoopIntentLauncher : IntentLauncher {
        override fun launch(intent: Intent) = Unit
    }
}
