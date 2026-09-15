package com.sidr.launcher.data.repository.agent.shortcut

import com.sidr.launcher.domain.tool.ToolId

/**
 * A shortcut's [ToolId] is **derived in the adapter**, never hand-written — identity C, the same rule
 * `SystemIntentToolSource` follows for a projected `ActionId`. Two properties are required and both are
 * tested: the id is **stable** — derived from the package and the shortcut id alone, so a refresh that
 * re-labels a shortcut leaves it byte-identical; and it cannot collide with an authored id, because no
 * authored id contains [PREFIX]'s colon.
 *
 * **Stability is not the same claim as "a plan persisted before a restart still resolves", and fix
 * round 1 (finding 7) narrowed this sentence to the one that is proved.** Stability is necessary for
 * that; it is not sufficient. [ShortcutCatalog]'s snapshot starts **empty** and is filled
 * asynchronously by [ShortcutRefreshTrigger], so in the window before the first refresh lands
 * [ShortcutToolSource.find] answers `null` for a perfectly valid shortcut id — and a restored session
 * whose plan names one does not degrade gently: `InvocationValidator` rejects the invocation,
 * `AgentExecutor` ends the session `Failed`, and the row is cascade-deleted.
 *
 * **That window is reachable now, not merely predicted — A1″ Task 11 (`92e8704`) wired
 * `ToolMatchPlanner` through `ToolSelector`, whose dynamic branch returns exactly these ids, so a
 * persisted plan can name a shortcut tool.** The mechanism above is therefore live: a restore inside the
 * startup window fails closed exactly as described — nothing crashes and nothing wrong executes, but the
 * session dies rather than pausing for the refresh it needed. That fail-closed outcome is what Task 11
 * shipped, by ruling rather than by building new machinery: waiting for the first refresh, or re-planning,
 * instead of failing is a deliberate widening this block's DoD does not cover, and stays addressed onward
 * rather than decided here.
 *
 * [parse] is the inverse and is **total**: it answers `null` for anything it cannot read back, and every
 * caller fails closed on that rather than guessing. That is what lets [ShortcutToolSource] refuse to
 * advertise a shortcut whose id does not round-trip **to the same pair** — an advertised tool that
 * cannot run, or runs the wrong thing, is the exact failure that cost A1′ its first device acceptance.
 */
object ShortcutToolIds {
    const val PREFIX = "shortcut:"

    fun of(packageName: String, shortcutId: String): ToolId = ToolId("$PREFIX$packageName/$shortcutId")

    /**
     * The **first** slash separates: an Android package name cannot contain one, while a shortcut id
     * is an app-chosen string that may. Reading the last slash instead would split `chat/42` in the
     * wrong place and route to a shortcut that does not exist.
     */
    fun parse(id: ToolId): Pair<String, String>? {
        if (!id.value.startsWith(PREFIX)) return null
        val rest = id.value.removePrefix(PREFIX)
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash == rest.lastIndex) return null
        return rest.substring(0, slash) to rest.substring(slash + 1)
    }
}
