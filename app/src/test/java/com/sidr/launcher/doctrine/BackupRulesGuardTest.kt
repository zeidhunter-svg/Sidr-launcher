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
 * file to the user's cloud account and to a new phone. The cost the owner accepted: memory is not
 * restored on a new phone.
 *
 * **B-16 — domains, not file names.** The rules leave out the whole `database` domain and the
 * `datastore/` directory of the `file` domain, so a database or DataStore added later is left out
 * without an edit here.
 *
 * **B-17 — the guard holds the reach, not only the XML.** The rules cover two storage shapes only:
 * `Room.databaseBuilder` (→ `databases/`) and `preferencesDataStoreFile` (→ `files/datastore/`). A
 * production file that opened storage any other way (`getSharedPreferences`, `openFileOutput`, a path
 * under `filesDir` …) would write memory the rules do not cover, so any such call is red until the
 * rules and this guard are widened together.
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
        "getSharedPreferences(",
        "openFileOutput(",
        "dataStoreFile(",
        "getDir(",
        "filesDir",
        "externalFilesDir",
        "noBackupFilesDir",
        "openOrCreateDatabase(",
    )

    /** The two shapes whose files the rules cover; the control needs each found at least once. */
    private val allowed = listOf("Room.databaseBuilder(", "preferencesDataStoreFile(")

    /** Production Kotlin: every module's `src/main` (and KMP `commonMain`), `build/` excluded. */
    private fun productionKotlin(): List<File> =
        repoRoot.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".git" && it.name != ".gradle" && it.name != ".superpowers" }
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
            file.readLines().forEachIndexed { index, line ->
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
