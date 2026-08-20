plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

// Этап 2.2 — :domain on kotlin.multiplatform (android + jvm targets), portable-core boundary
// decided in ADR 3/4. All production code stays in commonMain (target-agnostic Kotlin, compiled
// for both targets — the KMP-ready guarantee this module already held informally). Tests stay in
// jvmTest only: JUnit4 (this module's existing test framework) isn't a commonTest-compatible
// multiplatform artifact, and migrating to kotlin.test is a separate decision, not a mechanical
// toolchain move.
kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm()

    sourceSets {
        commonMain.dependencies {
            // domain is pure Kotlin: stdlib + coroutines only. No core/*, no Android. (Block A / A2)
            implementation(libs.coroutines.core)
        }

        jvmTest.dependencies {
            implementation(project(":core:testing"))
            implementation(libs.junit4)
            implementation(libs.coroutines.test)
        }
    }
}

android {
    namespace = "com.sidr.launcher.domain"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }
}

// A0 fix-privacy-guard (2026-08-20). Three privacy-scope guards in domain/src/jvmTest read
// production source files BY PATH, outside :domain's own compiled dependency graph — Gradle has no
// way to know those files are inputs, so :domain:jvmTest can stay UP-TO-DATE while the guard it
// "ran" silently didn't re-check anything. This is the exact trap that let
// ResolutionPrivacyScopeGuardTest's "recording call is gated by an app-ambiguity learning token"
// stay green-on-paper for three commits after 23000ae moved its target code out of
// LauncherViewModel.kt — see the KDoc on that test. Same fix pattern as the doctrine-matrix guard
// in app/build.gradle.kts.
//
// domain/src/commonMain/.../memory/{resolution,alias} and domain/src/commonMain/.../prayer are NOT
// declared here: they are :domain's own production sources, already inputs of
// compileKotlinJvm/compileTestKotlinJvm via the normal Kotlin source-set graph, so a change there
// already invalidates jvmTest's up-to-date check without any extra declaration.
tasks.withType<Test>().configureEach {
    // ResolutionPrivacyScopeGuardTest — single file, read by path.
    inputs.file(
        rootProject.file(
            "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherAppLaunch.kt",
        ),
    )
        .withPropertyName("resolutionRecordCallGuardTarget")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // PrayerLocationPrivacyGuardTest guards 4b/5/6 — three module trees read by path, none of them
    // a dependency of :domain (domain has zero deps on feature/data/core per the hard rules, so
    // there is no other path by which Gradle would ever see these as inputs).
    inputs.dir(rootProject.file("data/prayer/src/main"))
        .withPropertyName("prayerGuardDataPrayerSrcMain")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("feature/prayer/src/main"))
        .withPropertyName("prayerGuardFeaturePrayerSrcMain")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("core/android/src/main/java/com/sidr/launcher/core/android/prayer"))
        .withPropertyName("prayerGuardCoreAndroidPrayerSrcMain")
        .withPathSensitivity(PathSensitivity.RELATIVE)

    // PrayerLocationPrivacyGuardTest guards 4a/4c (`allSrcMainKotlinFiles`) additionally scan EVERY
    // module's src/main tree, repo-wide, for adhan2/com.batoulapps/android.location import lines —
    // the same staleness risk, just wider. Declared as one filtered file tree (every .kt file under
    // any src/main directory) rather than enumerating every module by hand, since that is exactly
    // what the guard itself scans.
    inputs.files(
        fileTree(rootProject.projectDir) {
            include("**/src/main/**/*.kt")
            exclude("**/build/**")
        },
    )
        .withPropertyName("prayerGuardRepoWideSrcMainScan")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
