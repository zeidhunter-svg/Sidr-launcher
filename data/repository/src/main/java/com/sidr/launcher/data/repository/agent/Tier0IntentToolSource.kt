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

/** The two Tier-0 ids. Not projections of `ActionIds` — those seven are frozen and contain neither. */
object Tier0ToolIds {
    val SET_TIMER = ToolId("set_timer")
    val OPEN_SYSTEM_SETTINGS = ToolId("open_system_settings")
}

/**
 * A1′'s second source: Android system intents that are **not** among the frozen seven `ActionIds`, so
 * they mint their own ids (spec §5.1 — identity C binds projections only).
 *
 * Both tools are `EXTERNAL` and `SAFE`, and that combination is the one `DOC-ILM-2` is actually about:
 * a `CONFIRM` tool is already stopped by the consent gate, so provenance is the only mechanism telling
 * the user where a `SAFE` effect went.
 *
 * **Permissions.** `ACTION_SETTINGS` needs none. `ACTION_SET_TIMER` needs
 * `com.android.alarm.permission.SET_ALARM`, declared in `app/src/main/AndroidManifest.xml` — an
 * install-time (`protectionLevel: normal`) permission, so it is granted without a prompt and needs no
 * runtime request or education flow.
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
class Tier0IntentToolSource @Inject constructor() : ToolRegistry {

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
    )

    override fun all(): List<ToolDescriptor> = descriptors

    override fun find(id: ToolId): ToolDescriptor? = descriptors.firstOrNull { it.id == id }
}
