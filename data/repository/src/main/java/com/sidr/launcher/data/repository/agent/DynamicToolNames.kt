package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.tool.ToolId

/**
 * One tool whose user-visible name came from **data** rather than from our string resources.
 *
 * [qualifier] is who published it (an app label); [name] is what it does (the shortcut label). They are
 * kept apart rather than pre-joined because the join is **copy** — punctuation between two nouns — and
 * copy is the feature layer's to choose from a resource, never this layer's to concatenate. See
 * `AgentSessionPresentation.line` for where the two halves are finally put together.
 *
 * Both strings are third-party: authored by another app, in whatever language that app chose. The
 * measured device shows them arriving already localized and mixed (`ru` and `tr` labels side by side in
 * one list — `docs/superpowers/plans/2026-09-12-a1-device-measurements.md`, row 11), so they are not
 * ours to translate and they do not pass through `sidrString`.
 */
data class DynamicToolName(val id: ToolId, val qualifier: String, val name: String)

/**
 * Display names that came from data.
 *
 * `ToolDescriptor`'s KDoc says it carries no user-facing copy, and A1″ keeps that true: a shortcut's
 * name travels through this port — read by the tool selector for matching and, through a composition-root
 * projection, by the launcher's ViewModel for rendering — and never enters `:domain`.
 *
 * The port deliberately spells its method `names()` rather than `all()`: [ShortcutToolSource] implements
 * this **and** `ToolRegistry`, and one class cannot carry two `all()` overrides. Splitting the class in
 * two was rejected for a stronger reason than compilation — the names and the descriptors are derived
 * from the same snapshot, and separating them would let one go stale against the other, which is the F2
 * disease the federation exists to prevent.
 */
interface DynamicToolNames {
    fun names(): List<DynamicToolName>
}
