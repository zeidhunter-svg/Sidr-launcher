package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * The second consumer's only tool source: four filesystem tools over a sandbox directory.
 *
 * It is a `ToolRegistry` directly, the same shape `SystemIntentToolSource` takes, so the registry
 * stays "a list of sources" and A1' can still federate or discard either one. **The A1 fork is not
 * touched here** — this source declares its own ids (see [SandboxToolIds]) and projects nothing from
 * `ActionCatalog`, which is what keeps both branches of that fork equally cheap.
 *
 * Risk is assigned **here**, in the adapter, exactly as `DefaultActionCatalog` assigns it on the
 * Android side. The three-level scale is core-owned and untouched (spec §11.3).
 */
class SandboxToolSource : ToolRegistry {

    private val descriptors: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            id = SandboxToolIds.WORKSPACE_INFO,
            level = ToolLevels.SANDBOX,
            effect = ToolEffect.LOCAL,
            argSchema = emptyList(),
            outputSchema = listOf(
                ActionArg(SandboxKeys.ROOT, description = "Absolute path of the sandbox root"),
            ),
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        ToolDescriptor(
            id = SandboxToolIds.FIND_FILE,
            level = ToolLevels.SANDBOX,
            effect = ToolEffect.LOCAL,
            argSchema = listOf(
                ActionArg(SandboxKeys.QUERY, description = "Exact file name to look for"),
                ActionArg(SandboxKeys.ROOT, description = "Directory to search, must be inside the sandbox"),
            ),
            outputSchema = listOf(
                ActionArg(
                    SandboxKeys.RESOLVED_PATH,
                    description = "Absolute path of the match; blank when nothing matched",
                ),
            ),
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        ToolDescriptor(
            id = SandboxToolIds.DELETE_FILE,
            level = ToolLevels.SANDBOX,
            effect = ToolEffect.LOCAL,
            argSchema = listOf(
                ActionArg(SandboxKeys.PATH, description = "Absolute path of the file to delete"),
            ),
            outputSchema = emptyList(),
            // The first DANGEROUS + DURABLE tool in this repository. Deleting a file is irreversible
            // and writes durable state; nothing on the Android side is either.
            risk = ActionRiskLevel.DANGEROUS,
            durability = ToolDurability.DURABLE,
        ),
        ToolDescriptor(
            id = SandboxToolIds.RENAME_FILE,
            level = ToolLevels.SANDBOX,
            effect = ToolEffect.LOCAL,
            argSchema = listOf(
                ActionArg(SandboxKeys.PATH, description = "Absolute path of the file to rename"),
                ActionArg(SandboxKeys.NEW_NAME, description = "The new file name — a bare name, never a path"),
            ),
            outputSchema = listOf(
                ActionArg(SandboxKeys.RENAMED_PATH, description = "Absolute path after the rename"),
                ActionArg(SandboxKeys.PREVIOUS_NAME, description = "The file name before the rename"),
            ),
            // A4′ phase 2: the stand's one reversible tool (spec §6.5). It writes durable state but is
            // undone by itself, so it is TRANSIENT in this codebase's sense — the precedent of the
            // launcher's alias tools; DURABLE here means "cannot be put back".
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
    )

    override fun all(): List<ToolDescriptor> = descriptors

    override fun find(id: ToolId): ToolDescriptor? = descriptors.firstOrNull { it.id == id }
}
