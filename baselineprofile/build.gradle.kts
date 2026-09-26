plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.sidr.launcher.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        // Этап 2.1 — see app/build.gradle.kts: held one level behind compileSdk pending Robolectric
        // API 37 support.
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    targetProjectPath = ":app"

    androidResources {
        ignoreAssetsPattern =
            "trace_processor_shell_arm:trace_processor_shell_x86:trace_processor_shell_x86_64:" +
                "tracebox_arm:tracebox_x86:tracebox_x86_64"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
