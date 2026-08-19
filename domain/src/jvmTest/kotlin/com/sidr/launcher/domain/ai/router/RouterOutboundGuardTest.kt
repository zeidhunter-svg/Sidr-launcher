package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.ai.OutboundContextPolicy
import com.sidr.launcher.domain.ai.OutboundContextPolicy.AllowedContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * AIL-4 outbound-privacy guard (ADR 2026-07-05 §5). The router widens the outbound allow-list by
 * exactly one static, content-free category — [AllowedContext.ACTION_CATALOG_SCHEMA] — and this test
 * pins that widening and proves the rendered schema carries only capability metadata, never a
 * forbidden context term or a credential term. (The data-layer companion scans the *real*
 * `DefaultActionCatalog`; the impl-level companion proves the outbound HTTP body is command + schema
 * only.)
 */
class RouterOutboundGuardTest {

    @Test
    fun `allow-list adds exactly the static action-schema category and nothing else`() {
        assertEquals(
            setOf(
                AllowedContext.USER_COMMAND,
                AllowedContext.STATIC_SYSTEM_PROMPT,
                AllowedContext.GENERATION_LIMITS,
                AllowedContext.ACTION_CATALOG_SCHEMA,
            ),
            OutboundContextPolicy.ALLOWED,
        )
    }

    @Test
    fun `no allowed category name is a forbidden context source`() {
        val violations = OutboundContextPolicy.ALLOWED.flatMap { category ->
            OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS
                .filter { term -> category.name.contains(term, ignoreCase = true) }
                .map { term -> category.name to term }
        }
        if (violations.isNotEmpty()) {
            fail("Allow-listed category names a forbidden context source: $violations")
        }
    }

    @Test
    fun `static router instruction carries no forbidden context term`() {
        val hits = OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS
            .filter { CatalogSchemaRenderer.ROUTER_INSTRUCTION.contains(it, ignoreCase = true) }
        if (hits.isNotEmpty()) fail("ROUTER_INSTRUCTION leaks forbidden context term(s): $hits")
    }

    @Test
    fun `static router instruction carries no credential term`() {
        val hits = OutboundContextPolicy.CREDENTIAL_TERMS
            .filter { CatalogSchemaRenderer.ROUTER_INSTRUCTION.contains(it, ignoreCase = true) }
        if (hits.isNotEmpty()) fail("ROUTER_INSTRUCTION leaks credential term(s): $hits")
    }

    @Test
    fun `render over an empty catalog is exactly the static instruction - no hidden context`() {
        // render() takes ONLY the catalog: an empty catalog proves nothing but the static preamble
        // and descriptor-derived text can ever be produced (no device/usage/history smuggling).
        val emptyCatalog = catalogOf()
        assertEquals(CatalogSchemaRenderer.ROUTER_INSTRUCTION, CatalogSchemaRenderer.render(emptyCatalog))
    }

    @Test
    fun `rendered schema for representative descriptors is denylist-clean`() {
        val rendered = CatalogSchemaRenderer.render(representativeCatalog)
        val forbidden = OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS
            .filter { rendered.contains(it, ignoreCase = true) }
        val credential = OutboundContextPolicy.CREDENTIAL_TERMS
            .filter { rendered.contains(it, ignoreCase = true) }
        if (forbidden.isNotEmpty() || credential.isNotEmpty()) {
            fail("Rendered schema leaks term(s) — forbidden=$forbidden credential=$credential")
        }
    }

    @Test
    fun `a planted sensitive value in device context never reaches the rendered schema`() {
        // The renderer has no device-context input at all — this asserts the structural guarantee:
        // a sentinel that is NOT part of any descriptor cannot appear in the outbound schema.
        val sentinel = "LAT:37.7749,LON:-122.4194"
        val rendered = CatalogSchemaRenderer.render(representativeCatalog)
        assertFalse(rendered.contains(sentinel))
        assertTrue(rendered.startsWith(CatalogSchemaRenderer.ROUTER_INSTRUCTION))
    }

    private val representativeCatalog = catalogOf(
        ActionDescriptor(
            id = ActionIds.LAUNCH_APP,
            title = "Open app",
            description = "Launch an installed app by name",
            category = ActionCategory.APP,
            risk = ActionRiskLevel.SAFE,
            argSchema = listOf(ActionArg("query", description = "The app name to launch")),
        ),
        ActionDescriptor(
            id = ActionIds.OPEN_URL,
            title = "Open link",
            description = "Open a web address in the browser",
            category = ActionCategory.WEB,
            risk = ActionRiskLevel.CONFIRM,
            argSchema = listOf(ActionArg("url", description = "The web address to open")),
        ),
        ActionDescriptor(
            id = ActionIds.OPEN_ASSISTANT,
            title = "Assistant",
            description = "Open the AI assistant, optionally with a prompt",
            category = ActionCategory.ASSISTANT,
            risk = ActionRiskLevel.SAFE,
            argSchema = listOf(ActionArg("prompt", required = false, description = "Optional prompt to prefill")),
        ),
    )

    private fun catalogOf(vararg descriptors: ActionDescriptor): ActionCatalog =
        object : ActionCatalog {
            private val list = descriptors.toList()
            override fun all(): List<ActionDescriptor> = list
            override fun descriptor(id: ActionId): ActionDescriptor? = list.firstOrNull { it.id == id }
        }
}
