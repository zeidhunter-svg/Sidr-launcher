plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    // Room runs on KSP (Fork 8 — hybrid); Hilt stays on kapt below.
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sidr.launcher.data.repository"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            // Required for Robolectric to resolve Android resources in JVM unit tests.
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        // Expose the exported Room schemas as androidTest assets so MigrationTestHelper can
        // validate the golden schema (Fork 2 / Block F, step F8.5).
        getByName("androidTest") {
            assets.srcDirs(files("$projectDir/schemas"))
        }
    }
}

kotlin {
    jvmToolchain(17)
}

kapt {
    correctErrorTypes = true
}

ksp {
    // Fork 2 — schemas are committed to VCS at data/repository/schemas/.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core:common"))
    implementation(project(":core:android"))

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(project(":core:testing"))
    // Test-only. `AgentSessionMappersTest` enumerates TraceEvent's sealed subclasses so that adding a
    // variant without teaching the mapper to read it fails a test instead of shipping.
    testImplementation(libs.kotlin.reflect)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
