package com.sidr.launcher.domain.tool

/**
 * Port: the registered tools. A0 registers exactly one source (a projection of `ActionCatalog`);
 * A1' turns this into a federation of adapters over one vocabulary. Read-only and side-effect free.
 */
interface ToolRegistry {
    fun all(): List<ToolDescriptor>
    fun find(id: ToolId): ToolDescriptor?
}
