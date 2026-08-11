plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.sidr.launcher.core.ui"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // I18N-1 Task 4 (spec §10.4): generates the `en-XA` / `ar-XB` pseudolocales into the debug
    // resource table. Load-bearing — proven by spike: with the flag off, a Robolectric
    // `b+en+XA` qualifier resolves `ui_action_cancel` to plain "Cancel"; with it on, to
    // "[Çåñçéļ one two]". The pseudolocale screenshot barrier depends on it.
    buildTypes {
        debug {
            isPseudoLocalesEnabled = true
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {

    implementation(project(":core:common"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)

    // Renders @Preview composables in the IDE / preview host (debug-only).
    debugImplementation(libs.compose.ui.tooling)

    // DS-1 — Roborazzi JVM/Robolectric screenshot harness.
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
