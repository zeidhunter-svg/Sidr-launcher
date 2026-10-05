package com.sidr.launcher.domain.action

/**
 * Port: the registered set of [ActionDescriptor]s, enumerated and looked up by [ActionId]. Its readers
 * today are `SystemIntentToolSource` (the `in_app` tool projection) and the learned-choice decorator;
 * the AIL-4 router and AIL-5 confirm UI it was first written for were deleted in A4′ phase 3a.
 *
 * Kept an interface in `domain` per the hard rule (interfaces here, implementations in the data
 * layer). The
 * concrete catalog — the actual descriptor instances for the shipped + AIL-2 families — is registered
 * by the data-layer implementation in AIL-2 and bound in `:app`; AIL-1 ships only the contract + the
 * family vocabulary ([LauncherAction] / [ActionIds]). Read-only and side-effect-free: enumerating the
 * catalog never touches Android, I/O, or the network.
 */
interface ActionCatalog {

    /** All registered descriptors, in a stable order. */
    fun all(): List<ActionDescriptor>

    /** The descriptor for [id], or `null` if no such family is registered. */
    fun descriptor(id: ActionId): ActionDescriptor?
}
