package com.sidr.launcher.data.repository.agent.shortcut

import com.sidr.launcher.data.repository.agent.DynamicToolName
import com.sidr.launcher.data.repository.agent.DynamicToolNames
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A1″'s third adapter: one agent tool per Android app shortcut, read from [ShortcutCatalog]'s snapshot.
 *
 * **The first source whose tool set moves while the process lives.** Both faces below read
 * [ShortcutCatalog.current] on every call rather than caching, which is the same decision
 * `ToolFederation.snapshot()` took for the same reason: the composition root provides this as a
 * `@Singleton`, so a set computed once would be the set the device had at startup forever.
 *
 * **The first source whose availability is a runtime role rather than a permission.** Shortcut host
 * access is `android.app.role.HOME`, held by one package at a time and assigned by the user
 * (`docs/superpowers/plans/2026-09-12-a1-device-measurements.md` §2, rows 1/3/4/6). On a device where
 * Sidr is not the home app the catalog is empty and this source advertises **nothing** — which is the
 * required degradation, not a failure: the registry never offers a tool the dispatcher cannot run.
 *
 * **`SAFE`, with the reason stated rather than assumed** (spec §5.4). A shortcut launch is the same act
 * the user performs by long-pressing an app icon: it happens in front of them, it is what the OS itself
 * offers, and nothing about it leaves the device (row 10: `startShortcut` needed nothing `getShortcuts`
 * did not). The one property that does **not** transfer from the `set_timer` ruling is *"we know what it
 * does"*: nothing in the `ShortcutInfo` contract forbids an app from shipping a shortcut that acts
 * immediately. That is the limit of this rating, named here rather than implied absent.
 *
 * **`EXTERNAL`, and that is the half `DOC-ILM-2` is about.** A `SAFE` tool never reaches the consent
 * gate, so the provenance line under its step is the only thing telling the user the effect left the
 * launcher — and for this level it also says *which* app it went to.
 */
@Singleton
class ShortcutToolSource @Inject constructor(
    private val catalog: ShortcutCatalog,
) : ToolRegistry, DynamicToolNames {

    /**
     * Every shortcut whose derived id reads back **as the same pair**, and no others.
     *
     * The round-trip filter is not decoration, and it is a round trip rather than a null check — fix
     * round 1 (finding 8) corrected the second half, which the first version got wrong while its own
     * KDoc described the right thing. Two distinct defects are caught, and only an equality sees both:
     *  - a **blank** shortcut id produces `shortcut:pkg/`, which [ShortcutToolIds.parse] refuses
     *    outright, so [ShortcutToolWorker] could never route it;
     *  - a `/` anywhere in [AppShortcut.packageName] — a plain `String` with nothing validating it —
     *    re-parses into a **different** pair (`of("com.a/b", "x")` reads back as `("com.a", "b/x")`).
     *    That is not `null`, so the old check admitted it, and the worker would have asked
     *    `LauncherApps` to start some other shortcut.
     *
     * Advertising either would put a tool in the registry that fails, or misfires, every time it is
     * chosen — the precise failure `ShortcutCatalog`'s own KDoc refuses, one layer up.
     */
    private fun usable(): List<AppShortcut> = catalog.current()
        .filter {
            ShortcutToolIds.parse(ShortcutToolIds.of(it.packageName, it.shortcutId)) ==
                (it.packageName to it.shortcutId)
        }

    override fun all(): List<ToolDescriptor> = usable().map { shortcut ->
        ToolDescriptor(
            id = ShortcutToolIds.of(shortcut.packageName, shortcut.shortcutId),
            level = ToolLevels.APP_SHORTCUT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        )
    }

    override fun find(id: ToolId): ToolDescriptor? = all().firstOrNull { it.id == id }

    override fun names(): List<DynamicToolName> = usable().map { shortcut ->
        DynamicToolName(
            id = ShortcutToolIds.of(shortcut.packageName, shortcut.shortcutId),
            qualifier = shortcut.appLabel,
            name = shortcut.shortcutLabel,
        )
    }
}
