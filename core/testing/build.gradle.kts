plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.coroutines.core)
    implementation(libs.junit4)
    implementation(libs.coroutines.test)
}
