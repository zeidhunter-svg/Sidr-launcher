plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sidr.launcher.core.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // A4′ acceptance tail, task 8 (K13): `AndroidNextAlarmSourceTest` runs on Robolectric.
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
    // PermissionChecker port lives in :domain (a feature can't depend on core/android);
    // core/android provides its Android impl. :domain is pure, so no cycle is introduced.
    implementation(project(":domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.core)

    // Block Q: JVM unit tests for the pure device-profile classifier + cache mapping
    // (the Android-API reads in AndroidDeviceProfiler stay device/instrumented-pending).
    testImplementation(libs.junit4)
    // A4′ acceptance tail, task 8 (K13, plan review I9): the first Robolectric test here —
    // `AndroidNextAlarmSourceTest` drives the real AlarmManager / PackageManager / broadcast APIs through
    // Robolectric's shadows, and collects the source's flow with coroutines-test.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.coroutines.test)
}
