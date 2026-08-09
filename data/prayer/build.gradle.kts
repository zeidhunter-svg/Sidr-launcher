plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sidr.launcher.data.prayer"
    compileSdk = 35

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))
    // DS-6B Task 6 — @IoDispatcher qualifier only (no Android/core edge beyond this).
    implementation(project(":core:common"))

    implementation(libs.coroutines.core)

    // DS-6B Task 4 — adhan2 (Kotlin port of "adhan", MIT licensed). kotlinx-datetime is adhan2's own
    // public-API dependency (PrayerTimes exposes kotlinx.datetime.Instant); declared explicitly here
    // (pinned to the exact version adhan2:0.0.5 resolves) rather than relying on implicit transitive
    // resolution. Both must stay confined to this module — see the grep-confinement gate in the report.
    implementation(libs.adhan2)
    implementation(libs.kotlinx.datetime)

    // DS-6B Task 6 — prefs + schedule cache impls over the shared DataStore<Preferences> file.
    implementation(libs.datastore.preferences)
    implementation(libs.serialization.json)
    // @Inject constructor marker only (no DI framework here — :app wires the Hilt binding later).
    implementation(libs.javax.inject)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(project(":core:testing"))
}
