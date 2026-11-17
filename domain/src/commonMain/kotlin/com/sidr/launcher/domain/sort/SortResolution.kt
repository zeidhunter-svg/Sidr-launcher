package com.sidr.launcher.domain.sort

/** One value a sort could mean, offered to the user: [value] is what binds, [label] what is shown. */
data class SortCandidate(val value: String, val label: String)

/**
 * What the device made of the user's words for one sort (A4′ phase 3b, fork 3b-C). Five answers a resolver
 * may give, each a different thing to tell the user — so a RESOLVER folds none into another. Its CALLERS may,
 * where the user would be told the same thing: for a value sort (duration, clock time, address) step 2b
 * (`ToolMatchPlanner`) and the bridge's binder (`DeviceMentionBinder`) read [NotFound] as [Unreadable] and
 * a [Choice] as [Unavailable] — and the pure value sorts answer only [Resolved] or [Unreadable] anyway:
 *  - [Resolved] — one value; it binds;
 *  - [Choice] — several values, or one to confirm ([confirm] = `true`); the USER picks before any card;
 *  - [NotFound] — the words were read and nothing on the device answers to them («нет такого приложения»);
 *  - [Unreadable] — the words do not read as this sort at all («полчасика» is not a duration);
 *  - [Unavailable] — the device could not be asked (the app list could not be read) — neither of the two above.
 */
sealed interface SortResolution {
    data class Resolved(val value: String) : SortResolution
    data class Choice(val candidates: List<SortCandidate>, val confirm: Boolean) : SortResolution
    data object NotFound : SortResolution
    data object Unreadable : SortResolution
    data object Unavailable : SortResolution
}

/**
 * The resolver registered for one sort. It runs on the device, before any card; nothing it reads or
 * answers goes back to the model (D1). Each consumer registers its own map of sort → resolver.
 */
fun interface SortResolver {
    suspend fun resolve(words: String): SortResolution

    /**
     * The same reading for a tool whose act cannot be undone ([irreversible]; `ToolDurability.DURABLE`): a
     * resolver that knows when its one match is exact only after the text folding answers that match as a
     * one-candidate [SortResolution.Choice] with `confirm = true` — offered, never bound alone (owner
     * decisions 2026-11-17: «2: a» for `uninstall_app`, «1: a» for `:consumer:jvm`'s `delete_file`). The
     * default knows nothing finer than [resolve].
     */
    suspend fun resolve(words: String, irreversible: Boolean): SortResolution = resolve(words)
}
