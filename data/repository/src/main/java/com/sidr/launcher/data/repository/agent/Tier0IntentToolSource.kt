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

/**
 * The Tier-0 ids. Not projections of `ActionIds` — those seven are frozen and contain none of them.
 *
 * **Deliberately not counted here.** This KDoc read "The four Tier-0 ids" from A1′ until A1″ phase 3b
 * added eleven more, and it was wrong the moment `set_alarm` landed. A count in prose beside a list
 * that grows is a claim nobody re-reads; the authoritative count is the list below, and the equality
 * assertions in `Tier0IntentToolSourceTest` are what hold it (finding R14-41: the numbers in this
 * family have moved every session, and no comment has ever kept up).
 */
object Tier0ToolIds {
    val SET_TIMER = ToolId("set_timer")
    val OPEN_SYSTEM_SETTINGS = ToolId("open_system_settings")

    /** Task 5 (A1″ Phase 3a). See [Tier0IntentToolSource]'s KDoc for what `SAFE` rests on here. */
    val SET_ALARM = ToolId("set_alarm")

    /** Task 7 (A1″ Phase 3a): the track's first `CONFIRM` tool. See its descriptor for the conditions. */
    val UNINSTALL_APP = ToolId("uninstall_app")

    /** A1″ Phase 3b, Task 2 (Slice A). Opens the clock's alarm list; state unchanged (rows 14, 29). */
    val SHOW_ALARMS = ToolId("show_alarms")

    /**
     * A1″ Phase 3b, Task 2 (Slice A). Opens the viewfinder; the sensor goes live with no user tap
     * (row 17). See its descriptor for why `SAFE` still holds.
     */
    val OPEN_CAMERA = ToolId("open_camera")

    /**
     * A1″ Phase 3b, Task 2 (Slice A). Row 18: opened with a state-changing modal already raised
     * (finding P3). See its descriptor for the full reasoning.
     */
    val OPEN_WIFI_SETTINGS = ToolId("open_wifi_settings")

    /** A1″ Phase 3b, Task 2 (Slice A). Opens the screen; state unchanged (row 19). */
    val OPEN_BLUETOOTH_SETTINGS = ToolId("open_bluetooth_settings")

    /**
     * A1″ Phase 3b, Task 3 (Slice B). Row 20 — resolves to Samsung Device Care
     * (`com.samsung.android.lool/…PowerUsageSummary`), not `com.android.settings` (finding P4). See
     * its descriptor for why that is a named, ROM-dependent premise rather than a fixed one.
     */
    val OPEN_BATTERY_SETTINGS = ToolId("open_battery_settings")

    /** A1″ Phase 3b, Task 3 (Slice B). Opens the screen; the landed screen carries a mobile-data toggle (row 21). */
    val OPEN_DATA_USAGE_SETTINGS = ToolId("open_data_usage_settings")

    /** A1″ Phase 3b, Task 3 (Slice B). Opens the screen; state unchanged (row 22). */
    val OPEN_DISPLAY_SETTINGS = ToolId("open_display_settings")

    /** A1″ Phase 3b, Task 3 (Slice B). Opens the screen; state unchanged (row 23). */
    val OPEN_SOUND_SETTINGS = ToolId("open_sound_settings")

    /**
     * A1″ Phase 3b, Task 4 (Slice C). Opens the screen; state unchanged (rows 24, 29). Its catalog
     * row claims no permission, and **that claim is sufficiency, never proved necessity** — see the
     * descriptor and `ToolPermissionCatalog`'s row for what rows 30 and 33 did and did not exclude.
     */
    val OPEN_LOCATION_SETTINGS = ToolId("open_location_settings")

    /**
     * A1″ Phase 3b, Task 4 (Slice C). Opens the screen; state unchanged (row 25). The platform has
     * **no** public constant for this action — see [Tier0IntentToolWorker]'s own file-level constant
     * and the comment on it (finding P1).
     */
    val OPEN_NOTIFICATION_SETTINGS = ToolId("open_notification_settings")

    /**
     * A1″ Phase 3b, Task 4 (Slice C). The only argument-carrying tool of the eleven navigating ones.
     * Row 15: the landed screen is **one tap from «Удалить»**, and a target package with no launcher
     * activity was **not** filtered out. See its descriptor for why `SAFE` still holds and for the
     * `required = true` pin its single argument carries.
     */
    val OPEN_APP_INFO = ToolId("open_app_info")
}

