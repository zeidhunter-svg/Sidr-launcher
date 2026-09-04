package com.sidr.launcher.data.repository.agent

import android.content.ActivityNotFoundException
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

/**
 * The one place an intent leaves this worker. Injected so the parse can be tested without Android.
 *
 * **`launch` may throw, and that is deliberate** (final whole-branch review, finding 1). It is the raw
 * `startActivity` seam: on a device with no activity registered for the intent — an AOSP or ROM build,
 * or Deskclock disabled — it raises `ActivityNotFoundException`, and a restricted caller raises
 * `SecurityException`. [Tier0IntentToolWorker] catches both; see its KDoc for why the catch is the
 * worker's rather than this seam's.
 */
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
 *
 * **The launch is caught HERE, not in [ContextIntentLauncher]** (final whole-branch review, finding 1,
 * CRITICAL). `startActivity` was called bare and nothing above it catches — `AgentExecutor.perform`'s
 * one call site has no `try`, neither does `RunAgentSessionUseCase.run`, and `LauncherAgentSession`
 * runs on `viewModelScope` with no `CoroutineExceptionHandler` — so an `ActivityNotFoundException`
 * from a device with no clock app killed the home-screen process. That breaks the hard rule that a
 * repository/use-case operation never throws to UI, and it was a regression against this repo's own
 * `AndroidActionExecutor`, which catches the same two exceptions at every one of its `startActivity`
 * call sites.
 *
 * The contract made load-bearing is **[ToolWorker]'s: an invocation always yields a [ToolResult]** —
 * not the seam's "launching never throws". Catching inside [ContextIntentLauncher] would make a
 * swallowed failure indistinguishable from a success, because `launch` returns `Unit`: this worker
 * would answer [ToolResult.Effected] for an intent that never left, and `AgentExecutor` would record
 * `ToolObserved(Effected)` in a trace that must be 1:1 with reality (`DOC-ILM-3`). A trace that lies is
 * worse than one that stops. Keeping the seam's contract would therefore mean giving it a *reported*
 * outcome rather than a swallowed one — a different return type, which buys nothing the worker's own
 * catch does not already give. It also puts the catch in the object that owns the result type, exactly
 * where `AndroidActionExecutor` puts its own, and covers every intent this worker issues and every
 * [IntentLauncher] implementation rather than one of each.
 *
 * The failure is [com.sidr.launcher.domain.intent.CommandFailure.Generic], the same value every other
 * fail-closed path here already uses. A dedicated variant is deliberately not minted: the A1' ADR
 * records a richer per-tool failure vocabulary as rejected with a measured reason, and a new
 * `CommandFailure` is a closed-sum widening in `commonMain` plus three locale strings for a state the
 * user can do nothing about.
 */
class Tier0IntentToolWorker @Inject constructor(
    private val launcher: IntentLauncher,
) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = when (invocation.id) {
        Tier0ToolIds.SET_TIMER -> setTimer(invocation.args["duration"].orEmpty())
        Tier0ToolIds.OPEN_SYSTEM_SETTINGS -> launch(Intent(Settings.ACTION_SETTINGS))
        // Unreachable in a well-formed graph — the federation routes by the registry this adapter
        // declares. Fail-closed and labelled as such, never named in `CommandFailure` (spec §4.4).
        else -> ToolResult.Failed(CommandFailure.Generic)
    }

    private fun setTimer(duration: String): ToolResult {
        val seconds = parseSeconds(duration) ?: return ToolResult.Failed(CommandFailure.Generic)
        return launch(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
        )
    }

    /**
     * The only place this worker touches the world, and the only place it can fail from the world's
     * side. Both tools go through it, so the catch cannot be forgotten by whoever adds a third.
     *
     * The exception set is `AndroidActionExecutor`'s, deliberately: the two world-facing paths of this
     * repo should behave alike rather than each inventing its own — `ActivityNotFoundException` for
     * "nothing on this device handles it", `SecurityException` for "you may not". Nothing broader is
     * caught: a `RuntimeException` net here would swallow programming errors into a `Failed` step and
     * hide them from every test.
     */
    private fun launch(intent: Intent): ToolResult = try {
        launcher.launch(intent)
        ToolResult.Effected()
    } catch (e: ActivityNotFoundException) {
        ToolResult.Failed(CommandFailure.Generic)
    } catch (e: SecurityException) {
        ToolResult.Failed(CommandFailure.Generic)
    }

    /**
     * Leading integer plus an optional unit word. Returns `null` for anything it cannot read, and the
     * caller fails closed on that: an agent that silently starts a zero-length timer is worse than one
     * that declines and says so. Same reasoning as `InvocationValidator.resolve`'s refusal to bind a
     * blank.
     *
     * **The unit token is matched exactly, never by prefix** (fix round 1, controller ruling R11). A
     * `startsWith` match let `"1 min 30 sec"` silently read as 60 seconds instead of 90, and let
     * `"10 minecraft"` silently start a real 600-second timer from nonsense — the worse of the two
     * failures this KDoc already names, because it does not decline, it lies. The token left after the
     * digits must therefore be a single word — any whitespace or digit in it fails closed rather than
     * being read as "the first word matched" — and that word is compared with `==` against an explicit,
     * intentionally narrow list per unit. An unlisted-but-valid inflection is declined, not guessed;
     * widening the vocabulary is Task 8's job (`ToolMatchPlanner`), not this worker's.
     *
     * Unit words are read here rather than in `ToolVocabulary` because they belong to reading the
     * **value**, not to recognising the tool; the vocabulary hands this string over **unparsed** — it
     * never reads the value for meaning. Not *verbatim*, though: since Task 8 the vocabulary matches on
     * `CommandNormalizer`-normalized text, so what arrives here is already lower-cased and
     * whitespace-collapsed. Harmless for a duration — the trim/lowercase below is then redundant rather
     * than wrong — but see `ToolVocabulary.Entry` for why a free-text argument will need the raw span.
     */
    private fun parseSeconds(raw: String): Int? {
        val text = raw.trim().lowercase()
        val digits = text.takeWhile { it.isDigit() }
        val amount = digits.toIntOrNull() ?: return null
        if (amount <= 0 || digits.length > 4) return null
        val unit = text.drop(digits.length).trim()
        val multiplier = when {
            unit.isEmpty() -> MINUTE
            unit.any { it.isWhitespace() || it.isDigit() } -> return null
            unit in SECOND_FORMS -> 1
            unit in MINUTE_FORMS -> MINUTE
            unit in HOUR_FORMS -> MINUTE * 60
            else -> return null
        }
        return amount * multiplier
    }

    private companion object {
        const val MINUTE = 60
        val SECOND_FORMS = setOf("sec", "secs", "second", "seconds", "сек", "секунда", "секунды", "секунд", "saniye")
        val MINUTE_FORMS = setOf("min", "mins", "minute", "minutes", "мин", "минута", "минуты", "минут", "dakika", "dk")
        val HOUR_FORMS = setOf("hour", "hours", "hr", "hrs", "час", "часа", "часов", "saat")
    }
}
