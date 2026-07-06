package com.sidr.launcher.data.repository.action

import com.sidr.launcher.domain.ai.OutboundContextPolicy
import com.sidr.launcher.domain.ai.router.CatalogSchemaRenderer
import org.junit.Assert.fail
import org.junit.Test

/**
 * AIL-4 outbound-privacy guard over the **real, shipped** catalog. `RouterOutboundGuardTest` (in
 * `:domain`) proves the allow-list widening and the renderer's structural purity against representative
 * descriptors; this scans the exact `DefaultActionCatalog` that goes on the wire, so a future
 * descriptor whose title/description/arg text names a forbidden context source (or a credential) fails
 * closed here.
 */
class DefaultActionCatalogRouterSchemaGuardTest {

    @Test
    fun `rendered production schema names no forbidden context source and no credential`() {
        val rendered = CatalogSchemaRenderer.render(DefaultActionCatalog())
        val forbidden = OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS
            .filter { rendered.contains(it, ignoreCase = true) }
        val credential = OutboundContextPolicy.CREDENTIAL_TERMS
            .filter { rendered.contains(it, ignoreCase = true) }
        if (forbidden.isNotEmpty() || credential.isNotEmpty()) {
            fail(
                "DefaultActionCatalog's outbound router schema leaks term(s) — " +
                    "forbidden=$forbidden credential=$credential",
            )
        }
    }
}
