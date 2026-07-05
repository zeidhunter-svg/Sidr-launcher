package com.sidr.launcher.domain.action

/**
 * Opaque, stable identifier for a registered launcher action family (e.g. "launch_app",
 * "web_search").
 *
 * Deliberately a value class over a raw [String], NOT an enum: the string is the wire identity used
 * both as the [LauncherAction] family key and, in AIL-4, as the `action` id the LLM router emits in
 * its structured JSON. Keeping it a value class means adding an action requires no change to a
 * central enum, while still giving callers a distinct type instead of a bare `String`.
 *
 * Canonical values live in [ActionIds]; construct new ones only when registering a new family.
 */
@JvmInline
value class ActionId(val value: String)
