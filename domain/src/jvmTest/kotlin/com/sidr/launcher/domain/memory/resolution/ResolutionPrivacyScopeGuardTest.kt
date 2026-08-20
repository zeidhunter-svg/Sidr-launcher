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
        // Repointed 2026-08-20 (A0 fix-privacy-guard), then widened same day (review item
        // IMPORTANT 6): commit 23000ae ("extract app launch and app list from LauncherViewModel",
        // Task 3 of the A0 extraction) moved recordChoiceIfPending()/launch() out of
        // LauncherViewModel.kt and into the new LauncherAppLaunch.kt collaborator. The record call
        // and its gate moved together, byte-for-byte, so the invariant this guard protects was never
        // actually broken — only its file key went stale (fixed by pointing at LauncherAppLaunch.kt
        // alone, plus the `inputs.file` declaration in domain/build.gradle.kts).
        //
        // That single-file fix left a blind spot: LauncherViewModel.kt still injects
        // `recordResolutionChoice: RecordResolutionChoiceUseCase` (it hands the instance to
        // LauncherAppLaunch's constructor) and is a perfectly legal place for someone to add a
        // second, UNGATED `recordResolutionChoice.record(...)` call directly — the guard, watching
        // only LauncherAppLaunch.kt, would still see exactly one correctly-gated call there and stay
        // green. Before the Task 3 extraction the one watched file WAS the one file holding the
        // dependency; after it, the dependency is in scope in two files, so per-file semantics were
        // preserved but coverage silently shrank to one of them.
        //
        // Fix: scan the whole feature/launcher/src/main tree (not just one hard-coded file) for
        // record calls, but keep the ordering check PER FILE — gate and call must be in the SAME
        // file — rather than weakening it into "the gate string exists somewhere in the tree", which
        // would pass even with the gate in one file and an ungated call in another.
        val root = repoRoot()
        val sourceDir = File(root, "feature/launcher/src/main")
        val ktFiles = sourceDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(
            "the scan walked zero .kt files under ${sourceDir.relativeTo(root).path} - the guard " +
                "would vacuously pass; check the directory path",
            ktFiles.isNotEmpty(),
        )

        val recordCallPattern = Regex("""recordResolutionChoice\.record\(""")
        val callSites = ktFiles.flatMap { file ->
            val text = file.readText()
            recordCallPattern.findAll(text).map { match -> Triple(file, text, match.range.first) }
        }

        assertEquals(
            "feature/launcher should have exactly one learned-resolution record call across its " +
                "whole source tree (found in: ${callSites.joinToString { (file, _, _) -> file.relativeTo(root).path }})",
            1,
            callSites.size,
        )

        val (owningFile, ownerText, callStart) = callSites.single()
        val tokenGateIndex = ownerText.indexOf("if (!token.isAppAmbiguityFlow) return")

        assertTrue(
            "A learned-resolution record call must be guarded by isAppAmbiguityFlow in the SAME " +
                "file (${owningFile.relativeTo(root).path})",
            tokenGateIndex >= 0 && tokenGateIndex < callStart,
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
