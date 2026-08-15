import java.io.File

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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sidr.launcher"
        minSdk = 28
        targetSdk = 35
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
    implementation(project(":data:ai-local"))
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

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.navigation.compose)

    // Block Q: WorkManager (one-shot model download) + Hilt @HiltWorker support. The worker shell,
    // HiltWorkerFactory and Configuration.Provider live here (composition root, already kapt+Hilt);
    // the correctness-critical provisioning logic stays in :data:ai-local (ModelProvisioner).
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
val checkOwnerReviewedLocaleStrings by tasks.registering {
    group = "verification"
    description = "I18N-1 release gate (spec §7.2): fails until every translated strings_locked.xml " +
        "carries OWNER-REVIEWED in its header, i.e. until the owner has signed off on it."

    val repoRoot = rootProject.projectDir
    inputs.property("repoRootPath", repoRoot.path)

    doLast {
        val offenders = repoRoot.walkTopDown()
            .onEnter { dir -> dir.name != "build" && dir.name != ".git" && dir.name != ".gradle" }
            .filter { it.isFile && it.name == "strings_locked.xml" }
            .filter { it.parentFile.name.startsWith("values-") }
            .filter { !it.readText().contains("OWNER-REVIEWED") }
            .map { it.relativeTo(repoRoot).path }
            .sorted()
            .toList()

        if (offenders.isNotEmpty()) {
            error(
                "Release build blocked - the following translated strings_locked.xml files have not " +
                    "been signed off by the owner (spec §7.2 owner-review gate):\n" +
                    offenders.joinToString("\n") { "  - $it" } +
                    "\n\nTo clear a file: once the owner has personally read and approved its " +
                    "Russian/Turkish text, add the token OWNER-REVIEWED to that file's header XML " +
                    "comment (e.g. \"<!-- OWNER-REVIEWED 2026-08-16 -->\"). Clearing this gate IS the " +
                    "act of signing off - see docs/superpowers/specs/2026-08-11-i18n-1-owner-review.md.",
            )
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(checkOwnerReviewedLocaleStrings)
}
