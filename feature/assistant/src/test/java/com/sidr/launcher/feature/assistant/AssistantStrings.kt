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
        listOf("strings.xml", "strings_locked.xml")
            .map { name ->
                File(dir, name).also {
                    check(it.isFile) { "missing string resource file: ${it.absolutePath}" }
                }
            }
            .flatMap { parse(it).entries }
            .associate { it.key to it.value }
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

    private fun parse(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val el = nodes.item(i) as Element
            el.getAttribute("name") to el.textContent.unescapeAndroidResource()
        }
    }

    /** Android's resource escaping (`\'`, `\"`) is not XML escaping, so the parser leaves it in place. */
    private fun String.unescapeAndroidResource(): String = replace("\\'", "'").replace("\\\"", "\"")
}
