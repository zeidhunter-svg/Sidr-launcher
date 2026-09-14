package com.sidr.launcher.data.repository.agent.shortcut

import com.sidr.launcher.domain.tool.ToolId

/**
 * A shortcut's [ToolId] is **derived in the adapter**, never hand-written — identity C, the same rule
 * `SystemIntentToolSource` follows for a projected `ActionId`. Two properties are required and both are
 * tested: the id is stable across a refresh, so a plan persisted before a restart still resolves; and
 * it cannot collide with an authored id, because no authored id contains [PREFIX]'s colon.
 *
 * [parse] is the inverse and is **total**: it answers `null` for anything it cannot read back, and every
 * caller fails closed on that rather than guessing. That is what lets [ShortcutToolSource] refuse to
 * advertise a shortcut whose id would not round-trip — an advertised tool that cannot run is the exact
 * failure that cost A1′ its first device acceptance.
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