/**
 * A1′'s second source: Android system intents that are **not** among the frozen seven `ActionIds`, so
 * they mint their own ids (spec §5.1 — identity C binds projections only).
 *
 * Every tool here is `EXTERNAL`. **Most, but not all, are `SAFE`** — and that combination is the one
 * `DOC-ILM-2` is actually about: a `CONFIRM` tool is already stopped by the consent gate, so
 * provenance is the only mechanism telling the user where a `SAFE` effect went. Since Task 7 the
 * source also holds the other kind — `uninstall_app`, the track's first `CONFIRM` and first `DURABLE`
 * tool — so "all Tier-0 tools are safe" is no longer true of this file and must not be re-asserted
 * anywhere as a property of the level. The proportion is deliberately not written as a ratio: it was
 * "three of the four" until phase 3b, and a ratio in prose beside a growing list goes stale silently.
 * `DoctrineGuardTest.declaredRisk` is where each tool's risk is actually pinned.
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
        // is no prefilled form, and the OS interposed no consent step. Both rows fired with
        // `EXTRA_SKIP_UI = false`, which is what this tool sends; the `true` case was never measured,
        // so nothing here claims it behaves the same. (Narrowed by fix round 2, finding #4: the
        // sentence used to say "either way", which stated as measured fact the one half of the pair
        // that no row covers.) So
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
        // refuses or draws its dialog) — and the limit that belongs with it (spec §7.7): the
        // precondition closes exactly ONE cause of refusal, a missing permission; device policy, a
        // work profile or an unremovable package still refuse invisibly; (6) the manifest line is
        // held by `ToolPermissionManifestGuardTest`.
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
        // A1″ Phase 3b, Task 2 (Slice A) — the first four of the eleven navigating tools (spec §7.6,
        // plan §0.1). All four are SAFE, and the justification is positive rather than "it is only a
        // settings screen": each performs no act at all, so there is nothing to reverse, and "the
        // final act is the user's" is literally true of them — the same reasoning this file's KDoc
        // already isolates for `open_system_settings`, deliberately not folded into `set_timer`'s
        // four-property argument (that argument is for a tool that DOES perform an act).
        //
        // Row 14, re-run 29: `AlarmClock.ACTION_SHOW_ALARMS` opens the clock's own alarm list; state
        // unchanged.
        ToolDescriptor(
            id = Tier0ToolIds.SHOW_ALARMS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Row 17: `MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA` opens the viewfinder. The app holds
        // NO `CAMERA` permission, so the negative ("this needs none") is readable here, unlike
        // `open_location_settings` (Task 4's P2). Row 17 also names a side effect worth stating
        // plainly rather than softening: **the camera sensor goes live with no user tap.** SAFE still
        // holds on the same positive ground as the rest of this slice — opening the app performs no
        // act the tool itself is responsible for; whatever the camera app then does with its own UI
        // is that app's act, not this one's.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_CAMERA,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Row 18, finding P3. `Settings.ACTION_WIFI_SETTINGS` measured on the SM-A325F opened WITH A
        // MODAL ALREADY RAISED — «Отключить мобильную точку доступа? … Отмена / OK» — because that
        // phone's mobile hotspot was on, and that phone is the owner's own tether (the laptop reaches
        // the internet through it). This tool stays SAFE because IT performed no act: it opened a
        // screen, and the modal belongs to the responder, the tap belongs to the user. Both halves of
        // that must be said and neither softened: this is also the ONE navigating tool of the eleven
        // whose landed screen can change device state on a single tap once it is on screen — a
        // property of what the screen offers, not of anything this tool did.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_WIFI_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Row 19: `Settings.ACTION_BLUETOOTH_SETTINGS` opens the screen; state unchanged. The app
        // holds no `BLUETOOTH*` permission at all.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // A1″ Phase 3b, Task 3 (Slice B) — the next four of the eleven navigating tools. Same positive
        // SAFE reasoning as Slice A: each performs no act at all, so there is nothing to reverse, and
        // "the final act is the user's" is literally true of them.
        //
        // Row 20, finding P4. `Intent.ACTION_POWER_USAGE_SUMMARY` measured on the SM-A325F resolved to
        // **Samsung Device Care** (`com.samsung.android.lool/com.samsung.android.sm.battery.ui.graph.
        // PowerUsageSummary`), NOT `com.android.settings`. That it resolves at all is a **ROM-dependent
        // premise measured on one device** — named, not fixed. `launch()` already returns `Failed` on
        // `ActivityNotFoundException` if a ROM does not answer this action.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_BATTERY_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Row 21: `Settings.ACTION_DATA_USAGE_SETTINGS` opens the screen. The landed screen carries a
        // mobile-data toggle — the same "what the landed screen offers is not an act this tool
        // performed" reasoning as `open_wifi_settings` (P3), but without P3's already-raised modal; not
        // overstated here because none was measured.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Row 22: `Settings.ACTION_DISPLAY_SETTINGS` opens the screen; state unchanged.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_DISPLAY_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Row 23: `Settings.ACTION_SOUND_SETTINGS` opens the screen; state unchanged.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_SOUND_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // A1″ Phase 3b, Task 4 (Slice C) — the last three navigating tools. Same positive SAFE
        // reasoning as Slices A and B, and it is worth restating rather than referring to, because two
        // of these three have a landed screen that offers the user something: each of these tools
        // performs no act at all, so there is nothing to reverse, and "the final act is the user's" is
        // literally true of them. What the LANDED SCREEN offers is not an act this tool performed.
        //
        // Rows 24 and 29: `Settings.ACTION_LOCATION_SOURCE_SETTINGS` opens the screen; state unchanged.
        //
        // **Its permission requirement is measured SUFFICIENT and never proved NECESSARY** (finding
        // P2), and the catalog row says `emptyList()` for a reason that is decisive rather than a
        // preference. Row 24 states the requirement is unmeasured. Row 30 removes the *grant* from the
        // explanations — `ACCESS_FINE_LOCATION: granted=false` at the very moment the screen opened —
        // row 33 confirms that reading from inside the process, and row 29 re-ran it on a fresh build.
        // What remains unexcluded is this app's *declaration* of `ACCESS_FINE_LOCATION`, present for
        // the prayer feature. A row NAMING that permission would be worse than an unproven
        // `emptyList()`: `available()` below filters on `presence::isGranted`, and row 33 measured
        // that permission `PERMISSION_DENIED (raw=-1)` in-process, so the source would withhold this
        // tool **on the owner's own phone** — registered nowhere, reachable never, and every equality
        // assertion green. That is this block's own "shipped invisible" failure.
        //
        // So `emptyList()` here means **"no permission we must add"**, and the necessity question is
        // OPEN. `ToolRegistryPermissionGuardTest` can only verify the permissions a row *claims*, never
        // the ones the platform will actually demand — the boundary that guard's own KDoc states. This
        // is a **named limit, not a closed question**, and it is a numbered item on the acceptance
        // checklist: if the screen fails to open on a build where the prayer feature's declaration is
        // absent, this row is wrong.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_LOCATION_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Row 25: the notification-settings screen opens; state unchanged. **The platform has no public
        // constant for this action** (finding P1): `javap` over `platforms/android-37.0/android.jar`
        // finds `ACTION_APP_NOTIFICATION_SETTINGS` and `ACTION_NOTIFICATION_LISTENER_SETTINGS`, but no
        // bare `Settings.ACTION_NOTIFICATION_SETTINGS`. The action string this tool sends is therefore
        // OURS, not the platform's — see the file-level constant in `Tier0IntentToolWorker`, which
        // carries the same warning at the site where it is spelled.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        // Row 15: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with a package `Uri` opens that app's
        // info screen. Two things that row measured, both named rather than softened: the landed screen
        // is **one tap from «Удалить»**, and run 2 measured that a target package with **no launcher
        // activity was NOT filtered out** — the platform opened the screen for it just the same.
        // Neither changes `SAFE`, on the reasoning above: this tool opened a screen and performed
        // nothing; the uninstall on that screen is an act the user performs, and it is the same act
        // `uninstall_app` exists to put behind a `CONFIRM` gate when WE perform it.
        //
        // **`app` is `required = true`, written out although that is the default, and the reason is not
        // tidiness** (R14-37). `ToolMatchPlanner` resolves an argument named `app` **regardless of
        // `required`**: the branch keys on the NAME. A descriptor declaring `app` optional would
        // therefore give `raw = ""`, `AppTargetResolver.resolve("")` returns `null` (its considered
        // refusal, never a guess), and the planner answers `NoPlan` — for EVERY goal, forever, with the
        // whole suite green and the tool registered, reachable by its trigger and dead. The planner
        // carries the same warning at the code site that causes it.
        //
        // **This is the SECOND consecutive per-descriptor pin, and there is still no generalisation.**
        // `uninstall_app` above pins the opposite direction (`app_label` must stay OPTIONAL, R14-35);
        // this one pins `app` REQUIRED. Neither direction is generalised, nothing in the type system
        // holds either, and the pins are two tests in `Tier0IntentToolSourceTest`. The generalisation
        // is the `ArgType` debt (spec §7.5) and it is A4′'s — the accumulation of instances is the
        // evidence that block needs, and it is recorded here rather than repaired.
        //
        // **`app_label` is deliberately NOT declared**, unlike `uninstall_app`. That tool needs both
        // because its `CONFIRM` card must name what the user said AND what will be removed. This tool
        // is `SAFE`, draws no card, and a second argument would route it down `uninstall_app`'s
        // by-name branch in `AgentSessionPresentation.line()` for no gain, while hitting the
        // argument-**count** step-line limit (A1′ residual (5) / A1″ residual (7)).
        //
        // **The legibility limit that follows, named rather than worked around:** resolution happens
        // above the consent gate, so the plan carries the RESOLVED package and the step line reads
        // e.g. "App info for com.example.app" rather than the word the user typed. For a `SAFE` tool
        // that is a legibility limit, not a safety one — nothing is gated on that line — and it is
        // not to be softened by adding `app_label`.
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_APP_INFO,
            argSchema = listOf(
                ActionArg("app", required = true, description = "Package of the app to show info for"),
            ),
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
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
