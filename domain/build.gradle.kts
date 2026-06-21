plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // domain is pure Kotlin: stdlib + coroutines only. No core/*, no Android. (Block A / A2)
    implementation(libs.coroutines.core)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
