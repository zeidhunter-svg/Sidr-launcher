package com.sidr.launcher.consumer.jvm.bridge

import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.domain.ai.bridge.ModelArg
import com.sidr.launcher.domain.ai.bridge.ModelOutput
import com.sidr.launcher.domain.ai.bridge.ToolDescription
import com.sidr.launcher.domain.ai.bridge.ToolDescriptions
import com.sidr.launcher.domain.ai.bridge.ToolNamespace
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels

/**
 * What the model sees of the sandbox's four tools (A4′ phase 3b, Task 13; the plan's finding S2) — our
 * words only, beside `SandboxToolSource`'s descriptors, never in them (spec §7.1 barrier 1). Each argument
 * is the MODEL's view, which differs from the tool's where the tool asks for something the model cannot
 * know:
 *  - `workspace_info()` — no arguments, and described as nothing a file request needs: it describes no
 *    output to the model, so no later step can name its `root`;
 *  - `find_file(name)` — [NAME] is the file name in the user's own words, a mention (derivable from the
 *    command). The tool's `query` is filled from it and its `root` is **bound by the device** — the sandbox
 *    root, by `SandboxMentionBinder` after the answer — and never asked of the model, which has no way to
 *    know it. Its output `resolved_path` is described, so a later step may reference it (B-18);
 *  - `delete_file(path)` — `path` is a mention that also accepts a reference: the user's words for a file,
 *    resolved on this computer by `SandboxFileSort`, or `{from_step, key: resolved_path}`;
 *  - `rename_file(path, new_name)` — `path` as `delete_file`'s; `new_name` the new name, text.
 *
 * Every tool is in [ToolNamespace.DEVICE] — the sandbox is this computer's own folder, not the web.
 * **A named limit, not this file's to change:** `BridgeRendering.INSTRUCTION` is Android's text — it tells
 * the model the request was typed into «Sidr, an Android home screen», and it marks every mention «app»;
 * both reach the JVM's prompt unchanged.
 */
class SandboxToolDescriptions : ToolDescriptions {

    override fun describe(id: ToolId): ToolDescription? = when (id) {
        SandboxToolIds.WORKSPACE_INFO -> ToolDescription(
            ToolNamespace.DEVICE,
            "Name the sandbox folder. Never needed to find, delete or rename a file: this computer supplies the folder itself.",
            emptyList(),
        )
        SandboxToolIds.FIND_FILE -> ToolDescription(
            ToolNamespace.DEVICE,
            "Find one file in the sandbox folder by its name.",
            listOf(ModelArg(NAME, "the file name, in the user's own words", required = true, mention = true)),
            outputs = listOf(ModelOutput(SandboxKeys.RESOLVED_PATH, "the path of the file found")),
        )
        SandboxToolIds.DELETE_FILE -> ToolDescription(
            ToolNamespace.DEVICE,
            "Delete one file in the sandbox folder.",
            listOf(filePath("the file to delete")),
        )
        SandboxToolIds.RENAME_FILE -> ToolDescription(
            ToolNamespace.DEVICE,
            "Rename one file in the sandbox folder.",
            listOf(
                filePath("the file to rename"),
                ModelArg(SandboxKeys.NEW_NAME, "the new file name, a bare name and never a path", required = true, mention = false),
            ),
        )
        else -> null
    }

    private fun filePath(what: String) = ModelArg(
        SandboxKeys.PATH,
        "$what: its name in the user's own words, or the path an earlier step found",
        required = true,
        mention = true,
        acceptsRef = true,
    )

    companion object {
        /** `find_file`'s argument as the model sees it — the tool's own is `query` (`SandboxKeys.QUERY`). */
        const val NAME = "name"

        /** This consumer's allow-list of tool levels (B-12): the sandbox, and nothing Android's. */
        val OFFERED_LEVELS: Set<ToolLevel> = setOf(ToolLevels.SANDBOX)
    }
}
