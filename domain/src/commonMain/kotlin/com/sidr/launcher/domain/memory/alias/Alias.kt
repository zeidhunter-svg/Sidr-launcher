package com.sidr.launcher.domain.memory.alias

/** Max stored alias-phrase length; longer phrases are never persisted (privacy + sanity). */
const val MAX_ALIAS_PHRASE_LENGTH: Int = 64

/** An explicit, user-declared nickname -> target mapping. `phrase` is the normalized full phrase key. */
data class Alias(
    val phrase: String,
    val target: AliasTarget,
    val createdAtEpochMs: Long,
)

/** The thing an alias points at. v1 = app only; sealed so it generalizes without breaking the port. */
sealed interface AliasTarget {
    data class App(val packageName: String) : AliasTarget
}

/** The app package for an [AliasTarget.App], or null for a non-app target. Exhaustive, cast-free. */
fun AliasTarget.appPackageOrNull(): String? = when (this) {
    is AliasTarget.App -> packageName
}

/** Display projection for the management screen, with target resolved against installed apps. */
data class AliasView(
    val phrase: String,
    val targetPackageName: String,
    val targetLabel: String,
)
