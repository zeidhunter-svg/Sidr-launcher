package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/** The four Tier-0 ids. Not projections of `ActionIds` — those seven are frozen and contain none of them. */
object Tier0ToolIds {
    val SET_TIMER = ToolId("set_timer")
    val OPEN_SYSTEM_SETTINGS = ToolId("open_system_settings")

    /** Task 5 (A1″ Phase 3a). See [Tier0IntentToolSource]'s KDoc for what `SAFE` rests on here. */
    val SET_ALARM = ToolId("set_alarm")

    /** Task 7 (A1″ Phase 3a): the track's first `CONFIRM` tool. See its descriptor for the conditions. */
    val UNINSTALL_APP = ToolId("uninstall_app")
}

/**
 * A1′'s second source: Android system intents that are **not** among the frozen seven `ActionIds`, so
 * they mint their own ids (spec §5.1 — identity C binds projections only).
 *
 * Every tool here is `EXTERNAL`. Three of the four are `SAFE`, and that combination is the one
 * `DOC-ILM-2` is actually about: a `CONFIRM` tool is already stopped by the consent gate, so
 * provenance is the only mechanism telling the user where a `SAFE` effect went. Since Task 7 the
 * source also holds the other kind — `uninstall_app`, the track's first `CONFIRM` and first `DURABLE`
 * tool — so "all Tier-0 tools are safe" is no longer true of this file and must not be re-asserted
 * anywhere as a property of the level.
 *
 * **Permissions.** `ACTION_SETTINGS` needs none. `ACTION_SET_TIMER` and `ACTION_SET_ALARM` need
 * `com.android.alarm.permission.SET_ALARM` and `ACTION_DELETE` needs
 * `android.permission.REQUEST_DELETE_PACKAGES`, all declared in `app/src/main/AndroidManifest.xml` —
 * install-time (`protectionLevel: normal`) permissions, so they are granted without a prompt and need
 * no runtime request or education flow.
 *
 * **What `SET_TIMER`'s `SAFE` actually rests on — corrected 2026-09-10 by owner decision.** Until
 * that date this KDoc, the spec and the plan all justified `SAFE` with *"neither skips the OS's own UI
 * — the 'prefilled but not sent' shape leaves the final act with the user"*. **That reason was
 * measured false and is withdrawn** (evidence below). The owner re-decided the level rather than
 * letting an unsupported sentence stand: **`SAFE` stays, the reasoning is replaced.** What it rests on
 * now is four properties that were observed rather than assumed — the effect is **trivially
 * reversible** (one tap: `Удалить` on the timer that just started), **immediately visible** (the
 * clock opens in front of the user — nothing happens silently or in the background), **disclosed**
 * (`EXTERNAL` draws a provenance line under the step, which is the only such mechanism a `SAFE` tool
 * gets, since the consent gate never fires for one), and **local** (the invocation and its argument
 * never leave the device). `open_system_settings` is a *different* case and must not be folded into
 * this one: opening a settings screen performs no act at all, so it has nothing to reverse and "the
 * final act is the user's" is literally true of it.
 *
 * **The measurement.** `EXTRA_SKIP_UI = false` was read as Master Plan §3.6 `B4`'s "prefilled but not
 * sent" form. Driven on the SM-A325F 2026-09-05, the Samsung clock opened *with the timer already
 * counting down* — `Пауза`/`Удалить`, not a start button. The flag governs whether the responding
 * app shows its UI, not whether it performs the act. So `B4`'s shape does not describe `set_timer`;
 * whether it describes the Tier-0 intents A1″ adds next (`SENDTO`, calendar `INSERT`, `DIAL`) is a
 * thing for that block to **measure on a device**, not to inherit from this one.
 *
 * This KDoc read "Zero new permissions: `ACTION_SET_TIMER` and `ACTION_SETTINGS` both need none" until
 * 2026-09-05, and that was simply false. It shipped through the whole block — spec, plan, commit
 * message, `CLAUDE.md`, status — and cost the owner's device acceptance: every `set_timer` invocation
 * died at `ActivityTaskManager`'s `Permission Denial … requires com.android.alarm.permission.SET_ALARM`,
 * so a registered, reachable, `SAFE` tool could never once run. No test could see it —
 * `Tier0IntentToolWorkerTest` injects a fake `IntentLauncher`, so the real `startActivity` is never
 * reached, and a manifest omission has no unit-test signature. It is now held by
 * `ToolPermissionManifestGuardTest` (`:app`), which fails when any intent a registered tool issues
 * needs a permission the manifest does not declare.
 */
