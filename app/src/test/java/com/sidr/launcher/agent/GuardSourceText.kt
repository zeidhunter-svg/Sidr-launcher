package com.sidr.launcher.agent

import java.io.File

// Shared plumbing for the two `:app` guards that scan Kotlin sources as TEXT
// (AgentVocabularyGuardTest, ToolExecutorCallSiteGuardTest): comment stripping, and the derivation of
// which directories of a KMP module hold production code.
//
// It lives here, package-level in `app/src/test`, rather than in `:core:testing`: both guards already
// sit in `com.sidr.launcher.agent`, so sharing needs no module and no build change, and helpers that
// exist only to serve two guards have no business on the production test-fixture classpath.
//
// (Line comments, not a KDoc block: a `/** */` here documents nothing — there is no file-level
// declaration for it to attach to.)
//
// Before Task 13's F1-F5 round the two guards disagreed about comments: the vocabulary guard stripped
// them, the call-site guard did a raw `contains` over `readLines()`. Nothing tripped it then, but a
// future KDoc spelling `toolExecutor.invoke(` in prose would have turned a guard red on correct code,
// and F1's declaration scan would have been red on the KDoc of `ToolExecutor` itself.

/**
 * Removes `//` tails and `/* … */` blocks (KDoc included, and Kotlin block comments nest) while
 * preserving line breaks, so a hit still reports the line it was on.
 *
 * **Named limitation:** it does not model string literals, so a comment opener inside one is
 * treated as a comment opener. That direction is one-way — it can only *hide* text from the scan,
 * never invent a hit — so a guard built on it can in principle be evaded by code no reviewer would
 * write, and can never fail on correct code. A guard that is honest about false negatives beats one
 * that needs a Kotlin parser nobody will maintain.
 */
internal fun stripComments(source: String): String {
    val out = StringBuilder()
    var i = 0
    var depth = 0
    while (i < source.length) {
        val two = if (i + 1 < source.length) source.substring(i, i + 2) else ""
        when {
            depth > 0 && two == "*/" -> { depth--; i += 2 }
            depth > 0 && two == "/*" -> { depth++; i += 2 }
            depth > 0 -> { if (source[i] == '\n') out.append('\n'); i++ }
            two == "/*" -> { depth++; i += 2 }
            two == "//" -> { while (i < source.length && source[i] != '\n') i++ }
            else -> { out.append(source[i]); i++ }
        }
    }
    return out.toString()
}

/**
 * The directories of a KMP module that can hold **production** Kotlin, derived from the filesystem
 * rather than hard-coded — `<moduleSrc>/<sourceSet>/{kotlin,java}[/<packagePath>]` for every source
 * set that is not a test source set. Only directories that exist are returned.
 *
 * **Why derived.** Hard-coding `commonMain/kotlin` was a coverage hole that a reader could not see:
 * this module is `kotlin.multiplatform` + `com.android.library`, and a file dropped in a source set
 * nobody listed still ships. Measured on `:domain`, 2026-08-22, by planting an ill-typed file in each
 * candidate directory and reading which compiler took it:
 *
 * | directory | taken by |
 * |---|---|
 * | `src/main/java`, `src/main/kotlin` | `:domain:compileDebugKotlinAndroid` |
 * | `src/androidMain/kotlin` | `:domain:compileDebugKotlinAndroid` |
 * | `src/jvmMain/kotlin`, `src/jvmMain/java` | `:domain:compileKotlinJvm` |
 * | `src/androidMain/java`, `src/commonMain/java` | nobody, today |
 *
 * So AGP's plain `src/main/…` **is** part of this KMP module's android compilation, and `"main"` is
 * not a `…Main` name. Both `kotlin/` and `java/` are returned for every source set, including the two
 * that no compiler reads today: over-covering costs a scan of an empty directory, under-covering costs
 * a guard that reports safety it never checked.
 *
 * **The exclusion is case-insensitive, and that is load-bearing.** AGP's unit-test directory is
 * `src/test`, and `"test".endsWith("Test")` is false — a case-sensitive rule would scan it. Verified
 * the same way: a file at `domain/src/test/java/…` is compiled by
 * `:domain:compileDebugUnitTestKotlinAndroid`. Excluded by this rule: `test`, `androidTest`,
 * `jvmTest`, `commonTest`, `androidUnitTest`, `androidInstrumentedTest`. Kept: `main`, `commonMain`,
 * `androidMain`, `jvmMain` and any future `<target>Main`.
 *
 * **Named limitations**, in the style of [stripComments]' own note — this reads a filesystem
 * convention, not the build model:
 *  - a source set named neither `…test` nor for production — AGP's `src/testFixtures` is the real
 *    example — would be scanned as production;
 *  - a directory added by an explicit `srcDir(…)` in a build script, or a language directory other
 *    than `kotlin/`/`java/`, is invisible to this and always will be.
 *
 * Both directions are visible to a reader, and the second is why the callers keep a non-vacuity
 * assertion that names files they expect to have found.
 */
internal fun kmpProductionRoots(moduleSrc: File, packagePath: String? = null): List<File> =
    (moduleSrc.listFiles()?.sortedBy { it.name } ?: emptyList())
        .filter { it.isDirectory && !it.name.lowercase().endsWith("test") }
        .flatMap { sourceSet ->
            listOf("kotlin", "java").map { language ->
                val languageRoot = File(sourceSet, language)
                if (packagePath == null) languageRoot else File(languageRoot, packagePath)
            }
        }
        .filter { it.isDirectory }
