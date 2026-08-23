package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ToolId

/**
 * This consumer's tool vocabulary — **declared here, not in `commonMain`.**
 *
 * `ToolIds` in `domain/tool` keeps exactly its two A0 values. Adding these three there would be
 * precisely the per-consumer taxonomy A0.5 exists to warn about (spec §11.2), and `ToolId`'s
 * value-class-over-`String` design already makes it unnecessary: "adding one requires no change to a
 * central enum", as `ActionId`'s KDoc puts it. That this works with no core edit is one of the two
 * positive findings of the block — the shape that survived contact with a second consumer.
 */
object SandboxToolIds {
    val WORKSPACE_INFO = ToolId("workspace_info")
    val FIND_FILE = ToolId("find_file")
    val DELETE_FILE = ToolId("delete_file")
}

/**
 * The one spelling of every argument and output key. Declaration and emission read the same constants,
 * so they cannot drift on a name — the pattern `SystemIntentToolSource.RESOLVED_QUERY` already sets.
 */
object SandboxKeys {
    const val ROOT = "root"
    const val QUERY = "query"
    const val RESOLVED_PATH = "resolved_path"
    const val PATH = "path"
}
