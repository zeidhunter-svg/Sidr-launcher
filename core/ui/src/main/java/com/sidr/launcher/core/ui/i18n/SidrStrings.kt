package com.sidr.launcher.core.ui.i18n

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.util.Locale

/**
 * I18N-1 string seam (spec §6). **Every** user-facing string read in this app goes through
 * [sidrString]; `stringResource` is called nowhere else, and `StringSeamGuardTest` enforces that.
 *
 * The seam exists so a future runtime overlay (the `translate_ui` A-stage feature) can serve strings
 * on top of the compiled resources without touching a single call site. I18N-1 ships the extension
 * point only - the shipped app always runs with [SidrStringOverlay.None].
 */
fun interface SidrStringOverlay {

    /**
     * An overlay value for [key] - the **resource entry name** (e.g. `ui_action_cancel`), never the
     * numeric id, which is not stable across builds. Returns `null` to fall through to resources.
     */
    fun lookup(key: String): String?

    companion object {
        /** No overlay: the shipped path. Identity-compared in [sidrString], so it costs nothing. */
        val None = SidrStringOverlay { null }
    }
}

val LocalSidrStringOverlay = staticCompositionLocalOf { SidrStringOverlay.None }

/**
 * Reads a string resource through the overlay seam.
 *
 * The [SidrStringOverlay.None] identity check short-circuits **before** the entry-name lookup, so the
 * shipped app performs exactly one `stringResource` call and no extra allocation.
 */
@Composable
@ReadOnlyComposable
fun sidrString(@StringRes id: Int): String {
    val overlay = LocalSidrStringOverlay.current
    if (overlay === SidrStringOverlay.None) return stringResource(id)
    val key = LocalContext.current.resources.getResourceEntryName(id)
    return overlay.lookup(key) ?: stringResource(id)
}

@Composable
@ReadOnlyComposable
fun sidrString(@StringRes id: Int, vararg formatArgs: Any): String {
    val overlay = LocalSidrStringOverlay.current
    if (overlay === SidrStringOverlay.None) return stringResource(id, *formatArgs)
    val key = LocalContext.current.resources.getResourceEntryName(id)
    val template = overlay.lookup(key) ?: return stringResource(id, *formatArgs)
    return String.format(Locale.getDefault(), template, *formatArgs)
}

/**
 * Plurals deliberately **bypass** the overlay in I18N-1: quantity selection is locale grammar, not
 * copy, and an overlay that supplied one form would silently break `ru` (one/few/many/other) and `tr`.
 * When `translate_ui` lands it must supply whole quantity sets or leave plurals to resources.
 */
@Composable
@ReadOnlyComposable
fun sidrPluralString(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
    pluralStringResource(id, count, *formatArgs)
