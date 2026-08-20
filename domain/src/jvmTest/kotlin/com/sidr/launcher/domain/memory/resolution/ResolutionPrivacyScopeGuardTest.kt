package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.ai.OutboundContextPolicy
import com.sidr.launcher.domain.ai.OutboundContextPolicy.AllowedContext
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResolutionPrivacyScopeGuardTest {

    private fun app(packageName: String) = ResolvedTarget.App(packageName)

    private val candidates = CandidateSet(listOf(app("com.a"), app("com.b")))

    @Test
    fun `learned resolutions keep outbound allow-list byte-for-byte unchanged`() {
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
    fun `resolution memory has no outbound or generative dependency`() {
        val root = repoRoot()
        val sourceDir = File(root, "domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/resolution")
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
            "Resolution memory must stay local-only; outbound/generative dependency found:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `recording call is gated by an app-ambiguity learning token`() {
        // Repointed 2026-08-20 (A0 fix-privacy-guard): commit 23000ae ("extract app launch and app
        // list from LauncherViewModel", Task 3 of the A0 extraction) moved
        // recordChoiceIfPending()/launch() out of LauncherViewModel.kt and into the new
        // LauncherAppLaunch.kt collaborator. The record call and its gate moved together, byte-for-
        // byte, so the invariant this guard protects was never actually broken — only its file key
        // went stale, and because that key points outside :domain's own source sets, Gradle had no
        // way to notice the guard had stopped running (see the `inputs.file` declaration in
        // domain/build.gradle.kts this fix adds alongside it).
        val source = File(
            repoRoot(),
            "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherAppLaunch.kt",
        ).readText()
        val recordCalls = Regex("""recordResolutionChoice\.record\(""").findAll(source).toList()
        val tokenGateIndex = source.indexOf("if (!token.isAppAmbiguityFlow) return")

        assertEquals(
            "LauncherAppLaunch should have exactly one learned-resolution record call",
            1,
            recordCalls.size,
        )
        assertTrue(
            "A learned-resolution record call must be guarded by isAppAmbiguityFlow",
            tokenGateIndex >= 0 && tokenGateIndex < recordCalls.single().range.first,
        )
    }

    @Test
    fun `recording no-ops for empty and over-length queries`() = runTest {
        val store = FakeResolutionPreferenceStore()
        val useCase = RecordResolutionChoiceUseCase(store)
        val invalidKeys = listOf(
            CapabilityKey(ActionIds.LAUNCH_APP, ""),
            CapabilityKey(ActionIds.LAUNCH_APP, "   "),
            CapabilityKey(ActionIds.LAUNCH_APP, "a".repeat(MAX_QUERY_LENGTH + 1)),
        )

        invalidKeys.forEach { key ->
            val result = useCase.record(key, ResolutionContext.None, app("com.a"), candidates)
            assertTrue(result is OperationResult.Success)
        }

        assertTrue(store.observeAll().first().isEmpty())
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return checkNotNull(dir) { "Could not locate repo root from ${System.getProperty("user.dir")}" }
    }
}
