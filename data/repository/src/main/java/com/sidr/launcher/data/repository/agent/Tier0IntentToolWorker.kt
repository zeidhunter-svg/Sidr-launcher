package com.sidr.launcher.data.repository.agent

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The one place an intent leaves this worker. Injected so the parse can be tested without Android. */
interface IntentLauncher {
    fun launch(intent: Intent)
}

class ContextIntentLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : IntentLauncher {
    override fun launch(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * The `SYSTEM_INTENT` level's worker: two Android intents that are not among the frozen seven
 * `ActionIds`, so they do not travel the `ExecuteActionUseCase` chain and this class is the whole of
 * their execution.
 *
 * **Neither skips the OS's own UI.** `EXTRA_SKIP_UI` stays `false` and settings opens its own screen,
 * so the final act is the user's. That is what makes `SAFE` an honest declaration rather than a
 * convenient one, and it is the "prefilled but not sent" form Master Plan §3.6 `B4` describes.
 */
class Tier0IntentToolWorker @Inject constructor(
    private val launcher: IntentLauncher,
) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = when (invocation.id) {
        Tier0ToolIds.SET_TIMER -> setTimer(invocation.args["duration"].orEmpty())
        Tier0ToolIds.OPEN_SYSTEM_SETTINGS -> {
            launcher.launch(Intent(Settings.ACTION_SETTINGS))
            ToolResult.Effected()
        }
        // Unreachable in a well-formed graph — the federation routes by the registry this adapter
        // declares. Fail-closed and labelled as such, never named in `CommandFailure` (spec §4.4).
        else -> ToolResult.Failed(CommandFailure.Generic)
    }

    private fun setTimer(duration: String): ToolResult {
        val seconds = parseSeconds(duration) ?: return ToolResult.Failed(CommandFailure.Generic)
        launcher.launch(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
        )
        return ToolResult.Effected()
    }

    /**
     * Leading integer plus an optional unit word. Returns `null` for anything it cannot read, and the
     * caller fails closed on that: an agent that silently starts a zero-length timer is worse than one
     * that declines and says so. Same reasoning as `InvocationValidator.resolve`'s refusal to bind a
     * blank.
     *
     * Unit words are read here rather than in `ToolVocabulary` because they belong to reading the
     * **value**, not to recognising the tool; the vocabulary hands this string over verbatim.
     */
    private fun parseSeconds(raw: String): Int? {
        val text = raw.trim().lowercase()
        val digits = text.takeWhile { it.isDigit() }
        val amount = digits.toIntOrNull() ?: return null
        if (amount <= 0 || digits.length > 4) return null
        val unit = text.drop(digits.length).trim()
        val multiplier = when {
            unit.isEmpty() -> MINUTE
            SECOND_FORMS.any { unit.startsWith(it) } -> 1
            MINUTE_FORMS.any { unit.startsWith(it) } -> MINUTE
            HOUR_FORMS.any { unit.startsWith(it) } -> MINUTE * 60
            else -> return null
        }
        return amount * multiplier
    }

    private companion object {
        const val MINUTE = 60
        val SECOND_FORMS = listOf("sec", "сек", "saniye")
        val MINUTE_FORMS = listOf("min", "мин", "dakika", "dk")
        val HOUR_FORMS = listOf("hour", "час", "saat")
    }
}
