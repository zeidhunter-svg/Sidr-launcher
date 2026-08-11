package com.sidr.launcher.feature.assistant

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads this module's **shipped English** string resources straight off disk (I18N-1 Task 10).
 *
 * `:feature:assistant` has no Robolectric host, so a plain JVM test cannot resolve a `@StringRes` id
 * to its text. Extracting copy into resources would therefore have silently turned every
 * "this sentence says X" assertion into a vacuous one. This helper keeps them real: the tests still
 * pin the exact words that ship, and now they pin them in the file that actually ships rather than in
 * a Kotlin constant that no longer exists.
 *
 * Reads `values/` only — the locale-completeness of `values-ru`/`values-tr` is `:app`'s
 * `LocaleCompletenessGuardTest`, not this module's job.
 *
 * Same file-walking approach as that guard; unit tests run with the module directory as their working
 * directory, with a repo-root fallback so a different runner fails loudly rather than vacuously.
 */
internal object AssistantStrings {

    private val english: Map<String, String> by lazy {
        val dir = valuesDir()
        val declarations = listOf("strings.xml", "strings_locked.xml")
            .map { name ->
                File(dir, name).also {
                    check(it.isFile) { "missing string resource file: ${it.absolutePath}" }
                }
            }
            .flatMap { file -> parse(file) }

        // A key declared in both strings.xml and strings_locked.xml would otherwise resolve to
        // whichever file is read last — a silent Class-B-vs-ordinary mix-up. Fail loudly instead.
        val duplicates = declarations.groupBy { it.name }.filterValues { it.size > 1 }
        check(duplicates.isEmpty()) {
            "duplicate string keys in feature/assistant/src/main/res/values: " +
                duplicates.entries.joinToString("; ") { (key, decls) ->
                    "'$key' declared in ${decls.map { it.file }}"
                }
        }

        declarations.associate { it.name to it.value }
    }

    /** The shipped English value for [name]; fails loudly rather than returning a placeholder. */
    fun en(name: String): String = english[name]
        ?: error("no English value for '$name' in feature/assistant/src/main/res/values")

    /** Every key declared in this module's English resources (`strings.xml` + `strings_locked.xml`). */
    fun englishKeys(): Set<String> = english.keys

    private fun valuesDir(): File {
        val candidates = listOf(
            File("src/main/res/values"),
            File("feature/assistant/src/main/res/values"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error(
                "cannot locate feature/assistant values dir from working directory " +
                    "${File("").absolutePath}; tried ${candidates.map { it.absolutePath }}",
            )
    }

    private data class Declaration(val name: String, val value: String, val file: String)

    private fun parse(file: File): List<Declaration> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { i ->
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name")
            Declaration(name, el.textContent.unescapeAndroidResource(name), file.name)
        }
    }

    /**
     * Undoes Android's resource escaping, which is **not** XML escaping and therefore survives the XML
     * parser untouched.
     *
     * Single-pass on purpose: a chain of `replace` calls mangles `\\n` (a literal backslash followed by
     * `n`) into a newline. Any escape this helper does not model — and `%%`, whose resolution depends on
     * whether format arguments are supplied at the call site — fails loudly, because a mangled value
     * that a later test then writes an expectation against is worse than a red build.
     */
    private fun String.unescapeAndroidResource(name: String): String {
        check(!contains("%%")) {
            "resource '$name' contains '%%', a format-time escape this helper does not model " +
                "(getString(id) keeps it, getString(id, args) resolves it) — model it deliberately " +
                "before asserting on such a value"
        }
        val out = StringBuilder(length)
        var i = 0
        while (i < length) {
            val c = this[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            check(i + 1 < length) { "resource '$name' ends with a dangling backslash" }
            when (val escaped = this[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                '\\' -> out.append('\\')
                '\'' -> out.append('\'')
                '"' -> out.append('"')
                '@' -> out.append('@')
                '?' -> out.append('?')
                else -> error(
                    "resource '$name' uses the escape '\\$escaped', which this helper does not model — " +
                        "handle it here rather than asserting against a mangled value",
                )
            }
            i += 2
        }
        return out.toString()
    }
}
