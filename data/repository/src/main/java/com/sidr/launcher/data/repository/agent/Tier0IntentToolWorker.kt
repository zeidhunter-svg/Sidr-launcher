package com.sidr.launcher.data.repository.agent

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Named

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

/**
 * The qualifier under which the composition root supplies **our own** package name.
 *
 * It is a shared constant rather than a string spelled twice because the two halves live in different
 * modules — the consumer is [Tier0IntentToolWorker] here in `:data:repository`, the provider is
 * `AgentProvidesModule` in `:app`, which is the only module that can reach a `Context`. Injecting the
 * name instead of reading `context.packageName` inside the worker is what makes the self-uninstall
 * refusal assertable in a plain unit test rather than behind Robolectric.
 */
const val APP_PACKAGE_NAME = "appPackageName"

class ContextIntentLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : IntentLauncher {
    override fun launch(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * **This action string is OURS, not the platform's** (A1″ Phase 3b, Task 4, finding P1).
 *
 * Row 25 of the device-measurement file records that `javap` over
 * `platforms/android-37.0/android.jar` finds **no** `Settings.ACTION_NOTIFICATION_SETTINGS` field:
 * `ACTION_APP_NOTIFICATION_SETTINGS` and `ACTION_NOTIFICATION_LISTENER_SETTINGS` exist, the bare one
 * does not. The screen is reachable only through a hardcoded action string, so this constant is a
 * claim about the platform that nothing on any classpath verifies — unlike every other action this
 * worker sends, which is a real `Settings`/`AlarmClock`/`Intent` field the compiler resolves. It is
 * named `ACTION_NOTIFICATION_SETTINGS` so the constant reads at the call site exactly like its
 * platform-owned neighbours; this comment is the only thing that says it is not one, which is why it
 * must not be deleted.
 *
 * It is also **why the string is bound to a name instead of written inline**:
 * `ToolPermissionManifestGuardTest`'s parse-completeness assertion counts every `Intent(` and requires
 * its action regex to have accounted for all of them, and that regex's capture group is
 * `[A-Za-z_][A-Za-z0-9_.]*` — which a quoted literal does not match. `Intent("android.settings.…")`
 * would therefore turn that guard RED on a count mismatch. That is the guard working as designed
 * ("teaching the scan one real shape at a time"), and the answer is this constant, **not** a wider
 * regex.
 */
private const val ACTION_NOTIFICATION_SETTINGS = "android.settings.NOTIFICATION_SETTINGS"

/**
 * The `SYSTEM_INTENT` level's worker: the Android intents that are not among the frozen seven
 * `ActionIds`, so they do not travel the `ExecuteActionUseCase` chain and this class is the whole of
 * their execution. Most are `SAFE`; `uninstall_app` (Task 7) is the track's first `CONFIRM` tool, and
 * the consent gate — not anything in this file — is what stops the loop before it runs. (Neither the
 * tools nor the safe ones are counted here: this KDoc said "four" and "three" until A1″ Phase 3b, and
 * a count in prose beside a `when` that grows is a claim nobody re-reads — the same correction
 * `Tier0IntentToolSource`'s KDoc already carries.)
 *
 * **`EXTRA_SKIP_UI = false` is not "prefilled but not sent" — corrected 2026-09-10.** This KDoc used
 * to read "neither skips the OS's own UI, so the final act is the user's", and to offer that as the
 * ground for `SAFE` and as Master Plan §3.6 `B4`'s form. Driven on the SM-A325F 2026-09-05 the Samsung
 * clock opened *with the timer already counting down* (`Пауза`/`Удалить`, no start button): the flag
 * governs whether the responding app shows **its UI**, not whether it **acts**. It is still set
 * `false` — a timer the user cannot see start would be worse — but what it buys is visibility, not
 * consent. `SAFE` stands by owner decision on other grounds (reversible, immediately visible,
 * provenance disclosed, nothing leaves the device); see [Tier0IntentToolSource]'s KDoc, which carries
 * the decision. `open_system_settings` is untouched by any of this: opening a settings screen performs
 * nothing, so for it there was never an act to complete.
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
 * **The Task 3 precondition does not replace that catch, and could not** (A1" Phase 3a). `invoke`
 * re-checks the tool's permissions against [PermissionPresence] immediately before dispatch. The two
 * answer different questions — "may this process do it" versus "did the call itself blow up" — and
 * **which of them can see a refusal at all depends on where the refusal is enforced.** Row 32 draws
 * that distinction in its own cell and forbids collapsing the two into one mechanism; spec §7.7 keeps
 * them apart for the same reason.
 *
 * *Framework-side* enforcement is this worker's own alarm-permission family: `ActivityTaskManager`
 * rejects the intent **before** dispatch and **throws `SecurityException` at the caller** (row 27,
 * measured on `ACTION_SET_ALARM` with the manifest line removed; A1''s device acceptance saw the same
 * `Permission Denial … requires com.android.alarm.permission.SET_ALARM` for this worker's own
 * `ACTION_SET_TIMER`). So for `set_timer` the catch below **already** produces [ToolResult.Failed] on
 * its own, and the precondition is the earlier, cheaper and more specific of two working signals —
 * not the only one.
 *
 * *Responder-side* enforcement is the opposite mode, and since Task 7 it is no longer hypothetical —
 * `uninstall_app` is in this `when`: the responding app refuses **silently**, `startActivity` returns
 * normally and throws nothing, and the caller's answer is byte-identical to the success case (rows
 * 16/28/32, measured on `ACTION_DELETE` itself; the refusal appears only in the responder's own
 * logcat, which another app cannot read). There **neither** catch fires in either direction, and the
 * precondition is the **only** signal there is: without it a refused step would come back exactly as
 * a dispatched one does — since A4′ phase 0 (Task 5) that is [ToolResult.HandedOff], "handed to the
 * system, outcome unknown", where it used to be [ToolResult.Effected]. The trace would no longer
 * claim a removal, but it would still record a failure we can see before the call as an outcome we
 * cannot see, in a trace `DOC-ILM-3` requires to be 1:1 with reality; with the check it is the
 * [ToolResult.Failed] it really is. Said the way it must always be said: the precondition is
 * **checked before the call**, and a refusal is never *detected* afterwards — and it closes one cause
 * of refusal only, a missing permission.
 *
 * Neither may therefore be removed on the strength of the other, and the catch additionally covers a
 * state that is no permission at all — `ActivityNotFoundException` on a device with no clock app,
 * which is the crash the final A1' review fixed.
 *
 * The failure is [com.sidr.launcher.domain.intent.CommandFailure.Generic], the same value every other
 * fail-closed path here already uses. A dedicated variant is deliberately not minted: the A1' ADR
 * records a richer per-tool failure vocabulary as rejected with a measured reason, and a new
 * `CommandFailure` is a closed-sum widening in `commonMain` plus three locale strings for a state the
 * user can do nothing about.
 */
class Tier0IntentToolWorker @Inject constructor(
    private val launcher: IntentLauncher,
    private val catalog: ToolPermissionCatalog,
    private val presence: PermissionPresence,
    @Named(APP_PACKAGE_NAME) private val ownPackageName: String,
) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
        // The source already withheld this tool if its permission was absent (Task 2). This is the
        // narrower window that check cannot cover — state can move between the snapshot and the call.
        //
        // It is a *precondition* and it replaces nothing; the class KDoc has the full reason, which
        // turns on WHERE a refusal is enforced. Short form: for this worker's alarm-permission family
        // the framework refuses before dispatch and throws, so the SecurityException catch below would
        // answer too (row 27) and this check is the earlier of two working signals; for a
        // responder-side refusal nothing whatever is observable afterwards (row 32) and this check is
        // the only signal there is. A missing catalog row fails closed for the reason the catalog's own
        // KDoc gives: "nobody stated an answer" must never read as "needs nothing".
        val needed = catalog.permissionsFor(invocation.id) ?: return ToolResult.Failed(CommandFailure.Generic)
        if (!needed.all(presence::isGranted)) return ToolResult.Failed(CommandFailure.Generic)

        return when (invocation.id) {
            Tier0ToolIds.SET_TIMER -> setTimer(invocation.args["duration"].orEmpty())
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS -> launch(Intent(Settings.ACTION_SETTINGS))
            Tier0ToolIds.SET_ALARM -> setAlarm(invocation.args["time"].orEmpty())
            Tier0ToolIds.UNINSTALL_APP -> uninstallApp(invocation.args["app"].orEmpty())
            // A1″ Phase 3b, Task 2 (Slice A). Each `Intent(...)` is built INLINE in its own arm
            // (finding P9): `ToolPermissionManifestGuardTest` admits a file only when it both
            // declares `: ToolWorker` and contains `Intent(` itself, so a shared intent-building
            // helper would take every intent it built out from under that guard's totality check
            // entirely. No such helper exists here or anywhere in this file.
            Tier0ToolIds.SHOW_ALARMS -> launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))
            Tier0ToolIds.OPEN_CAMERA -> launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            Tier0ToolIds.OPEN_WIFI_SETTINGS -> launch(Intent(Settings.ACTION_WIFI_SETTINGS))
            Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS -> launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            // A1″ Phase 3b, Task 3 (Slice B). Same inline-per-arm shape as Slice A (P9); no helper.
            // Row 20, finding P4: `Intent.ACTION_POWER_USAGE_SUMMARY` resolves to Samsung Device Care
            // on the measured device, not `com.android.settings` — a ROM-dependent premise, named on
            // the descriptor, not fixed here; `launch()` already fails closed on `ActivityNotFoundException`.
            Tier0ToolIds.OPEN_BATTERY_SETTINGS -> launch(Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS -> launch(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
            Tier0ToolIds.OPEN_DISPLAY_SETTINGS -> launch(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            Tier0ToolIds.OPEN_SOUND_SETTINGS -> launch(Intent(Settings.ACTION_SOUND_SETTINGS))
            // A1″ Phase 3b, Task 4 (Slice C). Same inline-per-arm shape (P9); no helper, no new file.
            Tier0ToolIds.OPEN_LOCATION_SETTINGS -> launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            // Finding P1: `ACTION_NOTIFICATION_SETTINGS` is the file-level constant above, NOT a
            // platform field — read its KDoc before touching this line.
            Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS -> launch(Intent(ACTION_NOTIFICATION_SETTINGS))
            Tier0ToolIds.OPEN_APP_INFO -> openAppInfo(invocation.args["app"].orEmpty())
            // Unreachable in a well-formed graph — the federation routes by the registry this adapter
            // declares. Fail-closed and labelled as such, never named in `CommandFailure` (spec §4.4).
            // The precondition above does NOT subsume this arm: `ToolIds.LAUNCH_APP` and
            // `PLAY_STORE_SEARCH` carry `emptyList()` rows in the same catalog and are not in this
            // `when`, so a misrouted in-app id passes the permission check and lands here.
            else -> ToolResult.Failed(CommandFailure.Generic)
        }
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
     * Task 5. `EXTRA_SKIP_UI = false` is not "prefilled but not sent" here either — [Tier0IntentToolSource]'s
     * KDoc on the `SET_ALARM` descriptor carries the measurement (row 13/29: `ACTION_SET_ALARM` creates
     * the alarm already enabled, so the flag governs the responder's own UI, not consent).
     */
    private fun setAlarm(raw: String): ToolResult {
        val at = parseClockTime(raw) ?: return ToolResult.Failed(CommandFailure.Generic)
        return launch(
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, at.first)
                .putExtra(AlarmClock.EXTRA_MINUTES, at.second)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
        )
    }

    /**
     * Task 7 — the track's first `CONFIRM` tool, and the only one so far whose effect cannot be undone.
     *
     * **This worker resolves nothing.** [target] is **already a package name**: `ToolMatchPlanner`
     * resolved it at plan time, above the consent checkpoint, which is what lets the consent card name
     * the package that will actually be removed. `AgentExecutor` evaluates `checkpointFor` before it
     * ever reaches `toolExecutor.invoke`, so a name resolved *here* would be resolved **after** the
     * user said yes — on a device carrying two Telegram-like labels the user would confirm one thing
     * and get another. Injecting a resolver into this class would reintroduce exactly that defect.
     *
     * Two refusals, both before the intent is built:
     *  - a **blank** target, for the same fail-closed reason `parseSeconds` and `parseClockTime`
     *    decline what they cannot read: `package:` names no package, so the call cannot remove
     *    anything and that is known before it is made. Without this check, a dispatch that returned
     *    normally would come back as this arm's success, [ToolResult.HandedOff] (since A4′ phase 0,
     *    Task 5 — [ToolResult.Effected] before it): "outcome unknown" for an outcome already known,
     *    where the answer `DOC-ILM-3` requires is [ToolResult.Failed]. It is a second line of defence — `ToolMatchPlanner` answers `NoPlan` when resolution fails, so a
     *    blank should never arrive — and second lines are kept, not argued away;
     *  - **our own package** (owner condition 4, 2026-09-18): uninstalling Sidr mid-session kills the
     *    surface the session is running on. [ownPackageName] is injected rather than read from a
     *    `Context` here, so this is an ordinary equality a unit test can state.
     *
     * **What the permission precondition above does and does not buy, in this tool's own terms.** This
     * is the responder-side family rows 16/28/32 measured: without
     * `android.permission.REQUEST_DELETE_PACKAGES` the uninstaller starts and dies in ~190 ms drawing
     * nothing, and `startActivity` **returns normally and throws nothing** — byte-identical to the
     * success case. So the precondition is **checked before the call**; a refusal is never *detected*
     * afterwards. It closes exactly one cause of refusal — a missing permission. Device policy, a work
     * profile and a non-removable package remain undetectable, because the platform gives the caller
     * nothing to read (spec §7.7).
     *
     * `Uri.fromParts` rather than `Uri.parse`: it is the shape `Tier0IntentProbe` fired on the
     * SM-A325F when those rows were measured, so what ships is what was measured, and it builds the
     * opaque URI without parsing a string the caller assembled.
     */
    private fun uninstallApp(target: String): ToolResult {
        if (target.isBlank()) return ToolResult.Failed(CommandFailure.Generic)
        if (target == ownPackageName) return ToolResult.Failed(CommandFailure.Generic)

        // The ONE arm of this worker where `Effected` was a lie (A1″ acceptance finding (a)).
        // `launch` is not changed and must not be: the eleven navigating tools really do open their
        // screen, and `set_alarm`/`set_timer` really do create the alarm (rows 13/29) — for them
        // `startActivity` returning IS the effect. Here it is only the dialog being raised, and the
        // caller learns nothing about what the user then did (rows 16/28/32: the return is
        // byte-identical for confirm, cancel and a silent responder refusal).
        //
        // Only the SUCCESS branch is converted. A `Failed` from `launch` stays `Failed`: a dialog
        // that never appeared is a failure we CAN see, and blurring it into "handed off" would give
        // back, in the other direction, exactly the honesty this change buys.
        return when (val dispatched = launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", target, null)))) {
            is ToolResult.Effected -> ToolResult.HandedOff()
            else -> dispatched
        }
    }

    /**
     * A1″ Phase 3b, Task 4 — the only argument-carrying tool of the eleven navigating ones.
     *
     * **This is a per-tool private function, not the shared intent helper finding P9 forbids.** P9's
     * measured risk is an `Intent(…)` built in a file that is not a `ToolWorker`:
     * `ToolPermissionManifestGuardTest` admits a file only when it both declares `: ToolWorker` **and**
     * contains `Intent(`, and a `call_number` probe whose worker delegated construction to a plain
     * helper object went **green 4/4** with `CALL_PHONE` absent from the manifest. The `Intent(…)`
     * below is in this file, which declares the worker, so the scan reads it exactly as it reads the
     * inline arms — the same reason [uninstallApp], [setTimer] and [setAlarm] are allowed to be
     * functions. It is one because it needs a statement before the launch, like [uninstallApp]:
     *
     *  - **a blank target is refused**, for the fail-closed reason [uninstallApp] gives — `package:`
     *    names no package, and answering [ToolResult.Effected] for an effect that cannot have happened
     *    is what `DOC-ILM-3` forbids. Second line of defence: `ToolMatchPlanner` answers `NoPlan` when
     *    resolution fails, so a blank should never arrive.
     *
     * **Our own package is deliberately NOT refused here, unlike [uninstallApp]** — and the asymmetry
     * is the reason, not an oversight. That refusal exists because uninstalling Sidr mid-session kills
     * the surface the session is running on. Opening Sidr's own app-info screen does nothing of the
     * kind, and row 15 run 1 measured exactly that call succeeding. Adding the refusal here would
     * remove a capability for a danger that was measured not to exist.
     *
     * [target] is **already a package name** for the same reason [uninstallApp]'s is: `ToolMatchPlanner`
     * resolved it at plan time. This worker resolves nothing.
     */
    private fun openAppInfo(target: String): ToolResult {
        if (target.isBlank()) return ToolResult.Failed(CommandFailure.Generic)
        return launch(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", target, null)),
        )
    }

    /**
     * `H:MM` or `HH:MM` only — a bounded token (spec §7.1), never a guess, never a default. The
     * vocabulary hands this string over unparsed, exactly like [parseSeconds]'s `duration`; there is no
     * free-text reading to normalize away here, only a fixed clock-time shape to accept or decline.
     */
    private fun parseClockTime(raw: String): Pair<Int, Int>? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").find(raw.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    /**
     * The only place this worker touches the world, and the only place it can fail from the world's
     * side. **Every** tool in the `when` above goes through it, so the catch cannot be forgotten by
     * whoever adds the next one. (It said "all four … whoever adds a fifth" until A1″ Phase 3b, which
     * was false from the moment the fifth landed — the property is "every", and it does not need a
     * number to be checkable.)
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