class Tier0IntentToolSource @Inject constructor(
    private val catalog: ToolPermissionCatalog,
    private val presence: PermissionPresence,
) : ToolRegistry {

    private val descriptors = listOf(
        ToolDescriptor(
            id = Tier0ToolIds.SET_TIMER,
            argSchema = listOf(
                ActionArg("duration", description = "How long the timer should run, e.g. 10 minutes"),
            ),
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Task 5. Same permission family as SET_TIMER (com.android.alarm.permission.SET_ALARM, row
        // 27), a different intent and a different argument shape (a clock time, not a duration).
        //
        // **What SAFE rests on here, stated so it cannot later be "simplified" away.** Row 13/29
        // measured `ACTION_SET_ALARM` on the SM-A325F: it creates the alarm **already enabled** — there
        // is no prefilled form, and EXTRA_SKIP_UI yields no consent step from the OS either way. So
        // this is NOT the "prefilled but not sent" shape (Master Plan §3.6 B4) — that reading was
        // measured false for set_timer's own ACTION_SET_TIMER and the same measurement applies here.
        // SAFE rests on the same four properties the owner accepted for set_timer instead: the effect
        // is reversible (one tap deletes the alarm that was just created), immediately visible (the
        // clock app opens showing it), disclosed (EXTERNAL provenance, this tool's only consent-gate
        // substitute since a SAFE tool never reaches the gate), and local (the invocation and its
        // argument never leave the device).
        ToolDescriptor(
            id = Tier0ToolIds.SET_ALARM,
            argSchema = listOf(
                ActionArg("time", description = "Clock time for the alarm, e.g. 7:30"),
            ),
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Task 7 — **the first CONFIRM tool of the whole track, and the first DURABLE one.** Every
        // other tool shipped so far is SAFE, so the consent gate has never once fired on a real path.
        //
        // **Why CONFIRM rather than the four properties that carried SAFE for the alarm family.** Not
        // one of them holds here. The effect is not reversible — a removed app and its data do not
        // come back with one tap. It is not merely visible-after-the-fact: it has to be stopped
        // *before* it happens, which is what a gate is. And the target is the product of a **fuzzy**
        // resolution (`AppTargetResolver`), so the one thing the user must be able to check is which
        // package this will actually remove — which is why the descriptor declares two arguments and
        // resolution happens above the gate rather than inside the worker.
        //
        // **`app_label` is `required = false`, and that is not tidiness.** `ToolMatchPlanner` runs its
        // required-argument check BEFORE the resolution block that binds `app_label`; the vocabulary
        // never supplies that argument, the planner adds it one step later. A descriptor declaring it
        // required would answer `NoPlan` for every goal forever, and this tool would ship registered,
        // reachable, matching its trigger and dead on every invocation with the whole suite green.
        // `ActionArg.required` defaults to `true`, so this has to be — and stays — written out.
        //
        // **What the six owner conditions (2026-09-18) rest on, per condition:** (1) explicit user
        // command only — there is no suggestion path into the planner; (2) the card names label AND
        // package — **held, closed by Task 11**: the two arguments above are bound by `ToolMatchPlanner`
        // above the checkpoint, and `AgentSessionPresentation.line()` now reads `app` and `app_label` BY
        // NAME for this tool id (a dedicated branch, not the `singleOrNull()` single-literal path, which
        // cannot render two values), so the consent card reads e.g. "Remove telegram
        // (org.telegram.messenger)" — both what the user said and what will actually be removed;
        // (3) exact resolution or decline — `AppTargetResolver` returns `null` to decline and the
        // planner turns that into `NoPlan`, so no card is ever drawn for an unresolved name;
        // (4) our own package is refused — `Tier0IntentToolWorker.uninstallApp`; (5) the wording says
        // the precondition is CHECKED BEFORE THE CALL and never that a refusal is DETECTED (rows
        // 16/28/32: `startActivity` returns normally and throws nothing whether the uninstaller
        // refuses or draws its dialog); (6) the manifest line is held by `ToolPermissionManifestGuardTest`.
        ToolDescriptor(
            id = Tier0ToolIds.UNINSTALL_APP,
            argSchema = listOf(
                ActionArg("app", description = "Package of the app to remove"),
                ActionArg("app_label", required = false, description = "What the user called it"),
            ),
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.CONFIRM,
            durability = ToolDurability.DURABLE,
        ),
    )

    /**
     * **A source does not advertise a tool it cannot run**, and the shape is not new: this is what
     * `AndroidShortcutQuery` already does for the `HOME` role — `if (!hasShortcutHostPermission())
     * return emptyList()`. Measured basis: rows 27 and 32/33. A missing catalog row fails closed
     * (`?: return@filter false`), because "nobody stated an answer" must never read as "needs nothing".
     */
    private fun available(): List<ToolDescriptor> = descriptors.filter { descriptor ->
        val needed = catalog.permissionsFor(descriptor.id) ?: return@filter false
        needed.all(presence::isGranted)
    }

    override fun all(): List<ToolDescriptor> = available()

    /**
     * Answers from [available], never from [descriptors]. Reading the unfiltered list here would make
     * the whole filter cosmetic: `ToolFederation.registry.find` is what the executor routes by, so a
     * `find` that still answered would hand the dispatcher a tool `all()` had just withheld.
     */
    override fun find(id: ToolId): ToolDescriptor? = available().firstOrNull { it.id == id }
}
