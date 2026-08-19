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
