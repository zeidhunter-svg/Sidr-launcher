package com.sidr.launcher.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * I18N-1 barrier 2 (spec §10.3). Walks every module's `res/values*` dirs for `strings*.xml` and fails the build on:
 *  - a key present in `values` and missing from a MAIN locale (ru, tr);
 *  - a `translatable="false"` key that appears in any translated folder;
 *  - a key whose name does not carry its module prefix, or that collides across modules
 *    (`android.nonTransitiveRClass=true` hides the collision until the app merge picks a winner);
 *  - a translation whose placeholder set differs from the base - `%1$s` dropped in `ru` is an
 *    `IllegalFormatException` at runtime, which no screenshot can catch;
 *  - a `<plurals>` missing a quantity form its locale requires (ru: one/few/many/other; tr: one/other).
 *
 * Long-tail locales (anything outside MAIN_LOCALES) only WARN: a barrier nobody can satisfy is a
 * barrier somebody deletes.
 *
 * Unit-test working directory is the module dir (`app`), so the repo root is `..`.
 */
class LocaleCompletenessGuardTest {

    private val mainLocales = listOf("ru", "tr")

    private val requiredQuantities = mapOf(
        "ru" to setOf("one", "few", "many", "other"),
        "tr" to setOf("one", "other"),
    )

    /** Module res dir (relative to repo root) -> mandatory key prefix. */
    private val modulePrefixes = mapOf(
        "core/ui" to "ui_",
        "feature/launcher" to "launcher_",
        "feature/settings" to "settings_",
        "feature/prayer" to "prayer_",
        "feature/permission_education" to "perm_",
        "feature/assistant" to "assistant_",
        "app" to "app_",
    )

    private data class Entry(val name: String, val translatable: Boolean, val placeholders: Set<String>)

    private val placeholderPattern = Regex("""%(\d+\$)?[a-zA-Z]""")

    private fun parse(file: File): Pair<Map<String, Entry>, Map<String, Set<String>>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = mutableMapOf<String, Entry>()
        val plurals = mutableMapOf<String, Set<String>>()

        val stringNodes = doc.getElementsByTagName("string")
        for (i in 0 until stringNodes.length) {
            val el = stringNodes.item(i) as Element
            val name = el.getAttribute("name")
            strings[name] = Entry(
                name = name,
                translatable = el.getAttribute("translatable") != "false",
                placeholders = placeholderPattern.findAll(el.textContent).map { it.value }.toSet(),
            )
        }

        val pluralNodes = doc.getElementsByTagName("plurals")
        for (i in 0 until pluralNodes.length) {
            val el = pluralNodes.item(i) as Element
            val items = el.getElementsByTagName("item")
            val quantities = mutableSetOf<String>()
            for (j in 0 until items.length) {
                quantities += (items.item(j) as Element).getAttribute("quantity")
            }
            plurals[el.getAttribute("name")] = quantities
        }
        return strings to plurals
    }

    private fun resFiles(moduleDir: File, locale: String?): List<File> {
        val dirName = if (locale == null) "values" else "values-$locale"
        val dir = File(moduleDir, "src/main/res/$dirName")
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.startsWith("strings") && f.extension == "xml" }
            ?.sortedBy { it.name }.orEmpty()
    }

    @Test fun main_locales_are_complete_and_consistent() {
        val repoRoot = File("..")
        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val seenKeys = mutableMapOf<String, String>()   // key -> module that declared it

        for ((modulePath, prefix) in modulePrefixes) {
            val moduleDir = File(repoRoot, modulePath)
            val baseFiles = resFiles(moduleDir, null)
            if (baseFiles.isEmpty()) continue

            val base = mutableMapOf<String, Entry>()
            val basePlurals = mutableMapOf<String, Set<String>>()
            baseFiles.forEach { f -> parse(f).let { base += it.first; basePlurals += it.second } }

            base.keys.forEach { key ->
                if (!key.startsWith(prefix)) {
                    failures += "$modulePath: key '$key' must start with '$prefix'"
                }
                val owner = seenKeys.put(key, modulePath)
                if (owner != null) {
                    failures += "key '$key' declared in both $owner and $modulePath - " +
                        "resource merge silently keeps one"
                }
            }

            for (locale in mainLocales) {
                val translated = mutableMapOf<String, Entry>()
                val translatedPlurals = mutableMapOf<String, Set<String>>()
                resFiles(moduleDir, locale).forEach { f ->
                    parse(f).let { translated += it.first; translatedPlurals += it.second }
                }

                base.values.filter { it.translatable }.forEach { entry ->
                    val t = translated[entry.name]
                    if (t == null) {
                        failures += "$modulePath: '${entry.name}' missing from values-$locale"
                    } else if (t.placeholders != entry.placeholders) {
                        failures += "$modulePath: '${entry.name}' placeholder mismatch in values-$locale " +
                            "- base ${entry.placeholders}, $locale ${t.placeholders}"
                    }
                }

                base.values.filterNot { it.translatable }.forEach { entry ->
                    if (translated.containsKey(entry.name)) {
                        failures += "$modulePath: '${entry.name}' is translatable=\"false\" but appears " +
                            "in values-$locale"
                    }
                }

                basePlurals.keys.forEach { name ->
                    val quantities = translatedPlurals[name]
                    if (quantities == null) {
                        failures += "$modulePath: plurals '$name' missing from values-$locale"
                    } else {
                        val missing = requiredQuantities.getValue(locale) - quantities
                        if (missing.isNotEmpty()) {
                            failures += "$modulePath: plurals '$name' in values-$locale is missing " +
                                "quantity forms $missing"
                        }
                    }
                }
            }

            // Long-tail locales: warn only.
            val resRoot = File(moduleDir, "src/main/res")
            resRoot.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
                .map { it.name.removePrefix("values-") }
                .filter { it !in mainLocales && it.length <= 3 }
                .forEach { locale ->
                    val translated = mutableMapOf<String, Entry>()
                    resFiles(moduleDir, locale).forEach { f -> translated += parse(f).first }
                    val missing = base.values.filter { it.translatable && !translated.containsKey(it.name) }
                    if (missing.isNotEmpty()) {
                        warnings += "$modulePath: values-$locale is missing ${missing.size} keys"
                    }
                }
        }

        warnings.forEach { println("WARNING (long-tail locale): $it") }
        assertTrue(
            "Locale completeness failures (spec §10.3):\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }
}
