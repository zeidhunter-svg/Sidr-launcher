package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.domain.ai.OutboundContextPolicy
import com.sidr.launcher.domain.ai.OutboundContextPolicy.AllowedContext
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AliasPrivacyScopeGuardTest {

    @Test
    fun `explicit aliases keep outbound allow-list byte-for-byte unchanged`() {
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
    fun `alias memory has no outbound or generative dependency`() {
        val root = repoRoot()
        val sourceDir = File(root, "domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/alias")
        val forbiddenTerms = listOf(
            "AiRequest",
            "GenerativeAiEngine",
            "GenerateReplyUseCase",
            "PromptContextBuilder",
            "CommandPlanner",
            "CatalogSchemaRenderer",
            "com.sidr.launcher.domain.ai",
        )

        val violations = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val text = file.readText()
                forbiddenTerms
                    .filter { term -> text.contains(term) }
                    .map { term -> "${file.relativeTo(root).path} contains $term" }
            }
            .toList()

        assertTrue(
            "Alias memory must stay local-only; outbound/generative dependency found:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `saving no-ops on blank and over-length phrase`() = runTest {
        val store = FakeAliasStore()
        val save = SaveAliasUseCase(store)

        assertTrue(save.save("   ", AliasTarget.App("com.x")) is OperationResult.Success<*>)
        assertTrue(
            save.save(
                "a".repeat(MAX_ALIAS_PHRASE_LENGTH + 1),
                AliasTarget.App("com.x"),
            ) is OperationResult.Success<*>,
        )
        assertEquals(0, store.observeAll().first().size)
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return checkNotNull(dir) { "Could not locate repo root from ${System.getProperty("user.dir")}" }
    }
}
