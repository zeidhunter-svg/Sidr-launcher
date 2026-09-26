package com.sidr.launcher.data.repository.agent.memory

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.requiresConsent
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Task 9, A1″ Phase 3a: `MemoryToolSource`, the fourth federated source.
 *
 * **The floor this class exists for, spelled out because the identical hole was found and closed once
 * already for `Tier0IntentToolSource`.** That source's `available()` withholds any tool with no
 * `ToolPermissionCatalog` row, which turns "missing a row" into "silently never registered" rather than
 * "registered but refused", so a guard that quantifies over the registry passes vacuously. This source
 * filters nothing — it consults no `ToolPermissionCatalog` at all, unlike `Tier0IntentToolSource` — so
 * that particular failure mode does not apply here: `all()`/`find()` always answer from the same
 * unconditional `descriptors` list. The floor below exists anyway, for the reason
 * `Tier0IntentToolSourceTest`'s own KDoc gives for its twin: **adding a fourth tool to this source
 * without extending `REQUIRED_TOOL_IDS` in both `:app` guards would leave nothing anywhere red.** The
 * list here is this source's own equivalent — its own literal, never derived from `descriptors`.
 */
class MemoryToolSourceTest {

    @Test
    fun `the memory source offers three tools at the launcher_memory level, all local and safe`() {
        val source = MemoryToolSource()

        assertEquals(
            "A tool registered by this source with no line here — and no matching line in " +
                "REQUIRED_TOOL_IDS in both ToolRegistryPermissionGuardTest and DoctrineGuardTest " +
                "(:app) — is not checked by any :app guard.",
            listOf(
                MemoryToolIds.SET_APP_ALIAS,
                MemoryToolIds.FORGET_APP_ALIAS,
                MemoryToolIds.FORGET_LEARNED_CHOICE,
            ),
            source.all().map { it.id },
        )
        assertEquals(true, source.all().all { it.level == ToolLevels.LAUNCHER_MEMORY })
        assertEquals(true, source.all().all { it.effect == ToolEffect.LOCAL })
        assertEquals(true, source.all().all { it.risk == ActionRiskLevel.SAFE })
        assertEquals(true, source.all().none { requiresConsent(it.risk) })
        assertEquals(
            "A memory tool is a row in the launcher's own database, undone from Settings in two taps — " +
                "not the irreversible footprint ToolDurability's own KDoc defines DURABLE to mean " +
                "(owner decision 2026-09-18, review finding C1). DURABLE here would stop the loop for " +
                "consent on an operation that hands nothing outside the launcher.",
            true,
            source.all().all { it.durability == ToolDurability.TRANSIENT },
        )
    }

    @Test
    fun `set_app_alias declares app, phrase and an optional app_label`() {
        val source = MemoryToolSource()
        val descriptor = source.find(MemoryToolIds.SET_APP_ALIAS)!!

        assertEquals(listOf("app", "phrase", "app_label"), descriptor.argSchema.map { it.name })
        assertEquals(
            "app_label is bound by the planner AFTER resolution and the vocabulary never supplies it " +
                "directly (same shape as uninstall_app's own app_label); a required declaration would " +
                "make every goal binding it NoPlan forever.",
            listOf(true, true, false),
            descriptor.argSchema.map { it.required },
        )
    }

    @Test
    fun `forget_app_alias and forget_learned_choice each declare exactly one required phrase`() {
        val source = MemoryToolSource()

        assertEquals(listOf("phrase"), source.find(MemoryToolIds.FORGET_APP_ALIAS)!!.argSchema.map { it.name })
        assertEquals(
            listOf(true),
            source.find(MemoryToolIds.FORGET_APP_ALIAS)!!.argSchema.map { it.required },
        )
        assertEquals(
            listOf("phrase"),
            source.find(MemoryToolIds.FORGET_LEARNED_CHOICE)!!.argSchema.map { it.name },
        )
        assertEquals(
            listOf(true),
            source.find(MemoryToolIds.FORGET_LEARNED_CHOICE)!!.argSchema.map { it.required },
        )
    }

    @Test
    fun `find answers nothing for an id this source does not declare`() {
        val source = MemoryToolSource()

        assertNull(source.find(ToolId("not_a_memory_tool")))
    }
}
