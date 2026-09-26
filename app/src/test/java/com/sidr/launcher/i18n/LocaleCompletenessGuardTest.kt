package com.sidr.launcher.i18n

import org.junit.Assert.assertEquals
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

    /**
     * Guard-the-guard (whole-branch review fix wave, item 2a — mirrors
     * `HardcodedUiTextGuardTest.scoped_roots_cover_every_ui_module`). [modulePrefixes] is a
     * hand-written 7-entry map with no check against reality: a new module that ships
     * `res/values/strings*.xml` but is never added here is silently skipped by
     * [main_locales_are_complete_and_consistent] entirely - it could ship English-only in `ru`/`tr`
     * with every barrier reporting green. This test parses `settings.gradle.kts` for every included
     * module and fails if any module has a base `res/values/strings*.xml` file but no entry in
     * [modulePrefixes].
     */
    @Test fun module_prefixes_cover_every_module_that_ships_strings() {
        val repoRoot = File("..")
        val settingsFile = File(repoRoot, "settings.gradle.kts")
        val includePattern = Regex("""include\(":([^"]+)"\)""")
        val includedModules = includePattern.findAll(settingsFile.readText())
            .map { it.groupValues[1].replace(':', '/') }
            .toList()

        val uncovered = includedModules.filter { modulePath ->
            modulePath !in modulePrefixes && resFiles(File(repoRoot, modulePath), null).isNotEmpty()
        }

        assertTrue(
            "Module(s) ship res/values/strings*.xml but are not in `modulePrefixes` - " +
                "LocaleCompletenessGuardTest would silently skip them entirely, allowing English-only " +
                "translations to ship gate-green (spec §10.3): $uncovered",
            uncovered.isEmpty(),
        )
    }

    /**
     * Whole-branch review fix wave, item 2c. Four independent locale lists exist with nothing
     * cross-checking them: `app/src/main/res/xml/locales_config.xml` (the OS locale-config manifest),
     * `SettingsScreen.kt`'s in-app language switcher, this class's own [mainLocales], and
     * `app/build.gradle.kts`'s `checkOwnerReviewedLocaleStrings` `locales` list. Adding a locale needs
     * four coordinated edits today and fails silently in a different way each time one is missed. This
     * does not unify them into one shared constant (that needs a Gradle-file/XML-resource/Kotlin-source
     * bridge disproportionate to a fix wave) - it only proves they currently agree, so drift is caught
     * the moment any one of the four changes without the others.
     *
     * [mainLocales]/the gate's `locales` are deliberately the "translated, non-base" set (`ru`, `tr`) -
     * `en` is the base `values/` locale and needs no translation completeness check - while
     * `locales_config.xml`/the switcher are the "every locale the app can render" set (`en`, `ru`,
     * `tr`). The third assertion below ties those two shapes together: translated == supported - base.
     */
    @Test fun locale_lists_agree_across_the_four_sources() {
        val repoRoot = File("..")

        val localesConfigFile = File(repoRoot, "app/src/main/res/xml/locales_config.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(localesConfigFile)
        val localeNodes = doc.getElementsByTagName("locale")
        val configuredLocales = (0 until localeNodes.length)
            .map { (localeNodes.item(it) as Element).getAttribute("android:name") }
            .toSet()

        val settingsScreenFile = File(
            repoRoot,
            "feature/settings/src/main/java/com/sidr/launcher/feature/settings/SettingsScreen.kt",
        )
        val tagPattern = Regex(""""([a-z]*)"\s+to\s+R\.string\.settings_language_""")
        val switcherTags = tagPattern.findAll(settingsScreenFile.readText())
            .map { it.groupValues[1] }
            .filter { it.isNotEmpty() } // "" = follow-system, not a locale
            .toSet()

        val translatedLocales = mainLocales.toSet()

        val buildFile = File(repoRoot, "app/build.gradle.kts")
        val gateLocalesMatch = Regex("""val locales = listOf\(([^)]*)\)""").find(buildFile.readText())
            ?: error(
                "Could not find `val locales = listOf(...)` in app/build.gradle.kts - has " +
                    "checkOwnerReviewedLocaleStrings moved or been renamed?",
            )
        val gateLocales = gateLocalesMatch.groupValues[1]
            .split(",")
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }
            .toSet()

        assertEquals(
            "locales_config.xml's supported locales must equal the settings switcher's offered tags " +
                "(excluding the follow-system \"\" option)",
            configuredLocales, switcherTags,
        )
        assertEquals(
            "LocaleCompletenessGuardTest.mainLocales must equal checkOwnerReviewedLocaleStrings's " +
                "`locales` in app/build.gradle.kts",
            translatedLocales, gateLocales,
        )
        assertEquals(
            "the translated (non-base) locales must equal the supported locales minus the base 'en'",
            translatedLocales, configuredLocales - "en",
        )
    }
}
