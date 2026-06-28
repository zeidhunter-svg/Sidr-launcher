plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sidr.launcher.data.ailocal"
    compileSdk = 35

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
            // The gate-degrade JVM tests exercise OnnxIntentClassifier WITHOUT a real session
            // (gate-off / missing-file paths). Those paths log non-PII reasons via android.util.Log,
            // which throws "not mocked" under plain JVM unit tests. Return defaults so the no-session
            // degrade logic is JVM-testable; real logging still works on device (P5 androidTest).
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":domain"))
    // Block P: Context (model file load via Block Q's ModelStore) + android.os.Build (NNAPI API gate).
    // Allowed by architecture.md:117 (data/* -> core/android).
    implementation(project(":core:android"))

    implementation(libs.coroutines.core)
    implementation(libs.onnxruntime.android)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
    testImplementation(project(":core:testing"))

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
