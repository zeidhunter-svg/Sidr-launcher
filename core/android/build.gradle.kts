plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sidr.launcher.core.android"
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
    implementation(project(":core:common"))
    // PermissionChecker port lives in :domain (a feature can't depend on core/android);
    // core/android provides its Android impl. :domain is pure, so no cycle is introduced.
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.core)
}
