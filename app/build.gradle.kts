import org.w3c.dom.Element
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.sidr.launcher"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sidr.launcher"
        minSdk = 28
        // Этап 2.1 — held one level behind compileSdk: Robolectric 4.16.1 (current latest) caps
        // supported SDK at 36 and rejects targetSdkVersion=37. Revisit once Robolectric catches up.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

kotlin {
    jvmToolchain(17)
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:android"))
    implementation(project(":domain"))
    implementation(project(":data:ai-cloud"))
    implementation(project(":data:repository"))
    implementation(project(":data:prayer"))
    implementation(project(":feature:launcher"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:suggestions"))
    implementation(project(":feature:permission_education"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:prayer"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)

    // WorkManager + Hilt @HiltWorker support for the periodic suggestion precompute/usage cleanup
    // workers. HiltWorkerFactory and Configuration.Provider live here (composition root).
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.room.runtime)

    // Block K: the cloud-AI HttpClient is provided here (composition root) and injected into the
    // :data:ai-cloud engine, which stays Hilt-free. Only the client/engine types cross into :app DI.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)

    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.compose.ui.tooling)
}

baselineProfile {
    mergeIntoMain = true
}

// I18N-1 barrier 2 / release gate (spec §7.2, §10.4). Every `strings_locked.xml` shipped in a
// `values-<locale>` folder holds copy that needed owner sign-off before it was extracted (religious
// terminology, cloud disclosures, destructive-gate consequences...) - but every ru/tr translation in
// this block is an unreviewed agent draft (see docs/superpowers/specs/2026-08-11-i18n-1-owner-review.md).
// This task fails a release build until each such file's header comment carries the token
// OWNER-REVIEWED, added by hand once the owner has actually read and approved that file's text.
//
// Deliberately fail-closed on a POSITIVE marker, not fail-open on the word "DRAFT": only 4 of the 10
// locked-translated files in this repo happen to say "DRAFT" in their header today, even though all 10
// are equally unreviewed - gating on that word's absence would let 6 of them ship unchecked.
//
// F2 (fix round): the check is KEY-SET-driven, not filename-driven. A naive "does a
// `values-<locale>/strings_locked.xml` file exist" check protects a filename convention, not the
// copy - a future cleanup that folds a module's `values-ru/strings_locked.xml` into the sibling
// `values-ru/strings.xml` (LocaleCompletenessGuardTest stays green either way; it globs `strings*.xml`)
// would make the gate see zero `strings_locked.xml` files there and report zero offenders, shipping
// unreviewed copy. Instead: for every Class B key (locked but translated - no `translatable="false"`)
// declared in a module's base `values/strings_locked.xml`, find whichever `values-<locale>/strings*.xml`
// file actually holds that key's translation, and require THAT file to carry the marker - wherever the
// translation lives, the file holding it must be signed off.
//
// A key simply ABSENT from `values-<locale>` entirely is a different, already-covered failure: nothing
// unreviewed ships (the locale falls back to English), and LocaleCompletenessGuardTest already fails
// that state for a translatable key - do not "fix" this gate to also flag absence, that is not its job.
//
// Этап 4.0 (2026-08-19, fork F2): the marker is now CONTENT-BOUND, not merely PRESENT. The old check
// was `readText().contains("OWNER-REVIEWED")`, which had two holes that stayed dormant only because
// no signed string had ever been edited:
//   1. COVERAGE - a signed file could silently gain a new key, or have an existing sentence rewritten,
//      and keep shipping green under a signature that was given for different text. Этап 4.0 is the
//      first block in the project's history to change an already-signed Class B string
//      (settings_smart_routing_description -> settings_local_only_description, because ADR 1/4
//      inverted the flag the toggle describes), so the hole stopped being theoretical here.
//   2. PROSE - four of the locked files mention the literal token inside an ordinary sentence
//      ("see the OWNER-REVIEWED block below"). A file carrying only that sentence, and no signature
//      at all, passed the old `contains` check.
// The marker is therefore a signature over content: `OWNER-REVIEWED <yyyy-mm-dd> sha256:<16 hex>`,
// where the digest covers exactly the Class B keys THAT file holds, canonicalised as sorted
// `key\u0000value` lines. Edit a signed sentence and the digest no longer matches - the release gate
// goes red and names the new digest to be signed. This does NOT verify who typed the token (nothing
// in a repo can); it verifies that what ships is what was read. Provenance stays a written claim in
// each file's header, as it has since 2026-08-19.
val checkOwnerReviewedLocaleStrings by tasks.registering {
    group = "verification"
    description = "I18N-1 release gate (spec §7.2): fails until every translated locked-vocabulary " +
        "key's owning file carries an OWNER-REVIEWED signature whose digest matches its current text."

    val repoRoot = rootProject.projectDir
    val locales = listOf("ru", "tr")
    inputs.property("repoRootPath", repoRoot.path)

    doLast {
        // Maps every <string name="..."> in `file` to whether it is Class B (locked, translated - no
        // translatable="false"). Returns an empty map for a file that doesn't exist.
        fun classification(file: java.io.File): Map<String, Boolean> {
            if (!file.isFile) return emptyMap()
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            val result = LinkedHashMap<String, Boolean>()
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                result[el.getAttribute("name")] = el.getAttribute("translatable") != "false"
            }
            return result
        }

        // Key -> its literal text, read exactly the way LocaleCompletenessGuardTest reads it, so the
        // two guards can never disagree about what a string "is".
        fun values(file: java.io.File): Map<String, String> {
            if (!file.isFile) return emptyMap()
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            val result = LinkedHashMap<String, String>()
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                result[el.getAttribute("name")] = el.textContent
            }
            return result
        }

        // The signature payload: only the Class B keys this file actually holds, sorted by key so the
        // digest is independent of file order, NUL-joined so no key/value pair can be forged by moving
        // a separator into a translation.
        fun digestOf(file: java.io.File, classBKeys: Set<String>): String {
            val held = values(file).filterKeys { it in classBKeys }
            val canonical = held.keys.sorted().joinToString("\n") { "$it\u0000${held.getValue(it)}" }
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
                .take(16)
        }

        // `OWNER-REVIEWED 2026-08-19 sha256:0123456789abcdef`. The date is required but not checked
        // against anything - it is provenance for a human reader, not an input to the verdict.
        val signature = Regex("""OWNER-REVIEWED\s+\d{4}-\d{2}-\d{2}\s+sha256:([0-9a-f]{16})""")

        val offenders = sortedSetOf<String>()

        repoRoot.walkTopDown()
            .onEnter { dir -> dir.name != "build" && dir.name != ".git" && dir.name != ".gradle" }
            .filter { it.isFile && it.name == "strings_locked.xml" && it.parentFile.name == "values" }
            .forEach { baseFile ->
                val classBKeys = classification(baseFile).filterValues { it }.keys
                if (classBKeys.isEmpty()) return@forEach // module's locked vocabulary is all Class A

                val resDir = baseFile.parentFile.parentFile // .../src/main/res
                locales.forEach { locale ->
                    val localeDir = resDir.resolve("values-$locale")
                    if (!localeDir.isDirectory) return@forEach

                    val stringsFiles = localeDir.listFiles { f ->
                        f.isFile && f.name.startsWith("strings") && f.extension == "xml"
                    }.orEmpty()

                    val keyToFile = LinkedHashMap<String, java.io.File>()
                    stringsFiles.forEach { f ->
                        classification(f).keys.forEach { key -> keyToFile.putIfAbsent(key, f) }
                    }

                    // One verdict per OWNING FILE, not per key: the signature covers the file's whole
                    // Class B set at once, so checking it once per key would report the same file N times.
                    classBKeys.mapNotNull { keyToFile[it] } // absent key: not our job, see above
                        .distinct()
                        .forEach { owningFile ->
                            val expected = digestOf(owningFile, classBKeys)
                            val signed = signature.find(owningFile.readText())?.groupValues?.get(1)
                            val path = owningFile.relativeTo(repoRoot).path
                            when (signed) {
                                null -> offenders += "$path - no OWNER-REVIEWED signature; " +
                                    "once approved, sign it with: sha256:$expected"
                                expected -> Unit
                                else -> offenders += "$path - signed text no longer matches what " +
                                    "ships (signature sha256:$signed, current text sha256:$expected); " +
                                    "the owner must re-read it and re-sign with sha256:$expected"
                            }
                        }
                }
            }

        if (offenders.isNotEmpty()) {
            error(
                "Release build blocked - the following files hold translated locked-vocabulary keys " +
                    "whose current text is not covered by an owner signature (spec §7.2 owner-review " +
                    "gate):\n" +
                    offenders.joinToString("\n") { "  - $it" } +
                    "\n\nTo clear a file: once the owner has personally read and approved its " +
                    "Russian/Turkish text, put the signature named above into that file's header XML " +
                    "comment, e.g. \"<!-- OWNER-REVIEWED 2026-08-19 sha256:0123456789abcdef -->\". " +
                    "The digest covers exactly that file's Class B keys, so editing any signed " +
                    "sentence invalidates the signature and brings this gate back. Clearing this gate " +
                    "IS the act of signing off - see " +
                    "docs/superpowers/specs/2026-08-11-i18n-1-owner-review.md.",
            )
        }
    }
}

// F1 (fix round): `bundleRelease` - the AAB, the actual Play/"Generate Signed Bundle" shipping path -
// was not wired at all; only `assembleRelease` was. A release build could bypass the gate entirely via
// the exact route the unreviewed Shahada would really ship through. Both are covered now.
tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(checkOwnerReviewedLocaleStrings)
}

// Этап 3.2 (agentic track). `DoctrineMatrixGuardTest` reads
// `docs/governing/sidr-doctrine-matrix-v1.0.md` - a file outside every source set, so Gradle has no
// way to know it is an input. Without this declaration the test task stays UP-TO-DATE when only the
// matrix changes, and a rule claiming a nonexistent test would ship unchecked on an incremental
// build. Verified by mutation: without it, four deliberately broken matrices all "passed"; with it,
// each one fails. Exactly the vacuous-guard class Этап 2 found in three privacy guards
// (`walkTopDown()` on a nonexistent directory throws nothing and asserts nothing).
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("docs/governing/sidr-doctrine-matrix-v1.0.md"))
        .withPropertyName("doctrineMatrix")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
