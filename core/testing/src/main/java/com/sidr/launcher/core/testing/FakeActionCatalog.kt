package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionId

/**
 * In-memory fake for [ActionCatalog]. Enumerates whatever [descriptors] it is seeded with (empty by
 * default) and looks them up by [ActionId]. Not wired into any Hilt graph — use directly in unit
 * tests. There is no production catalog yet (AIL-1 is contracts-only); AIL-2 registers the real one.
 */
class FakeActionCatalog(
    descriptors: List<ActionDescriptor> = emptyList(),
) : ActionCatalog {

    private var descriptors: List<ActionDescriptor> = descriptors

    fun setDescriptors(descriptors: List<ActionDescriptor>) {
        this.descriptors = descriptors
    }

    override fun all(): List<ActionDescriptor> = descriptors

    override fun descriptor(id: ActionId): ActionDescriptor? =
        descriptors.firstOrNull { it.id == id }
}
