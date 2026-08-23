plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

// :domain is the ONLY project dependency, and that is the point of this module (spec §4). No core/*,
// no data/*, no feature/*, nothing Android. `ModuleIsolationTest` holds it.
dependencies {
    implementation(project(":domain"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
