package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.tool.ToolId

/**
 * Where a tool sits in OUR vocabulary for the model (spec §7.12.1). Three, so the model can tell
 * Sidr's own surfaces from the phone's functions from the world outside: "settings" alone means two
 * different screens (Sidr's, Android's), and the namespace is what separates them.
 */
enum class ToolNamespace { LAUNCHER, DEVICE, CONTENT }

/**
 * One argument as the MODEL sees it — deliberately not `ActionArg` (spec §7.12.1). `uninstall_app`
 * declares `app` as "Package of the app to remove"; the model must never write a package, so its view
 * of the same argument is "the app, in the user's own words".
 *
 * [mention] marks an argument that names something in the user's world (today: an app). The model
 * copies the user's words into it; `BridgeValidator` refuses words the user did not type; the device
 * binds them (`MentionBinder`). One bit, not a sort taxonomy — the sort system is 3b (spec §7.8, `F6`).
 *
 * [acceptsRef] (A4′ phase 3b, B-18): the argument may hold, instead of words, a reference to an output of
 * an earlier step; a reference anywhere else is `BridgeError.SHAPE`. A mention that accepts one (a sandbox
 * path) is words when it is words — derivable — and the device's value when it is a reference.
 */
data class ModelArg(
    val name: String,
    val description: String,
    val required: Boolean,
    val mention: Boolean,
    val acceptsRef: Boolean = false,
)

/**
 * One output a tool's step makes that a later step may name (A4′ phase 3b, Task 8): [name] is what a
 * reference's `key` says, [description] our words for it. Android describes none (B-8).
 */
data class ModelOutput(val name: String, val description: String)

/**
 * What we tell the model about one tool: our literals only, never a third party's text. [outputs] are
 * what a later step may reference (B-8: none on Android; the JVM sandbox declares its own).
 */
data class ToolDescription(
    val namespace: ToolNamespace,
    val text: String,
    val args: List<ModelArg>,
    val outputs: List<ModelOutput> = emptyList(),
)

/**
 * The parallel port of descriptions (spec §7.1 barrier 1): beside `ToolDescriptor`, not a field in it,
 * so `ToolDescriptor` stays free of user-facing or model-facing copy. `null` means "we have not
 * described this tool", and an undescribed tool is never offered (`BridgeFilter`).
 */
fun interface ToolDescriptions {
    fun describe(id: ToolId): ToolDescription?
}
