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

    // Этап 4 / A0 Task 13. `ToolExecutorCallSiteGuardTest` and `AgentVocabularyGuardTest` scan .kt
    // sources outside :app's source set - the same silent-skip trap as the matrix above.
    //
    // A1' Task 3 - `ToolWorkerCallSiteGuardTest` scans the identical five roots (the derived
    // `:domain` set plus these four declared dirs) as the second hop's guard, so it is a second
    // consumer of every declaration below - not a reason to add a sixth. Verified the same way as the
    // consumerJvmSources note further down: no new `inputs.dir` needed, because nothing about which
    // directories are scanned changed, only which files inside them a second test also reads.
    //
    // Only ONE of the four declarations below is load-bearing, and what it is load-bearing FOR is
    // narrower than it looks. Both facts were measured, not assumed (Task 13 Step 7, row 7):
    //  - `domainSources` IS. The repo-wide `i18nGuardRepoWideSrcMainScan` tree further down includes
    //    `**/src/main/**/*.kt`, and :domain went KMP in Этап 2.2 - its production segment is
    //    `commonMain`, not `main` - so nothing in :domain matches that pattern.
    //  - But :domain is on :app's test RUNTIME CLASSPATH, and that is already a declared input. So a
    //    :domain edit that changes the compiled output re-triggers this task anyway, declaration or
    //    no declaration: with these lines removed, adding `import ...action.LauncherAction` to
    //    domain/src/commonMain/.../AgentExecutor.kt still re-ran the task and still went RED, because
    //    the added line shifts every line below it and :domain's compiled output changed with it.
    //  - The gap is edits where the scanned TEXT and the BYTECODE disagree - and an import is exactly
    //    that, since an import contributes no bytecode of its own. Re-running the same mutation with
    //    the added import balanced by a deleted blank line, so no executable line moved, left
    //    :domain's output byte-identical: with these lines removed `:app:testDebugUnitTest` came back
    //    FROM-CACHE at exit 0, serving the stale 4-tests-0-failures result while `domain/agent` sat
    //    there importing `LauncherAction`. Restoring these lines, same mutation, same command: RED.
    //    A guard that only catches the careless half of a change is not a guard.
    //  - `dataRepositorySources` and `launcherSources` are NOT load-bearing: both already match
    //    `**/src/main/**/*.kt`. They are declared anyway because that tree was written for the i18n
    //    guards, not for these two, and a future narrowing of it (it has already been re-widened once
    //    - see the `res/**` note below) would silently take these guards' inputs with it. Defence in
    //    depth costs a rare needless re-run; the other direction costs a guard that does not run.
    inputs.dir(rootProject.file("domain/src/commonMain/kotlin"))
        .withPropertyName("domainSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("data/repository/src/main/java"))
        .withPropertyName("dataRepositorySources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("feature/launcher/src/main/java"))
        .withPropertyName("launcherSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // A0.5 — ToolExecutorCallSiteGuardTest now scans the second consumer too. NOT load-bearing today,
    // and that was measured rather than assumed: `consumer/jvm/src/main/kotlin/**/*.kt` already matches
    // the repo-wide `i18nGuardRepoWideSrcMainScan` tree further down, so removing this block alone and
    // re-running with a second call site planted in `consumer/jvm` still came back RED at exit 1 — no
    // stale green. It is declared for the same reason as `dataRepositorySources` and `launcherSources`
    // above: that tree was written for the i18n guards, not for this one, and a future narrowing of it
    // would silently take this guard's input with it. That it IS load-bearing in the world it exists
    // for was proved by narrowing the i18n tree to `app/` first: without this block the task then came
    // back UP-TO-DATE at exit 0 with the mutation present, and with the block restored the same
    // mutation went RED. One named directory, not the repo-wide widening that findings D1/D3/D8 park
    // as an owner-level build trade-off.
    inputs.dir(rootProject.file("consumer/jvm/src/main/kotlin"))
        .withPropertyName("consumerJvmSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

// Fix-privacy-guard, review item IMPORTANT 7 (2026-08-20). Four I18N guards in app/src/test read
// repo paths by java.io.File(...) - LocaleCompletenessGuardTest, HardcodedUiTextGuardTest,
// DomainIdentifierLeakGuardTest, StringSeamGuardTest - and only the doctrine-matrix file above was
// ever declared as a Test input. Demonstrated end to end: deleting a translated key from
// app/src/main/res/values-tr/strings.xml left `:app:testDebugUnitTest` UP-TO-DATE (exit 0) because
// removing a values-tr entry never changes R, so nothing else invalidates the task - the very rule
// this project calls sacred (en/ru/tr ship complete in the same commit) was being enforced by a
// guard that silently did not run.
//
// What's declared, and what deliberately is NOT, per guard:
//  - LocaleCompletenessGuardTest reads every module's `res/values*/strings*.xml` (base "values" plus
//    every `values-<locale>` folder, including the long-tail warn-only scan) via `resFiles()`, for
//    both the 7 modules hand-listed in `modulePrefixes` AND (in
//    `module_prefixes_cover_every_module_that_ships_strings`) every module `settings.gradle.kts`
//    includes - so declared as one repo-wide `res/**` tree rather than 7 hand-picked module
//    dirs, exactly so a future module never falls into the same silent-skip gap that test itself
//    guards against. Resource XML is never part of any module's compiled Kotlin output, so unlike
//    .kt sources (see below) nothing else was ever going to catch a change here.
//    The tree is `res/**`, NOT the narrower `res/values*/**` it was until 2026-08-20: that pattern
//    did not match `res/xml/locales_config.xml`, which a comment right below wrongly claimed it
//    "already covered" - so the fourth locale source was unpinned and a `<locale android:name="de"/>`
//    planted there left `:app:testDebugUnitTest` UP-TO-DATE and exit 0 (re-review finding N1). One
//    include covering everything the guards actually read beats hand-maintained paths that drift, and
//    this narrow form had already drifted once. It costs an over-declaration of a handful of non-string
//    res files (`colors.xml`, `styles.xml`, drawables) - a rare needless re-run, which is the cheap
//    direction of this trade; the expensive direction is a guard that silently does not run.
//  - `locale_lists_agree_across_the_four_sources` additionally reads
//    `app/src/main/res/xml/locales_config.xml` (matched by the `res/**` tree above),
//    `feature/settings/.../SettingsScreen.kt` (a .kt source in a project(":feature:settings")
//    dependency of :app - NOT declared, see below), `app/build.gradle.kts` itself, and
//    `settings.gradle.kts` - the latter two are build scripts, never part of any compiled classpath,
//    so declared explicitly.
//  - HardcodedUiTextGuardTest / DomainIdentifierLeakGuardTest walk `scopedRoots` (core/ui,
//    feature/launcher, feature/settings, feature/prayer, feature/permission_education,
//    feature/assistant, feature/suggestions, app) for `.kt` files under `src/main`; StringSeamGuardTest
//    walks the ENTIRE repo tree the same way, unrestricted. Every one of those `scopedRoots` modules
//    is already an `implementation(project(...))` (or, for `app` itself, the same module hosting the
//    test) - verified empirically, not assumed: planting a real hardcoded-text violation in
//    core/ui/.../SectionHeader.kt and running `:app:testDebugUnitTest` with NO extra declaration
//    already re-executed the task and failed the guard, because editing a dependency module's .kt
//    source changes that module's compiled jar, which IS already part of the test runtime classpath.
//    So no separate declaration was added for `scopedRoots` alone. StringSeamGuardTest's unrestricted
//    walk is different: `:baselineprofile` (settings.gradle.kts line 34) ships
//    `src/main/java/.../BaselineProfileGenerator.kt` but is wired only via the special
//    `baselineProfile(project(...))` configuration, never `implementation`/`testImplementation` - it
//    is NOT part of :app's test classpath by any route, so it (and any future module in the same
//    position) would be a genuine, silent gap. Declared as one repo-wide `src/main/**/*.kt` tree
//    (same shape as :domain's `prayerGuardRepoWideSrcMainScan`) rather than special-casing
//    `:baselineprofile` by name, so a similarly-wired future module is covered automatically; this
//    also acts as a belt-and-suspenders backstop for the two `scopedRoots` guards above.
//    `feature/settings/.../SettingsScreen.kt` (used by `locale_lists_agree_across_the_four_sources`)
//    falls under this same tree and needs no separate declaration either.
tasks.withType<Test>().configureEach {
    inputs.files(
        fileTree(rootProject.projectDir) {
            include("**/src/main/res/**")
            exclude("**/build/**")
        },
    )
        .withPropertyName("i18nGuardRepoWideResScan")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs.files(
        fileTree(rootProject.projectDir) {
            include("**/src/main/**/*.kt")
            exclude("**/build/**")
        },
    )
        .withPropertyName("i18nGuardRepoWideSrcMainScan")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs.file(rootProject.file("settings.gradle.kts"))
        .withPropertyName("i18nGuardSettingsGradleKts")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    inputs.file(rootProject.file("app/build.gradle.kts"))
        .withPropertyName("i18nGuardAppBuildGradleKts")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
