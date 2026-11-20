package com.sidr.launcher.doctrine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * **The app's memory stays on the device: left out of Android Auto Backup and of device-to-device
 * transfer** (`DOC-AML-4`; owner answer «a», 2026-11-19; A4′ phase 4 Task 10, decisions B-16/B-17).
 *
 * The learned-plan cache, learned choices, aliases and the agent sessions are command text and user
 * habit. `android:allowBackup="true"` with no rules would copy every Room database and every DataStore
 * file to the user's cloud account and to a new phone. The cost the owner accepted: what is not restored
 * on a new phone is every Room database and every DataStore file — the memory **and every setting**,
 * `flag_local_only` included (it returns to its default).
 *
 * **B-16 — domains, not file names.** The rules leave out the whole `database` domain and the
 * `datastore/` directory of the `file` domain, so a database or DataStore added later is left out
 * without an edit here.
 *
 * **B-17 — the guard holds the reach, not only the XML.** The rules cover two storage shapes only:
 * `Room.databaseBuilder` (→ `databases/`) and `preferencesDataStoreFile` (→ `files/datastore/`). A
 * production file that opened storage any other way would write memory the rules do not cover, so any
 * such call is red until the rules and this guard are widened together. The forbidden tokens are case-
 * sensitive substrings, chosen to catch the Java-getter spelling too (`getFilesDir(` — Kotlin has no
 * property form for `getExternalFilesDir(String?)`, so `ExternalFilesDir` is the token), the
 * `SharedPreferences(` family (`getSharedPreferences(`, `PreferenceManager.getDefaultSharedPreferences(`),
 * the device-protected context (`createDeviceProtectedStorageContext(` — a database built on it lands in
 * `device_database`, outside the rules), the `root` domain (`dataDir`, `getDataDir(`) and
 * `moveDatabaseFrom(`.
 *
 * **What the scan holds.** Every `.kt` under a `src/main` or `src/commonMain` of the repository, walked
 * like `OneModelPathGuardTest` (canonical root; `build` and every dot-directory pruned), read with
 * comments stripped (line numbers preserved, so the message still says `file:line`). Two controls: the
 * scan must reach more than a hundred files, and each allowed shape must be found at least once in
 * stripped code.
 *
 * **Named limits, not implied absent.** `.java` sources are not read. Stores owned by a library are not
 * in the scan: ProfileInstaller's markers in `files/`, WorkManager's database in `no_backup/`. A
 * `Room.databaseBuilder` given an absolute name escapes `databases/`. A `File(` built from a root the
 * list does not name (`cacheDir`, say — not backed up anyway) is not seen. The comment stripper is two
 * regexes, not a lexer: a line-comment opener inside a string literal (an https address) blinds the rest
 * of that line. And only `src/main` and `src/commonMain` are scanned.
 *
 * The XML is read as text from the source tree (the same limit as `ToolPermissionManifestGuardTest`:
 * `:app` has no Robolectric), so this holds the **declared** rules, not the merged manifest.
 */
class BackupRulesGuardTest {

    private val repoRoot = File("..")
    private val appMain = File(repoRoot, "app/src/main")

    private val wantedExcludes = setOf("database" to ".", "file" to "datastore/")

    private fun parse(file: File): Element {
        assertTrue("missing ${file.path}", file.isFile)
        val factory = DocumentBuilderFactory.newInstance() // not namespace-aware: `android:x` is one name
        return factory.newDocumentBuilder().parse(file).documentElement
    }

    private fun children(parent: Element, tag: String): List<Element> =
        (0 until parent.childNodes.length).map { parent.childNodes.item(it) }
            .filterIsInstance<Element>().filter { it.tagName == tag }

    private fun excludesOf(section: Element): Pair<Set<Pair<String, String>>, Int> {
        val excludes = children(section, "exclude").map { it.getAttribute("domain") to it.getAttribute("path") }
        val includes = children(section, "include").size
        return excludes.toSet() to includes
    }

    @Test
    fun `the manifest points backup and device transfer at the rules`() {
        val application = children(parse(File(appMain, "AndroidManifest.xml")), "application").single()
        assertEquals("@xml/data_extraction_rules", application.getAttribute("android:dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.getAttribute("android:fullBackupContent"))
    }

    @Test
    fun `cloud backup and device transfer leave out every database and every DataStore file`() {
        val root = parse(File(appMain, "res/xml/data_extraction_rules.xml"))
        assertEquals("data-extraction-rules", root.tagName)
        for (name in listOf("cloud-backup", "device-transfer")) {
            val section = children(root, name).single()
            val (excludes, includes) = excludesOf(section)
            assertEquals("<$name> excludes", wantedExcludes, excludes)
            assertEquals("<$name> has no <include>", 0, includes)
        }
    }

    @Test
    fun `before Android 12 the same two are left out`() {
        val root = parse(File(appMain, "res/xml/backup_rules.xml"))
        assertEquals("full-backup-content", root.tagName)
        val (excludes, includes) = excludesOf(root)
        assertEquals("<full-backup-content> excludes", wantedExcludes, excludes)
        assertEquals("<full-backup-content> has no <include>", 0, includes)
    }

    /** Storage-opening calls the rules do NOT cover. */
    private val forbidden = listOf(
        "SharedPreferences(",
        "openFileOutput(",
        "dataStoreFile(",
        "getDir(",
        "filesDir",
        "getFilesDir(",
        "ExternalFilesDir",
        "noBackupFilesDir",
        "openOrCreateDatabase(",
        "createDeviceProtectedStorageContext(",
        "moveDatabaseFrom(",
        "dataDir",
        "getDataDir(",
    )

    /** The two shapes whose files the rules cover; the control needs each found at least once. */
    private val allowed = listOf("Room.databaseBuilder(", "preferencesDataStoreFile(")

    // Canonical on purpose, as in OneModelPathGuardTest: `File("..")` is NAMED "..", and the dot-directory
    // filter below would skip the root itself.
    private val canonicalRoot = repoRoot.canonicalFile

    /**
     * Comments out, line numbers kept: each block comment is replaced by its own newlines, so a hit still
     * reports the line it is on. Copy of OneModelPathGuardTest's two regexes, with that one difference.
     */
    private fun strip(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)) { m -> m.value.filter { it == '\n' } }
            .replace(Regex("//[^\n]*"), "")

    /** Production Kotlin: every module's `src/main` (and KMP `commonMain`); `build` and dot-directories pruned. */
    private fun productionKotlin(): List<File> =
        canonicalRoot.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                val path = f.invariantSeparatorsPath
                "/src/main/" in path || "/src/commonMain/" in path
            }
            .toList()

    @Test
    fun `every store the app opens lives where the rules leave it out`() {
        val files = productionKotlin()
        assertTrue("scan reached no production file", files.size > 100)
        val offenders = mutableListOf<String>()
        val allowedFound = allowed.associateWith { 0 }.toMutableMap()
        for (file in files) {
            strip(file.readText()).lines().forEachIndexed { index, line ->
                forbidden.filter { it in line }.forEach { shape ->
                    offenders += "${file.invariantSeparatorsPath}:${index + 1} uses `$shape`"
                }
                allowed.filter { it in line }.forEach { allowedFound[it] = allowedFound.getValue(it) + 1 }
            }
        }
        assertEquals(
            "storage opened where the backup rules do not reach — widen the rules and this guard together",
            emptyList<String>(), offenders,
        )
        allowed.forEach { shape ->
            assertTrue("control: `$shape` must be found at least once", allowedFound.getValue(shape) >= 1)
        }
    }
}
