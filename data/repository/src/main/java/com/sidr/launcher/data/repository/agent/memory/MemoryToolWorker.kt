package com.sidr.launcher.data.repository.agent.memory

import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.MAX_ALIAS_PHRASE_LENGTH
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.DeleteLearnedChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * The `launcher_memory` level's worker: three thin adapters over `SaveAliasUseCase`,
 * `DeleteAliasUseCase` and `DeleteLearnedChoiceUseCase` (spec §7.6).
 *
 * **This worker resolves NOTHING (controller ruling R14-17).** `invocation.args["app"]` on
 * `set_app_alias` already arrives as a package — `ToolMatchPlanner` resolves it at plan time, above the
 * consent checkpoint, the same shape `Tier0IntentToolWorker.uninstallApp` uses and for the identical
 * reason: a name resolved *here* would be resolved after any consent already given, and a worker handed
 * an already-resolved package that tried to re-resolve it as a label would simply fail. `app_label`
 * carries what the user actually typed, for the surface to render — this worker never reads it back.
 *
 * **It catches broadly, and the reason is asymmetric across its three use cases, not defensive
 * boilerplate.** `SaveAliasUseCase.save` and `DeleteAliasUseCase.delete` already map every exception to
 * [OperationResult.Failure] internally (both rethrow [CancellationException] first) — true of two of the
 * three. `DeleteLearnedChoiceUseCase.delete` is `suspend fun delete(key, context) = store.delete(key,
 * context)` with **no `try` of its own**, so a throwing `ResolutionPreferenceStore` would otherwise
 * escape into `AgentExecutor.perform`'s one un-`try`ed `toolExecutor.invoke` call site — the exact crash
 * class the final A1′ review fixed once already for `Tier0IntentToolWorker`. This worker's own outer
 * catch is therefore the net the third use case does not carry itself; it is harmless for the other two,
 * whose own catches never let anything through it would otherwise see.
 *
 * **Two things this worker must do that `SaveAliasUseCase` does not** (review finding I6):
 * `SaveAliasUseCase.save` answers `Success(Unit)` **without writing** for a blank or over-length phrase
 * (`MAX_ALIAS_PHRASE_LENGTH` = 64), so passing that straight through as [ToolResult.Effected] would put
 * a success marker in the trace for an alias that was never stored — the exact class of lie this whole
 * block exists to remove. This worker therefore rejects a blank or over-length phrase itself, before
 * calling the use case, and likewise rejects a blank resolved package.
 *
 * **`Effected` for `forget_*` means the operation RAN, not that a row was removed.**
 * `AliasStore.delete`/`ResolutionPreferenceStore.delete` report no row count, so "there was nothing to
 * remove" and "one row was removed" are indistinguishable from here, and this worker must never imply
 * the stronger claim.
 */
class MemoryToolWorker @Inject constructor(
    private val save: SaveAliasUseCase,
    private val deleteAlias: DeleteAliasUseCase,
    private val deleteChoice: DeleteLearnedChoiceUseCase,
) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = try {
        when (invocation.id) {
            MemoryToolIds.SET_APP_ALIAS -> setAppAlias(invocation.args)
            MemoryToolIds.FORGET_APP_ALIAS -> forgetAppAlias(invocation.args)
            MemoryToolIds.FORGET_LEARNED_CHOICE -> forgetLearnedChoice(invocation.args)
            // Unreachable in a well-formed graph — the federation routes by the registry this adapter
            // declares. Fail-closed rather than named in CommandFailure (spec §4.4), same as the other
            // adapters' own catch-all arm.
            else -> ToolResult.Failed(CommandFailure.Generic)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ToolResult.Failed(CommandFailure.Generic)
    }

    private suspend fun setAppAlias(args: Map<String, String>): ToolResult {
        val app = args["app"].orEmpty()
        val phrase = args["phrase"].orEmpty()
        if (app.isBlank()) return ToolResult.Failed(CommandFailure.Generic)
        if (phrase.isBlank() || phrase.length > MAX_ALIAS_PHRASE_LENGTH) {
            return ToolResult.Failed(CommandFailure.Generic)
        }
        return when (save.save(phrase, AliasTarget.App(app))) {
            is OperationResult.Success -> ToolResult.Effected()
            is OperationResult.Failure -> ToolResult.Failed(CommandFailure.Generic)
        }
    }

    private suspend fun forgetAppAlias(args: Map<String, String>): ToolResult {
        val phrase = args["phrase"].orEmpty()
        return when (deleteAlias.delete(phrase)) {
            is OperationResult.Success -> ToolResult.Effected()
            is OperationResult.Failure -> ToolResult.Failed(CommandFailure.Generic)
        }
    }

    /**
     * `CapabilityKey(ActionIds.LAUNCH_APP, phrase)` / `ResolutionContext.None` — v1's only learning
     * family and its only context value (spec §7.6). A learned resolution is never used to resolve a
     * package name for this tool: this binds the phrase the user gave for *forgetting*, it does not
     * read or reuse whatever preference was actually stored under that key.
     */
    private suspend fun forgetLearnedChoice(args: Map<String, String>): ToolResult {
        val phrase = args["phrase"].orEmpty()
        val key = CapabilityKey(ActionIds.LAUNCH_APP, phrase)
        return when (deleteChoice.delete(key, ResolutionContext.None)) {
            is OperationResult.Success -> ToolResult.Effected()
            is OperationResult.Failure -> ToolResult.Failed(CommandFailure.Generic)
        }
    }
}
